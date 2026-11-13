package com.faust.agent.cache;

import com.faust.agent.factory.AgentFactory;
import com.faust.support.AfterCommit;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Objects;
import java.util.UUID;

/**
 * Agent 运行时缓存的分布式失效入口。
 *
 * <p>多副本部署时，单机的 {@code invalidate} 只影响自己。这里的做法是<b>双保险</b>：</p>
 * <ol>
 *   <li><b>代际号（权威）</b>：Redis 里递增 {@code faust:agent:gen:{code}}，
 *       缓存 key 带代际号，任何实例下次取 Agent 都会因代际不符而重建；</li>
 *   <li><b>广播（加速）</b>：通过 Redis Topic 通知所有实例立刻释放旧对象，
 *       不必等下一次请求或 TTL。消息丢了也不影响正确性，只影响释放时机。</li>
 * </ol>
 *
 * <p>调用约定：所有失效必须走 {@link #invalidate(String)}，并且它内部会等到
 * 事务提交后再执行——提交前失效会被并发请求把旧配置重新读回缓存。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentCacheInvalidator {

	/**
	 * 失效广播频道
	 */
	private static final String TOPIC_NAME = "faust:cache:agent";

	/**
	 * 消息分隔符：{@code agentCode|instanceId}，用于跳过自己发的消息
	 */
	private static final String SEPARATOR = "|";

	private final RedissonClient redissonClient;
	private final AgentCacheGeneration cacheGeneration;
	private final AgentFactory agentFactory;

	private final String instanceId = UUID.randomUUID().toString();

	private RTopic topic;

	private volatile boolean subscribed;

	@PostConstruct
	public void subscribe() {
		ensureSubscribed();
	}

	/**
	 * 订阅失败（如启动时 Redis 未就绪）不阻塞启动：代际号仍能保证正确性。
	 * 后续每次发布前再补一次订阅，避免永久处于「只听不到」的状态。
	 */
	private void ensureSubscribed() {
		if (subscribed) {
			return;
		}
		try {
			RTopic newTopic = redissonClient.getTopic(TOPIC_NAME, StringCodec.INSTANCE);
			newTopic.addListener(String.class, (channel, message) -> {
				String agentCode = parseAgentCode(message);
				if (agentCode == null || isSelf(message)) {
					return;
				}
				agentFactory.evictByAgent(agentCode);
				LOGGER.info("Agent cache evicted by remote instance, agentCode={}", agentCode);
			});
			topic = newTopic;
			subscribed = true;
			LOGGER.info("Subscribed agent cache invalidation topic, instanceId={}", instanceId);
		} catch (Exception e) {
			// 订阅失败不影响正确性：代际号仍会让其它实例在下一次请求时重建
			LOGGER.error("Subscribe agent cache invalidation topic failed, fallback to generation only", e);
		}
	}

	@PreDestroy
	public void unsubscribe() {
		if (topic != null) {
			try {
				topic.removeAllListeners();
			} catch (Exception e) {
				LOGGER.warn("Unsubscribe agent cache invalidation topic failed", e);
			}
		}
	}

	/**
	 * 让所有实例的该 Agent 缓存失效（含依赖它的父 Agent）。
	 *
	 * <p>顺序：本机清理 → 递增代际号 → 广播。代际号在前可以保证即使广播失败，
	 * 其它实例也会在下一次请求时重建。</p>
	 */
	public void invalidate(String agentCode) {
		if (agentCode == null || agentCode.isBlank()) {
			return;
		}
		AfterCommit.run(() -> {
			agentFactory.evictByAgent(agentCode);
			cacheGeneration.bump(agentCode);
			publish(agentCode);
		});
	}

	/**
	 * 批量失效：工具 / 技能 / 数据源变更后，按「引用它的 Agent」逐个失效
	 */
	public void invalidateAll(Collection<String> agentCodes) {
		if (agentCodes == null || agentCodes.isEmpty()) {
			return;
		}
		agentCodes.stream()
			.filter(Objects::nonNull)
			.filter(code -> !code.isBlank())
			.distinct()
			.forEach(this::invalidate);
	}

	private void publish(String agentCode) {
		try {
			ensureSubscribed();
			if (topic == null) {
				topic = redissonClient.getTopic(TOPIC_NAME, StringCodec.INSTANCE);
			}
			topic.publish(agentCode + SEPARATOR + instanceId);
		} catch (Exception e) {
			LOGGER.warn("Publish agent cache invalidation failed, remote instances will converge by generation. "
				+ "agentCode={}", agentCode, e);
		}
	}

	/**
	 * 解析 {@code agentCode|instanceId}；兼容只发 agentCode 的旧消息
	 */
	private String parseAgentCode(String message) {
		if (message == null || message.isBlank()) {
			return null;
		}
		int index = message.indexOf(SEPARATOR);
		return index > 0 ? message.substring(0, index) : message;
	}

	private boolean isSelf(String message) {
		int index = message == null ? -1 : message.indexOf(SEPARATOR);
		return index > 0 && message.substring(index + 1).equals(instanceId);
	}
}
