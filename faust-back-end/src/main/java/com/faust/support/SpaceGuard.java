package com.faust.support;

import com.faust.common.context.SpaceContext;
import com.faust.common.context.UserContext;
import com.faust.common.function.ThrowExceptionFunction;
import com.faust.constant.SpaceConstants;
import com.faust.enums.ErrorMessageEnum;
import com.faust.enums.SpaceRoleEnum;
import com.faust.repository.WorkSpaceRepository;
import com.faust.repository.WorkSpaceUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 空间鉴权统一入口。
 *
 * <p>两条原则：</p>
 * <ol>
 *   <li><b>列表/新建</b>：使用「当前空间」，即请求头 x-space-id 解析出的空间；</li>
 *   <li><b>单个资源</b>：使用「资源所属空间」鉴权，先查资源再校验成员身份，
 *       这样即使前端篡改请求头也越权不到别的空间（防 IDOR）。</li>
 * </ol>
 */
@Component
@RequiredArgsConstructor
public class SpaceGuard {

	private final WorkSpaceUserRepository workSpaceUserRepository;

	private final WorkSpaceRepository workSpaceRepository;

	/**
	 * 当前请求空间 ID（列表过滤、新建资源时使用）
	 */
	public Long currentSpaceId() {
		Long spaceId = SpaceContext.getSpaceId();
		ThrowExceptionFunction.isNull(spaceId).throwMessage(ErrorMessageEnum.SPACE_NOT_SELECTED);
		return spaceId;
	}

	/**
	 * 校验空间仍然存在。
	 *
	 * <p>用于「创建资源」拿到空间锁之后的二次校验：排队期间空间可能已被解散，
	 * 不校验就会把资源写进一个已经不存在、谁都无法访问的空间。</p>
	 */
	public void assertSpaceExists(Long spaceId) {
		boolean missing = spaceId == null || workSpaceRepository.getById(spaceId) == null;
		ThrowExceptionFunction.isTrue(missing).throwMessage(ErrorMessageEnum.SPACE_NOT_FOUND);
	}

	/**
	 * 校验当前用户是某空间的成员。
	 *
	 * @param workspaceId 资源所属空间，0 表示系统资源（人人可读）
	 */
	public void assertMember(Long workspaceId) {
		if (workspaceId == null || Objects.equals(workspaceId, SpaceConstants.SYSTEM_SPACE_ID)) {
			return;
		}
		SpaceRoleEnum role = workSpaceUserRepository.memberRole(workspaceId, UserContext.getUcId());
		ThrowExceptionFunction.isNull(role).throwMessage(ErrorMessageEnum.SPACE_NO_PERMISSION);
	}

	/**
	 * 校验当前用户是某空间的创建者（重命名 / 移除成员 / 解散）
	 */
	public void assertOwner(Long spaceId) {
		SpaceRoleEnum role = workSpaceUserRepository.memberRole(spaceId, UserContext.getUcId());
		ThrowExceptionFunction.isTrue(role != SpaceRoleEnum.OWNER)
			.throwMessage(ErrorMessageEnum.SPACE_NO_PERMISSION);
	}

	/**
	 * 跨空间引用校验：被引用资源必须同空间，或属于系统空间（NATIVE 工具）。
	 *
	 * @param ownerWorkspaceId  引用方所属空间
	 * @param targetWorkspaceId 被引用方所属空间
	 */
	public void assertSameSpace(Long ownerWorkspaceId, Long targetWorkspaceId) {
		boolean systemTarget = targetWorkspaceId == null
			|| Objects.equals(targetWorkspaceId, SpaceConstants.SYSTEM_SPACE_ID);
		ThrowExceptionFunction.isTrue(!systemTarget && !Objects.equals(ownerWorkspaceId, targetWorkspaceId))
			.throwMessage(ErrorMessageEnum.RESOURCE_SPACE_MISMATCH);
	}

	/**
	 * 系统空间 / 系统资源不可写
	 */
	public void assertNotSystemSpace(Long workspaceId) {
		ThrowExceptionFunction.isTrue(Objects.equals(workspaceId, SpaceConstants.SYSTEM_SPACE_ID))
			.throwMessage(ErrorMessageEnum.SPACE_SYSTEM_READONLY);
	}
}
