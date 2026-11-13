package com.faust.support;

import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 事务提交后执行（用于缓存失效等不能早于提交的副作用）。
 *
 * <p>提交前失效缓存存在竞态：并发请求可能把旧配置重新读回缓存；
 * 事务回滚时也不应该动缓存。</p>
 */
@Slf4j
public final class AfterCommit {

	private AfterCommit() {
	}

	public static void run(Runnable action) {
		if (action == null) {
			return;
		}
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					safely(action);
				}
			});
			return;
		}
		safely(action);
	}

	private static void safely(Runnable action) {
		try {
			action.run();
		} catch (Exception e) {
			LOGGER.warn("After-commit action failed", e);
		}
	}
}
