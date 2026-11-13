package com.faust.service;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.checkpoint.Checkpoint;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.faust.agent.cache.AgentCacheInvalidator;
import com.faust.agent.checkpoint.saver.NewRedisSaver;
import com.faust.entity.*;
import com.faust.repository.*;
import com.faust.common.function.ThrowExceptionFunction;
import com.faust.dto.agent.req.AgentCreateReq;
import com.faust.dto.agent.req.AgentListQueryReq;
import com.faust.dto.agent.req.AgentUpdateReq;
import com.faust.dto.agent.resp.*;
import com.faust.dto.skill.resp.SkillSimpleResp;
import com.faust.enums.AgentStatusEnum;
import com.faust.enums.ErrorMessageEnum;
import com.faust.exception.AgentBusinessException;
import com.faust.common.context.UserContext;
import com.faust.support.SpaceGuard;
import com.faust.support.lock.DistributedLockTemplate;
import com.faust.support.lock.LockKeys;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AgentService {

	private final AgentInfoRepository agentInfoService;

	private final AgentSkillRepository agentSkillService;

	private final AgentSkillRelRepository agentSkillRelService;

	private final AgentToolRelRepository agentToolRelService;

	private final AgentModelConfigRepository agentModelConfigService;

	private final AgentSystemPromptRepository agentSystemPromptService;

	private final AgentToolRepository agentToolService;
	private final NewRedisSaver redisSaver;

	private final SpaceGuard spaceGuard;

	private final DistributedLockTemplate lockTemplate;

	private final AgentCacheInvalidator agentCacheInvalidator;

	private final AgentGlobalEnvConfigRepository agentGlobalEnvConfigRepository;

	/**
	 * 编辑类操作：技能条目可能批量插入，耗时不可控，等待时间给宽一点
	 */
	private static final long LOCK_WAIT_EDIT = 2000L;

	/**
	 * 发布 / 下架 / 删除：用户预期「点一下就有反馈」，宁可快速失败
	 */
	private static final long LOCK_WAIT_QUICK = 800L;

	/**
	 * 关联资源锁数量上限，超过后只锁 Agent 本身，避免一次拿几十把锁
	 */
	private static final int MAX_RELATION_LOCKS = 50;

	@Transactional(rollbackFor = Exception.class)
	public AgentBaseInfoResp createAgent(AgentCreateReq req) {
		Long spaceId = spaceGuard.currentSpaceId();
		// 空间锁：与「解散空间」互斥，避免资源写进已解散空间（W1）
		List<String> keys = relationLockKeys(LockKeys.space(spaceId), req.getToolIdList(), req.getSkillIdList());
		return lockTemplate.call(keys, LOCK_WAIT_EDIT, () -> doCreateAgent(req, spaceId));
	}

	private AgentBaseInfoResp doCreateAgent(AgentCreateReq req, Long spaceId) {
		Long ucId = UserContext.getUcId();
		// 锁内二次校验：排队期间空间可能刚被解散
		spaceGuard.assertSpaceExists(spaceId);
		assertBindingsInSpace(spaceId, req.getSkillIdList(), req.getToolIdList());
		AgentInfo agentInfo = new AgentInfo();
		BeanUtils.copyProperties(req, agentInfo);
		agentInfo.setClassification(req.getClassification());
		agentInfo.setWorkspaceId(spaceId);
		Long agentId = agentInfoService.saveAgent(agentInfo, ucId);

		if (req.getModelCreateConfigReq() != null) {
			AgentModelConfig config = new AgentModelConfig();
			BeanUtils.copyProperties(req.getModelCreateConfigReq(), config);
			config.setAgentId(agentId);
			config.setCreateBy(ucId);
			config.setUpdateBy(ucId);
			agentModelConfigService.saveOrUpdateConfig(config);
		}

		if (req.getPromptReq() != null) {
			agentSystemPromptService.updatePrompt(agentId, req.getPromptReq().getContent());
		}

		if (!CollectionUtils.isEmpty(req.getSkillIdList())) {
			req.getSkillIdList().forEach(skillId -> agentSkillRelService.bindSkillToAgent(agentId, skillId, ucId));
		}

		if (!CollectionUtils.isEmpty(req.getToolIdList())) {
			req.getToolIdList().forEach(toolId -> agentToolRelService.bindToolToAgent(agentId, toolId, ucId));
		}
		AgentInfo agentInfoResp = agentInfoService.getAgentByCode(agentInfo.getAgentCode());
		ThrowExceptionFunction.isNull(agentInfoResp)
			.throwMessage(ErrorMessageEnum.AGENT_CREATE_FAILED);
		return AgentBaseInfoResp.builder()
			.agentId(agentInfoResp.getId())
			.name(agentInfoResp.getName())
			.agentCode(agentInfoResp.getAgentCode())
			.version(agentInfoResp.getVersion())
			.build();

	}

	@Transactional(rollbackFor = Exception.class)
	public boolean updateAgent(AgentUpdateReq req) {
		// 同时持有被关联工具 / 技能的锁，才能和它们的下架、删除互斥（A8 / T1 / S1）
		List<String> keys = relationLockKeys(LockKeys.agent(req.getAgentId()), req.getToolIdList(), req.getSkillIdList());
		return lockTemplate.call(keys, LOCK_WAIT_EDIT, () -> doUpdateAgent(req));
	}

	private boolean doUpdateAgent(AgentUpdateReq req) {
		Long ucId = UserContext.getUcId();
		AgentInfo oldAgentInfo = agentInfoService.getById(req.getAgentId());
		ThrowExceptionFunction.isNull(oldAgentInfo)
			.throwMessage(ErrorMessageEnum.AGENT_NOT_FOUND);
		spaceGuard.assertMember(oldAgentInfo.getWorkspaceId());
		assertBindingsInSpace(oldAgentInfo.getWorkspaceId(), req.getSkillIdList(), req.getToolIdList());
		// 版本号不作强制升级要求：允许与当前版本相同（同版本继续改写），
		// 并发保护由 agent:{id} 分布式锁 + 版本 CAS 兜底，不依赖调用方 bump version。
		AgentInfo agentInfo = new AgentInfo();
		BeanUtils.copyProperties(req, agentInfo);
		agentInfo.setId(req.getAgentId());
		agentInfo.setClassification(req.getClassification());
		// 版本 CAS 兜底：expected 取锁内读到的行版本，仅在行被删除 / 版本被绕过锁改写时命中 0 行 → 1045。
		// 同一版本的并发编辑由上面的 agent:{id} 锁串行化，属"后提交覆盖"，不再报冲突。
		agentInfoService.updateAgent(agentInfo, ucId, oldAgentInfo.getVersion());
		if (req.getModelCreateConfig() != null) {
			AgentModelConfig config = new AgentModelConfig();
			BeanUtils.copyProperties(req.getModelCreateConfig(), config);
			config.setAgentId(req.getAgentId());
			config.setCreateBy(ucId);
			config.setUpdateBy(ucId);
			agentModelConfigService.saveOrUpdateConfig(config);
		}

		if (req.getPromptReq() != null) {
			agentSystemPromptService.updatePrompt(req.getAgentId(), req.getPromptReq().getContent());
		}

		// 技能关联全量替换（技能本体独立管理，只维护关联关系）
		agentSkillRelService.unbindAllSkill(req.getAgentId());
		if (!CollectionUtils.isEmpty(req.getSkillIdList())) {
			req.getSkillIdList()
				.forEach(skillId -> agentSkillRelService.bindSkillToAgent(req.getAgentId(), skillId, ucId));
		}
		if (!CollectionUtils.isEmpty(req.getToolIdList())) {
			List<Long> oldToolIds = agentToolRelService.listToolIdsByAgentId(req.getAgentId());
			oldToolIds.forEach(toolId -> agentToolRelService.unbindTool(req.getAgentId(), toolId));
			req.getToolIdList().forEach(toolId -> agentToolRelService.bindToolToAgent(req.getAgentId(), toolId, ucId));
		} else {
			agentToolRelService.unbindAllTool(req.getAgentId());
		}
		evictAgentCache(oldAgentInfo.getAgentCode());
		return true;
	}


	public boolean publish(Long agentId) {
		return lockTemplate.call(LockKeys.agent(agentId), LOCK_WAIT_QUICK, () -> doPublish(agentId));
	}

	private boolean doPublish(Long agentId) {
		Long ucId = UserContext.getUcId();
		AgentInfo agent = agentInfoService.getById(agentId);
		ThrowExceptionFunction.isNull(agent).throwMessage(ErrorMessageEnum.AGENT_NOT_FOUND);
		spaceGuard.assertMember(agent.getWorkspaceId());
		// 条件更新兜底：即使锁失效（Redis 主从切换），也不会出现「状态回退」
		if (!agentInfoService.updateStatusIfChanged(agentId, AgentStatusEnum.PUBLISHED.getValue(), ucId)) {
			throw new AgentBusinessException(ErrorMessageEnum.STATUS_CHANGED.getErrorCode(),
				ErrorMessageEnum.STATUS_CHANGED.getErrorMsg());
		}
		evictAgentCache(agent.getAgentCode());
		return true;
	}


	/**
	 * 下架
	 *
	 * @param agentId agentId
	 */
	public boolean takeOff(Long agentId) {
		return lockTemplate.call(LockKeys.agent(agentId), LOCK_WAIT_QUICK, () -> doTakeOff(agentId));
	}

	private boolean doTakeOff(Long agentId) {
		Long ucId = UserContext.getUcId();
		AgentInfo agent = agentInfoService.getById(agentId);
		ThrowExceptionFunction.isNull(agent)
			.throwMessage(ErrorMessageEnum.AGENT_NOT_FOUND);
		spaceGuard.assertMember(agent.getWorkspaceId());
		if (!agentInfoService.updateStatusIfChanged(agentId, AgentStatusEnum.UNPUBLISHED.getValue(), ucId)) {
			throw new AgentBusinessException(ErrorMessageEnum.RESOURCE_ALREADY_TAKEOFF.getErrorCode(),
				ErrorMessageEnum.RESOURCE_ALREADY_TAKEOFF.getErrorMsg());
		}
		evictAgentCache(agent.getAgentCode());
		return true;
	}

	@Transactional(rollbackFor = Exception.class)
	public boolean deleteAgent(Long agentId) {
		return lockTemplate.call(LockKeys.agent(agentId), LOCK_WAIT_QUICK, () -> doDeleteAgent(agentId));
	}

	private boolean doDeleteAgent(Long agentId) {
		AgentInfo agent = agentInfoService.getById(agentId);
		ThrowExceptionFunction.isNull(agent)
			.throwMessage(ErrorMessageEnum.AGENT_NOT_FOUND);
		spaceGuard.assertMember(agent.getWorkspaceId());
		ThrowExceptionFunction.isTrue(Objects.equals(agent.getStatus(), AgentStatusEnum.PUBLISHED.getValue()))
			.throwMessage(ErrorMessageEnum.AGENT_IS_PUBLISHED_TO_DELETED);
		// 删除语句自带 status <> 'PUBLISHED'，作为并发下的第二道闸
		if (!agentInfoService.deleteIfNotPublished(agentId)) {
			throw new AgentBusinessException(ErrorMessageEnum.AGENT_IS_PUBLISHED_TO_DELETED.getErrorCode(),
				ErrorMessageEnum.AGENT_IS_PUBLISHED_TO_DELETED.getErrorMsg());
		}
		// 级联逻辑删除模型配置
		AgentModelConfig modelConfig = agentModelConfigService.lambdaQuery()
			.eq(AgentModelConfig::getAgentId, agentId)
			.one();
		if (Objects.nonNull(modelConfig)) {
			agentModelConfigService.removeById(modelConfig.getId());
		}
		// 级联逻辑删除系统提示词
		AgentSystemPrompt agentSystemPrompt = agentSystemPromptService.lambdaQuery()
			.eq(AgentSystemPrompt::getAgentId, agentId)
			.one();
		if (Objects.nonNull(agentSystemPrompt)) {
			agentSystemPromptService.removeById(agentSystemPrompt.getId());
		}
		// 级联解除技能关联（技能本体独立管理，不随 Agent 删除）
		agentSkillRelService.unbindAllSkill(agentId);
		// 级联逻辑删除工具关联
		List<AgentToolRel> toolRelList = agentToolRelService.lambdaQuery()
			.eq(AgentToolRel::getAgentId, agentId)
			.list();
		if (!CollectionUtils.isEmpty(toolRelList)) {
			List<Long> toolRelIds = toolRelList.stream().map(AgentToolRel::getId).toList();
			agentToolRelService.removeBatchByIds(toolRelIds);
		}
		evictAgentCache(agent.getAgentCode());
		// 级联清理该 Agent 的全局环境变量（Agent + 用户维度），
		// 否则用同一 agentCode 重建时会带上已删除 Agent 的旧配置
		agentGlobalEnvConfigRepository.lambdaUpdate()
			.eq(AgentGlobalEnvConfig::getAgentCode, agent.getAgentCode())
			.remove();
		return true;
	}

	public Page<AgentPageInfoResp> pageList(AgentListQueryReq req) {
		long current = req.getCurrent() != null ? req.getCurrent() : 1L;
		long size = req.getSize() != null ? req.getSize() : 20L;
		Page<AgentInfo> page = new Page<>(current, size);
		Page<AgentInfo> agentPage = agentInfoService.listAgents(page, spaceGuard.currentSpaceId(), req.getKeyword(),
			req.getStatus(), req.getClassification(), req.getTags());

		Page<AgentPageInfoResp> result = new Page<>(agentPage.getCurrent(), agentPage.getSize(), agentPage.getTotal());
		result.setRecords(agentPage.getRecords().stream()
			.map(this::toAgentPageInfoResp)
			.toList());
		return result;
	}


	public AgentBaseInfoResp getBaseInfo(String agentCode) {
		AgentInfo agent = agentInfoService.getAgentByCode(agentCode);
		ThrowExceptionFunction.isNull(agent)
			.throwMessage(ErrorMessageEnum.AGENT_NOT_FOUND);
		spaceGuard.assertMember(agent.getWorkspaceId());
		return AgentBaseInfoResp.builder()
			.agentId(agent.getId())
			.name(agent.getName())
			.agentCode(agent.getAgentCode())
			.version(agent.getVersion()).build();
	}


	public AgentInfoDetailResp detail(String agentCode) {
		AgentInfo agent = agentInfoService.getAgentByCode(agentCode);
		ThrowExceptionFunction.isNull(agent)
			.throwMessage(ErrorMessageEnum.AGENT_NOT_FOUND);
		spaceGuard.assertMember(agent.getWorkspaceId());
		AgentInfoDetailResp resp = toAgentInfoDetailResp(agent);
		AgentModelConfig config = agentModelConfigService.getConfigByAgentId(agent.getId());
		if (config != null) {
			AgentModelConfigResp configResp = new AgentModelConfigResp();
			BeanUtils.copyProperties(config, configResp);
			resp.setModelConfig(configResp);
		}

		resp.setSystemPrompt(agentSystemPromptService.lambdaQuery()
			.eq(AgentSystemPrompt::getAgentId, agent.getId())
			.orderByDesc(AgentSystemPrompt::getCreateTime)
			.last("LIMIT 1")
			.oneOpt()
			.map(AgentSystemPrompt::getContent)
			.orElse(null));

		List<Long> skillIds = agentSkillRelService.listSkillIdsByAgentId(agent.getId());
		if (!CollectionUtils.isEmpty(skillIds)) {
			List<AgentSkill> skills = agentSkillService.listByIds(skillIds);
			resp.setSkillList(skills.stream().map(this::toSkillSimpleResp).toList());
		}

		List<Long> toolIds = agentToolRelService.listToolIdsByAgentId(agent.getId());
		if (!CollectionUtils.isEmpty(toolIds)) {
			List<AgentTool> tools = agentToolService.listByIds(toolIds);
			resp.setToolList(tools.stream().map(tool -> {
				AgentToolSimpleResp simpleResp = new AgentToolSimpleResp();
				simpleResp.setToolCode(tool.getToolCode());
				simpleResp.setToolId(tool.getId());
				simpleResp.setName(tool.getName());
				return simpleResp;
			}).toList());
		}

		return resp;
	}

	public Page<AgentCardInfoResp> cardPublishedPageList(AgentListQueryReq req) {
		long current = req.getCurrent() != null ? req.getCurrent() : 1L;
		long size = req.getSize() != null ? req.getSize() : 20L;
		Page<AgentInfo> page = new Page<>(current, size);
		// 空间隔离：只返回当前空间内已发布的 Agent
		Page<AgentInfo> agentPage = agentInfoService.listPublished(page, spaceGuard.currentSpaceId(),
			req.getKeyword(), Collections.singletonList(AgentStatusEnum.PUBLISHED.getValue()),
			req.getClassification(), req.getTags());
		Page<AgentCardInfoResp> result = new Page<>(agentPage.getCurrent(), agentPage.getSize(), agentPage.getTotal());
		result.setRecords(agentPage.getRecords().stream()
			.map(this::toAgentCardInfoResp)
			.toList());
		return result;
	}


	public Page<AgentBaseInfoResp> publishedSubAgentPageList(AgentListQueryReq req) {
		long current = req.getCurrent() != null ? req.getCurrent() : 1L;
		long size = req.getSize() != null ? req.getSize() : 20L;
		Page<AgentInfo> page = new Page<>(current, size);
		// 空间隔离：子智能体候选只能是当前空间内的已发布 Agent
		Page<AgentInfo> agentPage = agentInfoService.listPublishedSubAgent(page, spaceGuard.currentSpaceId());
		Page<AgentBaseInfoResp> result = new Page<>(agentPage.getCurrent(), agentPage.getSize(), agentPage.getTotal());
		result.setRecords(agentPage.getRecords().stream()
			.map(agentInfo-> AgentBaseInfoResp.builder()
				.agentId(agentInfo.getId())
				.name(agentInfo.getName())
				.agentCode(agentInfo.getAgentCode())
				.version(agentInfo.getVersion())
				.build())
			.toList());
		return result;
	}


	/**
	 * 锁 key：主体资源 + 被关联的工具 / 技能。
	 * <p>关联数量过多时退化为只锁主体，避免一次拿几十把锁把等待时间放大。</p>
	 */
	private List<String> relationLockKeys(List<String> primary, List<Long> toolIds, List<Long> skillIds) {
		List<String> bindings = LockKeys.merge(LockKeys.tools(toolIds), LockKeys.skills(skillIds));
		if (bindings.size() > MAX_RELATION_LOCKS) {
			return primary;
		}
		return LockKeys.merge(primary, bindings);
	}

	/**
	 * 失效 Agent 运行时缓存（发布 / 下架 / 删除 / 改版本后必须做，否则旧配置仍可对话）
	 *
	 * <p>本方法内部会等到事务提交后才执行，并同时完成「本机清理 + 递增代际号 + 广播」，
	 * 因此多副本部署下其它实例也会在下次请求时重建（详见 {@link AgentCacheInvalidator}）。</p>
	 */
	private void evictAgentCache(String agentCode) {
		agentCacheInvalidator.invalidate(agentCode);
	}

	/**
	 * 校验绑定的技能 / 工具与 Agent 属于同一空间（NATIVE 工具属于系统空间，允许跨空间引用）
	 */
	private void assertBindingsInSpace(Long spaceId, List<Long> skillIds, List<Long> toolIds) {
		if (!CollectionUtils.isEmpty(skillIds)) {
			agentSkillService.listByIds(skillIds)
				.forEach(skill -> spaceGuard.assertSameSpace(spaceId, skill.getWorkspaceId()));
		}
		if (!CollectionUtils.isEmpty(toolIds)) {
			agentToolService.listByIds(toolIds)
				.forEach(tool -> spaceGuard.assertSameSpace(spaceId, tool.getWorkspaceId()));
		}
	}

	private AgentPageInfoResp toAgentPageInfoResp(AgentInfo agent) {
		AgentPageInfoResp resp = new AgentPageInfoResp();
		BeanUtils.copyProperties(agent, resp);
		resp.setAgentId(agent.getId());
		AgentStatusEnum statusEnum = AgentStatusEnum.fromValue(agent.getStatus());
		resp.setStatusDesc(statusEnum != null ? statusEnum.getLabel() : agent.getStatus());
		return resp;
	}

	private AgentInfoDetailResp toAgentInfoDetailResp(AgentInfo agent) {
		AgentInfoDetailResp resp = new AgentInfoDetailResp();
		BeanUtils.copyProperties(agent, resp);
		resp.setAgentId(agent.getId());
		AgentStatusEnum statusEnum = AgentStatusEnum.fromValue(agent.getStatus());
		resp.setStatusDesc(statusEnum != null ? statusEnum.getLabel() : agent.getStatus());
		return resp;
	}

	private AgentCardInfoResp toAgentCardInfoResp(AgentInfo agent) {
		AgentCardInfoResp resp = new AgentCardInfoResp();
		BeanUtils.copyProperties(agent, resp);
		resp.setAgentId(agent.getId());
		return resp;
	}

	private SkillSimpleResp toSkillSimpleResp(AgentSkill skill) {
		SkillSimpleResp resp = new SkillSimpleResp();
		resp.setSkillId(skill.getId());
		resp.setSkillCode(skill.getSkillCode());
		resp.setName(skill.getName());
		resp.setDescription(skill.getDescription());
		return resp;
	}


	public Object getMemory(@Valid String chatId) {
		RunnableConfig config = RunnableConfig.builder()
			.threadId(chatId).build();
		Optional<Checkpoint> checkpointOpt = redisSaver.get(config);
		if (checkpointOpt.isPresent()) {
			Checkpoint checkpoint = checkpointOpt.get();
			return checkpoint.getState().get("messages");
		}
		return null;
	}
}
