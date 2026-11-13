package com.faust.common.function;


import com.faust.enums.ErrorMessageEnum;
import com.faust.exception.AgentBusinessException;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Collection;
import java.util.Objects;

/**
 * @author stephenMao
 * @date 2025/04/08 14:56
 */
@FunctionalInterface
public interface ThrowExceptionFunction {

	/**
	 * 抛出异常信息
	 *
	 * @param messageEnum 异常信息枚举
	 **/
	void throwMessage(ErrorMessageEnum messageEnum);

	/**
	 * 如果参数为true抛出异常
	 *
	 * @param flag true or false
	 * @return 异常
	 */
	static ThrowExceptionFunction isTrue(boolean flag) {
		return messageEnum -> {
			if (flag) {
				throw new AgentBusinessException(messageEnum.getErrorCode(), messageEnum.getErrorMsg());
			}
		};
	}

	/**
	 * 如果参数为false抛出异常
	 *
	 * @param flag true or false
	 * @return 异常
	 */
	static ThrowExceptionFunction isFalse(boolean flag) {
		return messageEnum -> {
			if (!flag) {
				throw new AgentBusinessException(messageEnum.getErrorCode(), messageEnum.getErrorMsg());
			}
		};
	}

	/**
	 * 如果参数为null抛出异常
	 *
	 * @param object 对象
	 * @return 异常
	 */
	static ThrowExceptionFunction isNull(Object object) {
		return messageEnum -> {
			if (Objects.isNull(object)) {
				throw new AgentBusinessException(messageEnum.getErrorCode(), messageEnum.getErrorMsg());
			}
		};
	}

	/**
	 * 如果参数为不为null抛出异常
	 *
	 * @param object 对象
	 * @return 异常
	 */
	static ThrowExceptionFunction nonNull(Object object) {
		return messageEnum -> {
			if (Objects.nonNull(object)) {
				throw new AgentBusinessException(messageEnum.getErrorCode(), messageEnum.getErrorMsg());
			}
		};
	}

	/**
	 * 如果列表为空抛出异常
	 *
	 * @param objects 列表
	 * @return 异常
	 */
	static ThrowExceptionFunction isEmpty(Collection<?> objects) {
		return messageEnum -> {
			if (CollectionUtils.isEmpty(objects)) {
				throw new AgentBusinessException(messageEnum.getErrorCode(), messageEnum.getErrorMsg());
			}
		};
	}


	/**
	 * 如果列表不为空抛出异常
	 *
	 * @param objects 列表
	 * @return 异常
	 */
	static ThrowExceptionFunction isNotEmpty(Collection<?> objects) {
		return messageEnum -> {
			if (CollectionUtils.isNotEmpty(objects)) {
				throw new AgentBusinessException(messageEnum.getErrorCode(), messageEnum.getErrorMsg());
			}
		};
	}

	/**
	 * 字符串不为空抛出异常
	 *
	 * @param cs 字符序列
	 * @return 异常
	 */
	static ThrowExceptionFunction isNotBlank(CharSequence cs) {
		return messageEnum -> {
			if (StringUtils.isNotBlank(cs)) {
				throw new AgentBusinessException(messageEnum.getErrorCode(), messageEnum.getErrorMsg());
			}
		};
	}

	/**
	 * 字符串为空抛出异常
	 *
	 * @param cs 字符序列
	 * @return 异常
	 */
	static ThrowExceptionFunction isBlank(CharSequence cs) {
		return messageEnum -> {
			if (StringUtils.isBlank(cs)) {
				throw new AgentBusinessException(messageEnum.getErrorCode(), messageEnum.getErrorMsg());
			}
		};
	}

}
