package com.faust.common.context;

import com.faust.enums.ErrorMessageEnum;
import com.faust.exception.AgentBusinessException;

import java.util.Optional;

/**
 * 用户信息
 */
public class UserContext {


	private UserContext() {
	}

	private static final ThreadLocal<User> userThreadLocal = new ThreadLocal<>();

	/**
	 * 设置用户信息
	 *
	 * @param user 用户信息
	 */
	public static void setUser(User user) {
		userThreadLocal.set(user);
	}

	/**
	 * 设置用户信息
	 * 如果用户信息为null，则设置当前线程的用户信息为传入的ucid、supplierCode和source
	 *
	 * @param ucId         用户UCID
	 * @param supplierCode 供应商代码
	 * @param source       来源
	 */
	public static void updateUser(Long ucId, String supplierCode, Integer source) {
		User user = new User();
		user.setUcId(ucId);
		user.setSupplierCode(supplierCode);
		user.setSource(source);
		setUser(user);
	}

	/**
	 * 更新用户信息
	 * 如果用户信息为null，则设置当前线程的用户信息为传入的user
	 * 如果用户信息不为null，则更新当前线程的用户信息
	 *
	 * @param user 用户信息
	 */
	public static void updateUser(User user) {
		if (user == null) {
			return;
		}
		User u = getUser();
		if (u == null) {
			setUser(user);
			return;
		}
		if (user.getUcId() != null) {
			u.setUcId(user.getUcId());
		}
		if (user.getSupplierCode() != null) {
			u.setSupplierCode(user.getSupplierCode());
		}
		if (user.getSource() != null) {
			u.setSource(user.getSource());
		}
		setUser(user);
	}

	/**
	 * 获取用户信息
	 *
	 * @return 用户信息
	 * @throws AgentBusinessException 如果用户未登录
	 */
	public static User getUser() {
		return Optional.ofNullable(userThreadLocal.get())
			.orElseThrow(() -> new AgentBusinessException(ErrorMessageEnum.NO_LOGIN_USER.getErrorCode(), ErrorMessageEnum.NO_LOGIN_USER.getErrorMsg()));
	}

	/**
	 * 获取用户信息（可能为空）
	 *
	 * @return 用户信息Optional包装
	 */
	public static Optional<User> getUserOptional() {
		return Optional.ofNullable(userThreadLocal.get());
	}

	/**
	 * 获取用户UCID
	 *
	 * @return 用户UCID
	 * @throws AgentBusinessException 如果用户未登录或UCID无效
	 */
	public static Long getUcId() {
		User user = getUser();
		return Optional.ofNullable(user.getUcId())
			.orElseThrow(() -> new AgentBusinessException(ErrorMessageEnum.NO_LOGIN_USER.getErrorCode(), ErrorMessageEnum.NO_LOGIN_USER.getErrorMsg()));
	}

	/**
	 * 清除当前线程的用户信息
	 */
	public static void remove() {
		userThreadLocal.remove();
	}
}
