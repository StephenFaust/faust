package com.faust.repository.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.faust.entity.AgentSkill;
import com.faust.entity.AgentSkillItem;
import com.faust.enums.ErrorMessageEnum;
import com.faust.enums.SkillStatusEnum;
import com.faust.exception.AgentBusinessException;
import com.faust.mapper.AgentSkillMapper;
import com.faust.repository.AgentSkillItemRepository;
import com.faust.repository.AgentSkillRepository;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.Collection;
import java.util.List;

@Service
public class AgentSkillRepositoryImpl extends ServiceImpl<AgentSkillMapper, AgentSkill> implements AgentSkillRepository {

	@Resource
	private AgentSkillItemRepository agentSkillItemService;

	@Override
	@Transactional(rollbackFor = Exception.class)
	public Long saveSkillWithItems(AgentSkill skill, List<AgentSkillItem> items, Long ucId) {
		validateSkillCode(skill.getSkillCode(), null);
		skill.setStatus(SkillStatusEnum.DRAFT.getValue());
		skill.setCreateBy(ucId);
		skill.setUpdateBy(ucId);
		save(skill);
		saveItems(skill.getId(), items);
		return skill.getId();
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void updateSkill(AgentSkill skill, List<AgentSkillItem> items, Long ucId) {
		AgentSkill exist = getById(skill.getId());
		if (exist == null) {
			throw new AgentBusinessException(ErrorMessageEnum.SKILL_NOT_FOUND.getErrorCode(),
				ErrorMessageEnum.SKILL_NOT_FOUND.getErrorMsg());
		}
		if (skill.getSkillCode() != null) {
			validateSkillCode(skill.getSkillCode(), skill.getId());
		}
		skill.setUpdateBy(ucId);
		// 条件更新兜底：SQL 自带 status <> 'PUBLISHED'，防止与发布操作并发时改到已发布技能
		boolean updated = lambdaUpdate()
			.eq(AgentSkill::getId, skill.getId())
			.ne(AgentSkill::getStatus, SkillStatusEnum.PUBLISHED.getValue())
			.set(skill.getSkillCode() != null, AgentSkill::getSkillCode, skill.getSkillCode())
			.set(skill.getName() != null, AgentSkill::getName, skill.getName())
			.set(skill.getDescription() != null, AgentSkill::getDescription, skill.getDescription())
			.set(skill.getKeyWords() != null, AgentSkill::getKeyWords, skill.getKeyWords())
			.set(AgentSkill::getUpdateBy, ucId)
			.update();
		if (!updated) {
			throw new AgentBusinessException(ErrorMessageEnum.SKILL_IS_PUBLISHED_NOT_EDITED.getErrorCode(),
				ErrorMessageEnum.SKILL_IS_PUBLISHED_NOT_EDITED.getErrorMsg());
		}
		List<AgentSkillItem> oldItems = agentSkillItemService.lambdaQuery()
			.eq(AgentSkillItem::getSkillId, skill.getId())
			.list();
		if (!CollectionUtils.isEmpty(oldItems)) {
			List<Long> itemIds = oldItems.stream().map(AgentSkillItem::getId).toList();
			agentSkillItemService.removeBatchByIds(itemIds);
		}
		saveItems(skill.getId(), items);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void deleteSkill(Long skillId) {
		AgentSkill skill = getById(skillId);
		if (skill == null) {
			throw new AgentBusinessException(ErrorMessageEnum.SKILL_NOT_FOUND.getErrorCode(),
				ErrorMessageEnum.SKILL_NOT_FOUND.getErrorMsg());
		}
		removeById(skillId);
		List<AgentSkillItem> agentSkillItems = agentSkillItemService.listBySkillId(skillId);
		if (CollectionUtils.isEmpty(agentSkillItems)) {
			return;
		}
		List<Long> skillItemIds = agentSkillItems.stream().map(AgentSkillItem::getId).toList();
		agentSkillItemService.getBaseMapper().deleteByIds(skillItemIds);
	}

	@Override
	public List<AgentSkill> listPublishedByIds(Collection<Long> skillIds) {
		if (CollectionUtils.isEmpty(skillIds)) {
			return List.of();
		}
		return lambdaQuery()
			.in(AgentSkill::getId, skillIds)
			.eq(AgentSkill::getStatus, SkillStatusEnum.PUBLISHED.getValue())
			.orderByAsc(AgentSkill::getId)
			.list();
	}

	/**
	 * 技能编码全局唯一校验
	 */
	private void validateSkillCode(String skillCode, Long excludeId) {
		if (skillCode == null) {
			return;
		}
		long count = lambdaQuery()
			.eq(AgentSkill::getSkillCode, skillCode)
			.ne(excludeId != null, AgentSkill::getId, excludeId)
			.count();
		if (count > 0) {
			throw new AgentBusinessException(ErrorMessageEnum.SKILL_CODE_DUPLICATE.getErrorCode(),
				ErrorMessageEnum.SKILL_CODE_DUPLICATE.getErrorMsg());
		}
	}

	private void saveItems(Long skillId, List<AgentSkillItem> items) {
		if (CollectionUtils.isEmpty(items)) {
			return;
		}
		items.forEach(item -> item.setSkillId(skillId));
		agentSkillItemService.saveBatch(items);
	}
}
