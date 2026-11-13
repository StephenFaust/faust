package com.faust.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.faust.entity.WorkSpaceUser;
import com.faust.enums.SpaceRoleEnum;
import com.faust.mapper.WorkSpaceUserMapper;
import com.faust.repository.WorkSpaceUserRepository;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class WorkSpaceUserRepositoryImpl
	extends ServiceImpl<WorkSpaceUserMapper, WorkSpaceUser>
	implements WorkSpaceUserRepository {

	@Override
	public SpaceRoleEnum memberRole(Long spaceId, Long ucId) {
		if (spaceId == null || ucId == null) {
			return null;
		}
		LambdaQueryWrapper<WorkSpaceUser> wrapper = Wrappers.lambdaQuery();
		wrapper.eq(WorkSpaceUser::getSpaceId, spaceId);
		wrapper.eq(WorkSpaceUser::getUcId, ucId);
		wrapper.select(WorkSpaceUser::getRole);
		wrapper.last("LIMIT 1");
		WorkSpaceUser member = getOne(wrapper);
		return member == null ? null : SpaceRoleEnum.fromValue(member.getRole());
	}

	@Override
	public List<WorkSpaceUser> listByUcId(Long ucId) {
		LambdaQueryWrapper<WorkSpaceUser> wrapper = Wrappers.lambdaQuery();
		wrapper.eq(WorkSpaceUser::getUcId, ucId);
		return list(wrapper);
	}

	@Override
	public List<WorkSpaceUser> listBySpaceId(Long spaceId) {
		LambdaQueryWrapper<WorkSpaceUser> wrapper = Wrappers.lambdaQuery();
		wrapper.eq(WorkSpaceUser::getSpaceId, spaceId);
		wrapper.orderByAsc(WorkSpaceUser::getCreateTime);
		return list(wrapper);
	}

	@Override
	public Map<Long, Long> countBySpaceIds(Collection<Long> spaceIds) {
		Map<Long, Long> result = new HashMap<>();
		if (spaceIds == null || spaceIds.isEmpty()) {
			return result;
		}
		QueryWrapper<WorkSpaceUser> wrapper = new QueryWrapper<>();
		wrapper.select("space_id", "COUNT(*) AS cnt")
			.in("space_id", spaceIds)
			.groupBy("space_id");
		for (Map<String, Object> row : listMaps(wrapper)) {
			Object spaceId = row.get("space_id") != null ? row.get("space_id") : row.get("spaceId");
			Object cnt = row.get("cnt");
			if (spaceId != null && cnt != null) {
				result.put(Long.valueOf(String.valueOf(spaceId)), Long.valueOf(String.valueOf(cnt)));
			}
		}
		return result;
	}

	@Override
	public void removeBySpaceId(Long spaceId) {
		LambdaQueryWrapper<WorkSpaceUser> wrapper = Wrappers.lambdaQuery();
		wrapper.eq(WorkSpaceUser::getSpaceId, spaceId);
		remove(wrapper);
	}
}
