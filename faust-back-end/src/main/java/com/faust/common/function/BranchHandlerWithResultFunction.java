package com.faust.common.function;

import java.util.function.Supplier;

/**
 * @author stephenMao
 * @date 2025/04/08 14:56
 */
@FunctionalInterface
public interface BranchHandlerWithResultFunction {

	/**
	 * true、false分支处理
	 *
	 * @param trueHandler  true 分支
	 * @param falseHandler false 分支
	 * @param <T>          类型
	 * @return result
	 */
	<T> T trueOrFalseHandler(Supplier<T> trueHandler, Supplier<T> falseHandler);


	/**
	 * true、false分支处理
	 *
	 * @param flag true、false
	 * @return func
	 */
	static BranchHandlerWithResultFunction match(boolean flag) {
		return new BranchHandlerWithResultFunction() {
			@Override
			public <T> T trueOrFalseHandler(Supplier<T> trueHandler, Supplier<T> falseHandler) {
				if (flag) {
					return trueHandler.get();
				} else {
					return falseHandler.get();
				}
			}
		};
	}
}
