package com.faust.repository.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.faust.entity.AgentSkillItem;
import com.faust.mapper.AgentSkillItemMapper;
import com.faust.repository.AgentSkillItemRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AgentSkillItemRepositoryImpl extends ServiceImpl<AgentSkillItemMapper, AgentSkillItem> implements AgentSkillItemRepository {

	@Override
	public List<AgentSkillItem> listBySkillId(Long skillId) {
		return lambdaQuery()
				.eq(AgentSkillItem::getSkillId, skillId)
				.orderByAsc(AgentSkillItem::getSortOrder)
				.orderByAsc(AgentSkillItem::getId)
				.list();
	}


	@Override
	public List<AgentSkillItem> listBySkillIdList(List<Long> skillIdList) {
		return lambdaQuery()
				.in(AgentSkillItem::getSkillId, skillIdList)
				.orderByAsc(AgentSkillItem::getSortOrder)
				.orderByAsc(AgentSkillItem::getId)
				.list();
	}
}
