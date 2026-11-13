package com.faust.repository;

import com.baomidou.mybatisplus.extension.service.IService;
import com.faust.entity.WorkSpace;

/**
 * 工作空间仓储
 */
public interface WorkSpaceRepository extends IService<WorkSpace> {

	/**
	 * 查询某 UCID 的个人空间
	 *
	 * @param ucId 用户 UCID
	 * @return 个人空间，不存在时返回 null
	 */
	WorkSpace getPersonalSpace(Long ucId);
}
