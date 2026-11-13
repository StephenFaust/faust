package com.faust.common.context;

import com.faust.constant.SpaceConstants;
import com.faust.enums.SpaceRoleEnum;

/**
 * 当前请求的工作空间上下文。
 * <p>由 {@link com.faust.interceptor.SpaceInterceptor} 在进入 Controller 前写入，
 * 请求结束后清理。注意：SSE 等响应式链路不会继承 ThreadLocal，
 * 需要在同步段取出后以参数形式传递。</p>
 */
public final class SpaceContext {

	private SpaceContext() {
	}

	private static final ThreadLocal<Long> SPACE_ID = new ThreadLocal<>();
	private static final ThreadLocal<SpaceRoleEnum> ROLE = new ThreadLocal<>();

	public static void set(Long spaceId, SpaceRoleEnum role) {
		SPACE_ID.set(spaceId);
		ROLE.set(role);
	}

	/**
	 * 当前空间 ID，未初始化时返回 null
	 */
	public static Long getSpaceId() {
		return SPACE_ID.get();
	}

	/**
	 * 当前空间角色
	 */
	public static SpaceRoleEnum getRole() {
		return ROLE.get();
	}

	/**
	 * 是否为系统空间（NATIVE 工具归属地，只读）
	 */
	public static boolean isSystemSpace() {
		return SpaceConstants.SYSTEM_SPACE_ID.equals(SPACE_ID.get());
	}

	public static void remove() {
		SPACE_ID.remove();
		ROLE.remove();
	}
}
