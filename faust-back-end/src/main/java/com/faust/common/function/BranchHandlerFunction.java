package com.faust.common.function;

/**
 * @author stephenMao
 * @date 2025/04/08 14:56
 */
@FunctionalInterface
public interface BranchHandlerFunction {

	/**
	 * true、false分支处理
	 *
	 * @param trueHandler  true 分支
	 * @param falseHandler false 分支
	 */
	void trueOrFalseHandler(Runnable trueHandler, Runnable falseHandler);


	/**
	 * true、false分支处理
	 *
	 * @param flag true、false
	 * @return func
	 * @date 2022/5/16 14:59
	 */
	static BranchHandlerFunction match(boolean flag) {
		return (trueHandler, falseHandler) -> {
			if (flag) {
				trueHandler.run();
			} else {
				falseHandler.run();
			}
		};
	}
}
