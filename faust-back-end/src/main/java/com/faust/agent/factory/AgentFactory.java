package com.faust.agent.factory;

import com.alibaba.cloud.ai.graph.agent.Builder;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.extension.interceptor.PatchToolCallsInterceptor;
import com.alibaba.cloud.ai.graph.agent.hook.Hook;
import com.alibaba.cloud.ai.graph.agent.hook.skills.SkillsAgentHook;
import com.alibaba.cloud.ai.graph.agent.hook.summarization.SummarizationHook;
import com.alibaba.cloud.ai.graph.agent.interceptor.Interceptor;
import com.alibaba.cloud.ai.graph.skills.registry.SkillRegistry;
import com.alibaba.cloud.ai.sandbox.ToolkitInit;
import com.faust.agent.cache.AgentCacheGeneration;
import com.faust.agent.checkpoint.saver.NewRedisSaver;
import com.faust.agent.hooks.MessageSaveAndHandleHook;
import com.faust.agent.skills.registry.DefaultSkillRegistry;
import com.faust.common.function.ThrowExceptionFunction;
import com.faust.common.security.PasswordEncryptor;
import com.faust.dto.agent.*;
import com.faust.entity.*;
import com.faust.enums.ErrorMessageEnum;
import com.faust.enums.ToolTypeEnum;
import com.faust.exception.AgentBusinessException;
import com.faust.repository.*;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import io.agentscope.runtime.sandbox.box.BaseSandbox;
import io.agentscope.runtime.sandbox.manager.SandboxService;
import io.micrometer.observation.ObservationRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.utils.Lists;
import org.jetbrains.annotations.NotNull;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.observation.ChatModelObservationConvention;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.ai.model.tool.DefaultToolExecutionEligibilityPredicate;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionEligibilityPredicate;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class AgentFactory implements ApplicationContextAware, DisposableBean {

	private final AgentSkillItemRepository agentSkillItemService;
	private final AgentModelConfigRepository agentModelConfigService;
	private final DataSourceConfigRepository dataSourceConfigDaoService;
	private ApplicationContext applicationContext;
	private final AgentInfoRepository agentInfoService;
	private final AgentSystemPromptRepository agentSystemPromptService;
	private final AgentToolRelRepository agentToolRelService;
	private final AgentToolRepository agentToolService;
	private final AgentSkillRepository agentSkillService;
	private final AgentSkillRelRepository agentSkillRelService;
	private final ToolCallingManager toolCallingManager;
	private final RetryTemplate retryTemplate;
	private final SandboxService sandboxService;
	private final ObjectProvider<ObservationRegistry> observationRegistry;
	private final ObjectProvider<ChatModelObservationConvention> observationConvention;
	private final ObjectProvider<ToolExecutionEligibilityPredicate> openAiToolExecutionEligibilityPredicate;
	private final ObjectProvider<RestClient.Builder> restClientBuilderProvider;
	private final ObjectProvider<WebClient.Builder> webClientBuilderProvider;
	private final ToolCallbackFactory toolCallbackFactory;
	private final NewRedisSaver redisSaver;
	private final PasswordEncryptor passwordEncryptor;
	private final AgentCacheGeneration cacheGeneration;
	private ThreadPoolExecutor agentParallelExecutor;
	private static final int THREAD_COUNT = Runtime.getRuntime().availableProcessors() << 1;

	/**
	 * 缓存 key 分隔符：{@code agentCode@version@generation[@subAgent]}
	 */
	private static final String KEY_SEPARATOR = "@";

	/**
	 * 子 Agent 缓存 key 后缀
	 */
	private static final String SUB_AGENT_SUFFIX = "@subAgent";

	/**
	 * 缓存有效期：代际号已保证正确性，这里只负责释放旧对象，不需要太长
	 */
	private static final long CACHE_EXPIRE_MINUTES = 30L;

	/**
	 * 单实例缓存条目上限，防止历史代际无限堆积
	 */
	private static final long CACHE_MAX_SIZE = 500L;

	/**
	 * 子 Agent 依赖反向索引：子 Agent code → 持有它的父 Agent 缓存 key。
	 *
	 * <p>父 Agent 的工具回调里内嵌了子 Agent 的 {@code ReactAgent} 实例，
	 * 子 Agent 变更时只清子 Key 是不够的，必须把父级一并失效。</p>
	 */
	private final Map<String, Set<String>> subAgentDependencies = new ConcurrentHashMap<>();

	private final ResponseErrorHandler responseErrorHandler;

	private static final String COMPLETIONS_PATH = "/chat/completions";


	private static final String MD_ATTENTION = """
			## 注意
			
			### 1.输出的时候必须校验md的格式，一定不能出现错误的md格式
			""";


	@PostConstruct
	public void init() {
		this.agentParallelExecutor = new ThreadPoolExecutor(THREAD_COUNT,
				THREAD_COUNT, 0L, TimeUnit.MILLISECONDS, new
				LinkedBlockingQueue<>(5000), r -> {
			Thread thread = new Thread(r);
			thread.setDaemon(true);
			thread.setName("agent-parallel-executor-" + thread.getId());
			return thread;
		}, new ThreadPoolExecutor.CallerRunsPolicy());
	}

	private final LoadingCache<String, ReactAgent> cache = CacheBuilder.newBuilder()
			.expireAfterAccess(Duration.ofMinutes(CACHE_EXPIRE_MINUTES))
			.maximumSize(CACHE_MAX_SIZE)
			.build(new CacheLoader<>() {
				@NotNull
				@Override
				public ReactAgent load(@NotNull String key) {
					String[] split = key.split(KEY_SEPARATOR);
					String agentCode = split[0];
					String version = split[1];
					boolean subAgent = split.length > 3;
					AgentInfo agentInfo = agentInfoService.getPublishedAgentByCodeAndVersion(agentCode, version);
					ThrowExceptionFunction.isNull(agentInfo)
							.throwMessage(ErrorMessageEnum.AGENT_NOT_FOUND);
					if (subAgent) {
						agentInfo.setIsSubAgent(true);
					}
					// 构建前先记下依赖：父 Agent 会把子 Agent 实例内嵌进工具回调，
					// 子 Agent 失效时必须连带父 Agent 一起失效
					Set<String> subAgentCodes = subAgentCodesOf(agentInfo.getId());
					ReactAgent agent = buildAgent(agentInfo);
					rememberDependencies(key, subAgentCodes);
					return agent;
				}
			});


	public ReactAgent getAgent(String agentCode, String version) {
		return load(cacheKey(agentCode, version, false));
	}

	public ReactAgent getSubAgent(String agentCode, String version) {
		return load(cacheKey(agentCode, version, true));
	}

	/**
	 * 缓存 key 带代际号：跨实例的失效靠代际号保证，任何实例重建后 key 自然变化
	 */
	private String cacheKey(String agentCode, String version, boolean subAgent) {
		String key = agentCode + KEY_SEPARATOR + version + KEY_SEPARATOR + cacheGeneration.current(agentCode);
		return subAgent ? key + SUB_AGENT_SUFFIX : key;
	}

	private ReactAgent load(String cacheKey) {
		try {
			return cache.get(cacheKey);
		} catch (Exception e) {
			throw new AgentBusinessException(ErrorMessageEnum.INTERNAL_ERROR.getErrorCode(), e.getMessage());
		}
	}

	/**
	 * 失效某个 Agent 的全部运行时缓存（所有版本、所有代际），并级联失效依赖它的父 Agent。
	 *
	 * <p>必须按 agentCode 做前缀清理而不是按 {@code code@version}：编辑会 bump 版本号，
	 * 只清当前版本会给旧版本留下永远不会再被访问的垃圾条目。</p>
	 *
	 * <p>本方法只清本机；跨实例失效见 {@code AgentCacheInvalidator#invalidate}，
	 * 其中代际号是权威、广播只是加速。</p>
	 */
	public void evictByAgent(String agentCode) {
		if (agentCode == null || agentCode.isBlank()) {
			return;
		}
		String prefix = agentCode + KEY_SEPARATOR;
		Set<String> targets = new HashSet<>();
		for (String key : cache.asMap().keySet()) {
			if (key.startsWith(prefix)) {
				targets.add(key);
			}
		}
		// 父 Agent 内嵌了子 Agent 实例，子失效必须连带父失效
		Set<String> dependents = subAgentDependencies.remove(agentCode);
		if (dependents != null) {
			targets.addAll(dependents);
		}
		if (targets.isEmpty()) {
			return;
		}
		cache.invalidateAll(targets);
		removeFromDependencies(targets);
		LOGGER.info("Agent runtime cache evicted, agentCode={}, evictedKeys={}", agentCode, targets.size());
	}

	/**
	 * 构建父 Agent 前收集它引用的子 Agent 编码（仅 SUB_AGENT 类型工具）
	 */
	private Set<String> subAgentCodesOf(Long agentId) {
		try {
			List<Long> toolIds = agentToolRelService.listToolIdsByAgentId(agentId);
			if (CollectionUtils.isEmpty(toolIds)) {
				return Set.of();
			}
			List<Long> subAgentIds = agentToolService.listByIds(toolIds).stream()
					.filter(tool -> ToolTypeEnum.SUB_AGENT.name().equals(tool.getType()))
					.map(AgentTool::getSubAgentId)
					.filter(Objects::nonNull)
					.toList();
			if (subAgentIds.isEmpty()) {
				return Set.of();
			}
			return agentInfoService.listByIds(subAgentIds).stream()
					.map(AgentInfo::getAgentCode)
					.filter(Objects::nonNull)
					.collect(Collectors.toSet());
		} catch (Exception e) {
			LOGGER.warn("Collect sub agent dependency failed, agentId={}", agentId, e);
			return Set.of();
		}
	}

	private void rememberDependencies(String parentKey, Set<String> subAgentCodes) {
		if (CollectionUtils.isEmpty(subAgentCodes)) {
			return;
		}
		subAgentCodes.forEach(code -> subAgentDependencies
				.computeIfAbsent(code, key -> ConcurrentHashMap.newKeySet())
				.add(parentKey));
	}

	/**
	 * 清掉索引里指向已失效 key 的残留，避免索引随代际增长
	 */
	private void removeFromDependencies(Set<String> evictedKeys) {
		subAgentDependencies.values().forEach(keys -> keys.removeAll(evictedKeys));
		subAgentDependencies.entrySet().removeIf(entry -> entry.getValue().isEmpty());
	}


	private AgentMateInfo getAgentMateInfo(AgentInfo agentInfo) {
		AgentMateInfo.AgentMateInfoBuilder builder = AgentMateInfo.builder()
				.agentCode(agentInfo.getAgentCode())
				.name(agentInfo.getName())
				.description(agentInfo.getDescription());
		// 获取系统提示词
		Long agentId = agentInfo.getId();
		AgentSystemPrompt systemPrompt = agentSystemPromptService.getSystemPrompt(agentId);
		ThrowExceptionFunction.isNull(systemPrompt)
				.throwMessage(ErrorMessageEnum.PROMPT_NOT_FOUND);
		builder.systemPrompt(systemPrompt.getContent());
		// 获取模型配置信息
		AgentModelConfig modeConfig = agentModelConfigService.getConfigByAgentId(agentId);
		ThrowExceptionFunction.isNull(modeConfig)
				.throwMessage(ErrorMessageEnum.MODEL_CONFIG_NOT_FOUND);
		AgentModelMateInfo agentModelMateInfo = AgentModelMateInfo.builder()
				.modelName(modeConfig.getModelName())
				.baseUrl(modeConfig.getBaseUrl())
				.apiKey(modeConfig.getApiKey())
				.timeout(modeConfig.getTimeout())
				.topP(modeConfig.getTopP())
				.temperature(modeConfig.getTemperature()).build();
		builder.agentModelMateInfo(agentModelMateInfo);
		// 获取skill元信息（通过 agent_skill_rel 关联，仅取已发布技能）
		List<Long> skillIds = agentSkillRelService.listSkillIdsByAgentId(agentId);
		if (!CollectionUtils.isEmpty(skillIds)) {
			List<AgentSkill> agentSkills = agentSkillService.listPublishedByIds(skillIds);
			ThrowExceptionFunction.isEmpty(agentSkills)
					.throwMessage(ErrorMessageEnum.SKILL_NOT_FOUND);
			List<AgentSkillItem> agentSkillItems = agentSkillItemService.listBySkillIdList(skillIds);
			ThrowExceptionFunction.isEmpty(agentSkillItems)
					.throwMessage(ErrorMessageEnum.SKILL_NOT_FOUND);
			Map<Long, List<AgentSkillItem>> skillItemMap = agentSkillItems
					.stream().collect(Collectors.groupingBy(AgentSkillItem::getSkillId));
			List<SkillMateInfo> skillMateInfoList = agentSkills.stream().map(e -> {
				SkillMateInfo skillMateInfo = SkillMateInfo.builder()
						.skillCode(e.getSkillCode())
						.name(e.getName())
						.description(e.getDescription()).build();
				Optional.ofNullable(skillItemMap.get(e.getId()))
						.ifPresent(items -> skillMateInfo.setSkillItemMateInfoList(items.stream()
								.map(item ->
										SkillItemMateInfo.builder()
												.itemType(item.getItemType())
												.fileName(item.getFileName())
												.sortOrder(item.getSortOrder())
												.content(item.getContent()).build()).toList()));
				return skillMateInfo;
			}).toList();
			builder.skillMateInfoList(skillMateInfoList);
		}
		// 获取工具元信息
		List<Long> toolIdList = agentToolRelService.listToolIdsByAgentId(agentId);
		if (!CollectionUtils.isEmpty(toolIdList)) {
			List<AgentTool> agentTools = agentToolService.publishedToolList(toolIdList);
			ThrowExceptionFunction.isEmpty(agentTools)
					.throwMessage(ErrorMessageEnum.TOOL_NOT_FOUND);
			List<Long> dbConfigIds = new ArrayList<>();
			List<Long> subAgentIds = new ArrayList<>();
			for (AgentTool agentTool : agentTools) {
				if (Objects.equals(ToolTypeEnum.DB.name(), agentTool.getType())) {
					dbConfigIds.add(agentTool.getDataSourceConfigId());
				} else if (Objects.equals(ToolTypeEnum.SUB_AGENT.name(), agentTool.getType())) {
					subAgentIds.add(agentTool.getSubAgentId());
				}
			}
			Map<Long, DataSourceInfo> dbConfigInfoMap = new HashMap<>(0);
			Map<Long, SubAgentInfo> subAgentInfoMap = new HashMap<>(0);
			if (!CollectionUtils.isEmpty(dbConfigIds)) {
				dbConfigInfoMap = dataSourceConfigDaoService.listByIds(dbConfigIds).stream()
						.collect(Collectors.toMap(DataSourceConfig::getId, e -> DataSourceInfo.builder()
								.url(e.getUrl())
								.pwd(passwordEncryptor.decrypt(e.getPwd()))
								.dbType(e.getDbType())
								.user(e.getUser()).build()));
			}
			if (!CollectionUtils.isEmpty(subAgentIds)) {
				subAgentInfoMap = agentInfoService.listByIds(subAgentIds).stream()
						.collect(Collectors.toMap(AgentInfo::getId, e -> SubAgentInfo.builder()
								.agentId(e.getId())
								.agentCode(e.getAgentCode())
								.version(e.getVersion())
								.build()));
			}
			List<ToolMateInfo> toolMateInfoList = Lists.newArrayList();
			for (AgentTool tool : agentTools) {
				ToolMateInfo.ToolMateInfoBuilder toolMateInfoBuilder = ToolMateInfo.builder()
						.name(tool.getName())
						.toolCode(tool.getToolCode())
						.type(tool.getType())
						.beanName(tool.getBeanName())
						.version(tool.getPublishVersion())
						.description(tool.getDescription());
				if (Objects.equals(ToolTypeEnum.DB.name(), tool.getType())) {
					toolMateInfoBuilder.dataSourceInfo(dbConfigInfoMap.get(tool.getDataSourceConfigId()));
				} else if (Objects.equals(ToolTypeEnum.SUB_AGENT.name(), tool.getType())) {
					toolMateInfoBuilder.subAgentInfo(subAgentInfoMap.get(tool.getSubAgentId()));
				} else if (Objects.equals(ToolTypeEnum.HTTP.name(), tool.getType())) {
					toolMateInfoBuilder.toolSchema(tool.getToolSchema());
				}
				toolMateInfoList.add(toolMateInfoBuilder.build());
			}
			builder.toolMateInfoList(toolMateInfoList);
		}
		return builder.build();
	}

	private OpenAiApi getOpenAiApi(AgentModelMateInfo agentModelMateInfo) {
		ResolvedConnectionProperties resolved = resolveConnectionProperties(agentModelMateInfo);
		return OpenAiApi.builder().baseUrl(resolved.baseUrl()).apiKey(new SimpleApiKey(resolved.apiKey())).headers(resolved.headers())
				.completionsPath(COMPLETIONS_PATH).embeddingsPath("/v1/embeddings")
				.restClientBuilder(restClientBuilderProvider.getIfAvailable(RestClient::builder))
				.webClientBuilder(webClientBuilderProvider.getIfAvailable(WebClient::builder))
				.responseErrorHandler(responseErrorHandler).build();
	}


	private ResolvedConnectionProperties resolveConnectionProperties(AgentModelMateInfo agentModelMateInfo) {
		String baseUrl = agentModelMateInfo.getBaseUrl();
		String apiKey = agentModelMateInfo.getApiKey();
		Map<String, List<String>> connectionHeaders = new HashMap<>(0);
		Assert.hasText(baseUrl, "OpenAI base URL must be set. ");
		Assert.hasText(apiKey, "OpenAI API key must be set. ");
		return new ResolvedConnectionProperties(baseUrl, apiKey, CollectionUtils.toMultiValueMap(connectionHeaders));
	}

	@Override
	public void destroy() {
		LOGGER.info("Shutting down agent parallel executor thread pool...");
		agentParallelExecutor.shutdown();
		try {
			if (!agentParallelExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
				agentParallelExecutor.shutdownNow();
			}
		} catch (InterruptedException e) {
			agentParallelExecutor.shutdownNow();
			Thread.currentThread().interrupt();
		}
	}

	private record ResolvedConnectionProperties(String baseUrl, String apiKey,
												MultiValueMap<String, String> headers) {
	}


	private OpenAiChatModel getOpenAiChatModel(AgentModelMateInfo agentModelMateInfo) {
		OpenAiChatOptions chatOptions = new OpenAiChatOptions();
		chatOptions.setModel(agentModelMateInfo.getModelName());
		chatOptions.setMaxTokens(agentModelMateInfo.getMaxTokens());
		chatOptions.setTopP(agentModelMateInfo.getTopP());
		chatOptions.setTemperature(agentModelMateInfo.getTemperature());
		OpenAiChatModel chatModel = OpenAiChatModel.builder().openAiApi(getOpenAiApi(agentModelMateInfo))
				.defaultOptions(chatOptions)
				.toolCallingManager(toolCallingManager)
				.toolExecutionEligibilityPredicate(openAiToolExecutionEligibilityPredicate
						.getIfUnique(DefaultToolExecutionEligibilityPredicate::new))
				.retryTemplate(retryTemplate)
				.observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP)).build();
		Objects.requireNonNull(chatModel);
		observationConvention.ifAvailable(chatModel::setObservationConvention);
		return chatModel;
	}


	private List<Hook> getHooks(AgentMateInfo agentMateInfo, ChatModel chatModel) {
		List<Hook> hooks = new ArrayList<>();
		// 用于生成摘要的模型，可以是主模型或更经济的模型
		SummarizationHook summarizationHook = SummarizationHook.builder()
				.model(chatModel).build();
		hooks.add(summarizationHook);
		MessageSaveAndHandleHook saveAndHandleHook = applicationContext.getBean(MessageSaveAndHandleHook.class);
		hooks.add(saveAndHandleHook);
		if (!CollectionUtils.isEmpty(agentMateInfo.getSkillMateInfoList())) {
			Hook skillsHook = getSkillsHook(agentMateInfo.getSkillMateInfoList());
			hooks.add(skillsHook);
		}
		return hooks;
	}


	private List<Interceptor> getInterceptors() {
		// 创建并注册PatchToolCallsInterceptor，解决历史中出现"悬垂"的工具调用
		PatchToolCallsInterceptor interceptor = PatchToolCallsInterceptor.builder().build();
		return List.of(interceptor);
	}

	public ReactAgent buildAgent(AgentInfo agentInfo) {
		AgentMateInfo agentMateInfo = getAgentMateInfo(agentInfo);
		// 创建 agent
		OpenAiChatModel openAiChatModel = getOpenAiChatModel(agentMateInfo.getAgentModelMateInfo());
		Builder builder = ReactAgent.builder()
				.name(agentInfo.getName())
				.model(openAiChatModel)
				.systemPrompt(getSystemPrompt(agentMateInfo))
				.hooks(getHooks(agentMateInfo, openAiChatModel))
				.interceptors(getInterceptors())
				.executor(agentParallelExecutor)
				.parallelToolExecution(true);
		if (!Boolean.TRUE.equals(agentInfo.getIsSubAgent())) {
			builder.saver(redisSaver);
		}
		if (!CollectionUtils.isEmpty(agentMateInfo.getToolMateInfoList())) {
			Map<Boolean, List<ToolMateInfo>> toolInfoMap = agentMateInfo.getToolMateInfoList()
					.stream().collect(Collectors.groupingBy(e -> ToolTypeEnum.NATIVE.name().equals(e.getType())));
			List<ToolMateInfo> nativeToolInfos = toolInfoMap.get(Boolean.TRUE);
			List<ToolMateInfo> toolInfos = toolInfoMap.get(Boolean.FALSE);
			if (!CollectionUtils.isEmpty(nativeToolInfos)) {
				builder.methodTools(getNativeTools(nativeToolInfos));
			}
			if (!CollectionUtils.isEmpty(toolInfos)) {
				builder.toolCallbackProviders(getToolCallbackProvider(toolInfos));
			}
		}
		return builder.build();
	}


	private String getSystemPrompt(AgentMateInfo agentMateInfo) {
		return agentMateInfo.getSystemPrompt() + "\n\n" +
				MD_ATTENTION;
	}


	private ToolCallbackProvider getToolCallbackProvider(List<ToolMateInfo> toolMateInfos) {
		if (CollectionUtils.isEmpty(toolMateInfos)) {
			return null;
		}
		return new ToolCallbackProvider() {

			@NotNull
			@Override
			public ToolCallback[] getToolCallbacks() {
				return toolMateInfos.stream()
						.map(toolCallbackFactory::getToolCallback).toArray(ToolCallback[]::new);
			}
		};
	}


	private Object[] getNativeTools(List<ToolMateInfo> nativeToolInfos) {
		List<Object> nativeTools = new ArrayList<>();
		nativeToolInfos.forEach(nativeToolInfo -> {
			try {
				Object nativeTool = applicationContext.getBean(nativeToolInfo.getBeanName());
				nativeTools.add(nativeTool);
			} catch (Exception e) {
				LOGGER.error("Native tool get error");
			}
		});
		return nativeTools.toArray(Object[]::new);
	}


	private Hook getSkillsHook(List<SkillMateInfo> skillMateInfos) {
		SkillRegistry registry = DefaultSkillRegistry.builder()
				.agentSkillsService(skillMateInfos)
				.build();
		return SkillsAgentHook.builder()
				.skillRegistry(registry)
				.build();

	}

	@Override
	public void setApplicationContext(@NotNull ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}
}
