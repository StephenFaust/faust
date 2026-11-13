package com.faust.support.lock;

import com.faust.config.property.LockProperties;
import com.faust.enums.ErrorMessageEnum;
import com.faust.exception.AgentBusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.RedissonMultiLock;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 写路径互斥：基于 Redisson 的编程式分布式锁。
 *
 * <p><b>为什么不用注解 + AOP：</b>本工程未引入 {@code spring-boot-starter-aop}，
 * 而 {@code @Aspect} 需要 aspectjweaver；离线构建无法通过。编程式调用同时规避了
 * 「同类自调用注解失效」的坑，加锁点一眼可见。</p>
 *
 * <p><b>语义约定：</b></p>
 * <ul>
 *   <li>锁在事务<b>外层</b>语义上生效：业务体执行完、事务提交后才真正释放
 *       （见 {@link #release}），避免「放锁 → 提交」之间被另一个请求读到旧数据；</li>
 *   <li>多资源操作必须一次性传入全部 key，内部做 distinct + 排序后批量加锁，
 *       保证全局加锁顺序一致，不会互相死锁；</li>
 *   <li>拿不到锁 → 1043，<b>不重试</b>；Redis 不可用 → 1044（fail-closed），
 *       绝不降级为无锁直写；</li>
 *   <li>{@code agent.center.lock.enabled=false} 时退化为直接执行（仅本地调试用）。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DistributedLockTemplate {

	/**
	 * -1 表示交给看门狗续期（默认 30s 续约），避免 DB 慢导致锁提前过期出现双写
	 */
	private static final long WATCHDOG_LEASE = -1L;

	private final RedissonClient redissonClient;

	private final LockProperties lockProperties;

	/**
	 * 加锁执行：keys 内部会去重并按字典序排序，保证加锁顺序全局一致
	 */
	public <T> T call(Collection<String> keys, long waitMillis, Supplier<T> action) {
		if (!lockProperties.isEnabled() || keys == null || keys.isEmpty()) {
			return action.get();
		}
		List<String> sortedKeys = keys.stream()
			.filter(key -> key != null && !key.isBlank())
			.distinct()
			.sorted()
			.toList();
		if (sortedKeys.isEmpty()) {
			return action.get();
		}

		RLock lock = buildLock(sortedKeys);
		long start = System.nanoTime();
		acquire(lock, sortedKeys, waitMillis);
		try {
			return action.get();
		} finally {
			release(lock, sortedKeys, start);
		}
	}

	private RLock buildLock(List<String> sortedKeys) {
		List<RLock> locks = sortedKeys.stream()
			.map(key -> (RLock) redissonClient.getLock(LockKeys.withPrefix(key)))
			.toList();
		if (locks.size() == 1) {
			return locks.get(0);
		}
		return new RedissonMultiLock(locks.toArray(new RLock[0]));
	}

	private void acquire(RLock lock, List<String> keys, long waitMillis) {
		boolean acquired;
		try {
			acquired = lock.tryLock(waitMillis, WATCHDOG_LEASE, TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new AgentBusinessException(ErrorMessageEnum.RESOURCE_LOCKED.getErrorCode(), "等待资源锁被中断，请重试");
		} catch (Exception e) {
			// Redis 不可用：fail-closed，宁可写失败也不能无锁直写
			LOGGER.error("Acquire distributed lock failed, keys={}", keys, e);
			throw new AgentBusinessException(ErrorMessageEnum.LOCK_UNAVAILABLE.getErrorCode(),
				ErrorMessageEnum.LOCK_UNAVAILABLE.getErrorMsg(), e);
		}
		if (!acquired) {
			LOGGER.warn("Distributed lock timeout after {}ms, keys={}", waitMillis, keys);
			throw new AgentBusinessException(ErrorMessageEnum.RESOURCE_LOCKED.getErrorCode(),
				ErrorMessageEnum.RESOURCE_LOCKED.getErrorMsg());
		}
	}

	/**
	 * 释放时机：有事务时注册同步回调，等事务提交/回滚后再解锁；
	 * 无事务时立即解锁。这样即使加锁点恰好落在事务内部，也不会「先放锁后提交」。
	 */
	private void release(RLock lock, List<String> keys, long startNanos) {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCompletion(int status) {
					doRelease(lock, keys, startNanos);
				}
			});
			return;
		}
		doRelease(lock, keys, startNanos);
	}

	private void doRelease(RLock lock, List<String> keys, long startNanos) {
		try {
			// 看门狗续期失败或租约过期后 unlock 会抛 IllegalMonitorStateException，先判归属
			if (lock.isHeldByCurrentThread()) {
				lock.unlock();
			}
		} catch (Exception e) {
			LOGGER.warn("Release distributed lock failed, keys={}", keys, e);
		} finally {
			LOGGER.debug("Distributed lock released, keys={}, held={}ms", keys,
				(System.nanoTime() - startNanos) / 1_000_000);
		}
	}
}
