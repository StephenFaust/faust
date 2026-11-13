package com.faust.agent.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent 运行时缓存的「代际号」。
 *
 * <p>本地缓存是每台实例各一份，单机 {@code invalidate} 无法通知其它实例。
 * 代际号存在 Redis，作为跨实例的权威版本：缓存 key 带上代际号，
 * 只要代际号变化，所有实例的下一次请求都会自然 miss 并重建。</p>
 *
 * <p>代际号的好处是<b>不依赖广播可达性</b>：订阅连接断开、消息丢失都只会影响
 * 「旧对象何时释放」，不会造成长期脏读。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentCacheGeneration {

	private static final String GEN_KEY_PREFIX = "faust:agent:gen:";

	private final RedissonClient redissonClient;

	/**
	 * 最近一次读到的代际号：Redis 不可用时用它兜底，避免整条链路直接失败。
	 */
	private final Map<String, Long> lastKnown = new ConcurrentHashMap<>();

	/**
	 * 当前代际号。Redis 读失败时返回本机最后一次读到的值（可能落后，但不阻塞对话）。
	 */
	public long current(String agentCode) {
		if (agentCode == null || agentCode.isBlank()) {
			return 0L;
		}
		try {
			long generation = atomicLong(agentCode).get();
			lastKnown.put(agentCode, generation);
			return generation;
		} catch (Exception e) {
			long fallback = lastKnown.getOrDefault(agentCode, 0L);
			LOGGER.warn("Read agent cache generation failed, fallback to local value. agentCode={}, generation={}",
				agentCode, fallback, e);
			return fallback;
		}
	}

	/**
	 * 递增代际号：必须在事务提交后调用，否则其它实例会读到未提交的旧数据。
	 *
	 * @return 是否成功。失败时不抛异常——本地缓存已经清掉，最坏靠 TTL 收敛
	 */
	public boolean bump(String agentCode) {
		if (agentCode == null || agentCode.isBlank()) {
			return false;
		}
		try {
			long generation = atomicLong(agentCode).incrementAndGet();
			lastKnown.put(agentCode, generation);
			LOGGER.info("Agent cache generation bumped, agentCode={}, generation={}", agentCode, generation);
			return true;
		} catch (Exception e) {
			LOGGER.warn("Bump agent cache generation failed, instances will converge by ttl. agentCode={}",
				agentCode, e);
			return false;
		}
	}

	private RAtomicLong atomicLong(String agentCode) {
		return redissonClient.getAtomicLong(GEN_KEY_PREFIX + agentCode);
	}
}
