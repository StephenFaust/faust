package com.faust.constant;

/**
 * 工作空间相关常量
 */
public final class SpaceConstants {

	private SpaceConstants() {
	}

	/**
	 * 当前空间请求头：由前端在每次请求中注入，缺省时后端回落到个人空间
	 */
	public static final String SPACE_ID_HEADER = "x-space-id";

	/**
	 * 系统空间 ID：存放 NATIVE 工具，所有空间可见、只读
	 */
	public static final Long SYSTEM_SPACE_ID = 0L;

	/**
	 * 个人空间默认名称
	 */
	public static final String PERSONAL_SPACE_NAME = "我的个人空间";
}
