package com.faust.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.faust.entity.WorkSpace;
import com.faust.enums.SpaceTypeEnum;
import com.faust.mapper.WorkSpaceMapper;
import com.faust.repository.WorkSpaceRepository;
import org.springframework.stereotype.Service;

@Service
public class WorkSpaceRepositoryImpl extends ServiceImpl<WorkSpaceMapper, WorkSpace> implements WorkSpaceRepository {

	@Override
	public WorkSpace getPersonalSpace(Long ucId) {
		LambdaQueryWrapper<WorkSpace> wrapper = Wrappers.lambdaQuery();
		wrapper.eq(WorkSpace::getOwnerUcId, ucId);
		wrapper.eq(WorkSpace::getSpaceType, SpaceTypeEnum.PERSONAL.getValue());
		wrapper.last("LIMIT 1");
		return getOne(wrapper);
	}
}
