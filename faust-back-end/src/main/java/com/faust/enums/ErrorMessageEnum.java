package com.faust.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author stephenMao
 * @date 2025/04/08
 **/
@AllArgsConstructor
@Getter
public enum ErrorMessageEnum {

	//错误信息

	NO_LOGIN_USER(-1, "用户未登录"),
	SUCCESS(0, "成功"),
	INTERNAL_ERROR(1, "系统内部异常,请联系管理员"),
	PARAMETER_ERROR(2, "参数错误"),

	REQUEST_NOT_NULL(3, "请求参数不能为空"),
	SESSION_NOT_BLANK(4, "会话id不能为空"),
	USER_MESSAGE_NOT_BLANK(5, "用户消息不能为空"),
	AGENT_IS_NULL(6, "agent实例获取失败"),
	AGENT_VERSION_NOT_BLANK(7, "agent版本不能为空"),
	AGENT_CODE_NOT_BLANK(8, "agent编码不能为空"),

	// 业务错误码
	AGENT_NOT_FOUND(1001, "Agent不存在，请联系管理员"),

	AGENT_CREATE_FAILED(1002, "Agent创建失败"),
	MODEL_CONFIG_NOT_FOUND(1003, "模型配置不存在"),
	SKILL_NOT_FOUND(1004, "Skill不存在"),
	TOOL_NOT_FOUND(1005, "工具不存在"),
	TOOL_NOT_PUBLISHED(1006, "工具未发布，无法绑定"),
	AGENT_VERSION_DUPLICATE(1007, "同一Agent编码下版本号已存在"),
	AGENT_SKILL_CODE_DUPLICATE(1008, "同一Agent下技能编码已存在"),
	TOOL_CODE_DUPLICATE(1009, "工具编码已存在"),
	AGENT_PUBLISHED_EXISTS(1010, "同一Agent编码下已存在发布版本"),
	PROMPT_HISTORY_NOT_FOUND(1011, "提示词历史版本不存在"),
	TOOL_ALREADY_PUBLISHED(1012, "工具已发布"),
	PROMPT_NOT_FOUND(1013, "提示词不存在"),
	CHAT_NOT_FOUND(1014, "会话不存在"),
	DATA_SOURCE_CONFIG_NOT_FOUND(1015, "数据源配置不存在"),
	DATA_SOURCE_CONNECT_FAILED(1016, "数据源连接失败"),
	AGENT_IS_PUBLISHED_TO_DELETED(1017, "agent已发布，不能删除"),
	REL_TOOL_DATA_SOURCE_CONFIG_NOT_DELETED(1018, "有关联工具使用，不能删除"),
	REL_TOOL_AGENT_NOT_OPERATE(1019, "有关联agent使用，不能操作"),
	TOOL_IS_PUBLISHED_NOT_DELETED(1020, "工具已经发布，不能删除"),
	FILE_SIZE_EXCEED(1021, "文件大小不能超过200MB"),
	FILE_UPLOAD_FAILED(1022, "文件上传失败"),
	TOOL_NATIVE_NOT_OPERABLE(1023, "原生工具不支持编辑、发布、下架、删除操作"),

	SKILL_CODE_DUPLICATE(1027, "技能编码已存在"),
	SKILL_NOT_PUBLISHED(1028, "技能未发布，无法绑定"),
	SKILL_ALREADY_PUBLISHED(1029, "技能已发布"),
	SKILL_IS_PUBLISHED_NOT_DELETED(1030, "技能已经发布，不能删除"),
	REL_SKILL_AGENT_NOT_OPERATE(1031, "有关联agent使用，不能操作"),

	// 工作空间
	SPACE_NOT_FOUND(1032, "工作空间不存在"),
	SPACE_NO_PERMISSION(1033, "无权访问该工作空间"),
	SPACE_NOT_SELECTED(1034, "请先选择工作空间"),
	SPACE_NOT_EMPTY(1035, "空间内仍有资源，无法解散"),
	SPACE_MEMBER_EXISTS(1036, "该用户已在空间中"),
	SPACE_MEMBER_NOT_FOUND(1037, "空间成员不存在"),
	SPACE_OWNER_CANNOT_QUIT(1038, "创建者不能退出，请先解散空间"),
	SPACE_TYPE_INVALID(1039, "空间类型不支持该操作"),
	SPACE_PERSONAL_NOT_ALLOWED(1040, "个人空间不支持该操作"),
	RESOURCE_SPACE_MISMATCH(1041, "资源不属于当前工作空间"),
	SPACE_SYSTEM_READONLY(1042, "系统空间只读，不支持该操作"),

	// 并发控制
	RESOURCE_LOCKED(1043, "该资源正在被其他成员操作，请稍后重试"),
	LOCK_UNAVAILABLE(1044, "并发控制服务暂不可用，请稍后重试"),
	CONCURRENT_MODIFIED(1045, "内容已被他人修改，请刷新后重试"),
	RESOURCE_ALREADY_TAKEOFF(1046, "资源已下架，请刷新后重试"),
	STATUS_CHANGED(1047, "资源状态已变更，请刷新后重试"),
	SKILL_IS_PUBLISHED_NOT_EDITED(1048, "技能已发布，请先下架后再编辑");

	private final int errorCode;
	private final String errorMsg;
}
