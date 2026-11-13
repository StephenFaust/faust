package com.faust.repository;

import com.baomidou.mybatisplus.extension.service.IService;
import com.faust.entity.WorkSpaceUser;
import com.faust.enums.SpaceRoleEnum;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 工作空间成员仓储
 */
public interface WorkSpaceUserRepository extends IService<WorkSpaceUser> {

	/**
	 * 查询某用户在指定空间中的角色
	 *
	 * @param spaceId 空间 ID
	 * @param ucId    用户 UCID
	 * @return 角色，非成员返回 null
	 */
	SpaceRoleEnum memberRole(Long spaceId, Long ucId);

	/**
	 * 查询用户已加入的全部成员记录（含自己创建的空间）
	 *
	 * @param ucId 用户 UCID
	 * @return 成员记录列表
	 */
	List<WorkSpaceUser> listByUcId(Long ucId);

	/**
	 * 查询空间成员
	 *
	 * @param spaceId 空间 ID
	 * @return 成员列表（创建者在前）
	 */
	List<WorkSpaceUser> listBySpaceId(Long spaceId);

	/**
	 * 批量统计各空间的成员数量，避免逐个空间查询
	 *
	 * @param spaceIds 空间 ID 集合
	 * @return spaceId -> 成员数
	 */
	Map<Long, Long> countBySpaceIds(Collection<Long> spaceIds);
	/**
	 * 删除空间内全部成员记录（解散空间时使用）
	 *
	 * @param spaceId 空间 ID
	 */
	void removeBySpaceId(Long spaceId);
}
