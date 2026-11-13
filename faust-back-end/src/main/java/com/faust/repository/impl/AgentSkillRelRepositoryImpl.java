package com.faust.repository.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.faust.entity.AgentSkill;
import com.faust.entity.AgentSkillRel;
import com.faust.enums.ErrorMessageEnum;
import com.faust.enums.SkillStatusEnum;
import com.faust.exception.AgentBusinessException;
import com.faust.mapper.AgentSkillRelMapper;
import com.faust.repository.AgentSkillRelRepository;
import com.faust.repository.AgentSkillRepository;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.List;

@Service
public class AgentSkillRelRepositoryImpl extends ServiceImpl<AgentSkillRelMapper, AgentSkillRel>
	implements AgentSkillRelRepository {

	@Resource
	private AgentSkillRepository agentSkillService;

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void bindSkillToAgent(Long agentId, Long skillId, Long ucId) {
		// 原子插入：已发布 + 同空间才能落库，避免「先查状态再插入」的窗口
		try {
			if (this.baseMapper.bindIfPublished(agentId, skillId) > 0) {
				return;
			}
		} catch (DuplicateKeyException e) {
			// 已经绑定过，接口语义是幂等
			return;
		}
		throwBindFailure(skillId);
	}

	/**
	 * 插入 0 行时，回查一次给出准确原因
	 */
	private void throwBindFailure(Long skillId) {
		AgentSkill skill = agentSkillService.getById(skillId);
		if (skill == null) {
			throw new AgentBusinessException(ErrorMessageEnum.SKILL_NOT_FOUND.getErrorCode(),
				ErrorMessageEnum.SKILL_NOT_FOUND.getErrorMsg());
		}
		if (!SkillStatusEnum.PUBLISHED.getValue().equals(skill.getStatus())) {
			throw new AgentBusinessException(ErrorMessageEnum.SKILL_NOT_PUBLISHED.getErrorCode(),
				ErrorMessageEnum.SKILL_NOT_PUBLISHED.getErrorMsg());
		}
		throw new AgentBusinessException(ErrorMessageEnum.RESOURCE_SPACE_MISMATCH.getErrorCode(),
			ErrorMessageEnum.RESOURCE_SPACE_MISMATCH.getErrorMsg());
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void unbindSkill(Long agentId, Long skillId) {
		List<AgentSkillRel> skillRelList = lambdaQuery()
			.eq(AgentSkillRel::getAgentId, agentId)
			.eq(AgentSkillRel::getSkillId, skillId)
			.list();
		if (CollectionUtils.isEmpty(skillRelList)) {
			return;
		}
		List<Long> skillRelIds = skillRelList.stream().map(AgentSkillRel::getId).toList();
		this.baseMapper.deleteByIds(skillRelIds);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void unbindAllSkill(Long agentId) {
		List<AgentSkillRel> skillRelList = lambdaQuery()
			.eq(AgentSkillRel::getAgentId, agentId)
			.list();
		if (CollectionUtils.isEmpty(skillRelList)) {
			return;
		}
		List<Long> skillRelIds = skillRelList.stream().map(AgentSkillRel::getId).toList();
		this.baseMapper.deleteByIds(skillRelIds);
	}

	@Override
	public List<Long> listSkillIdsByAgentId(Long agentId) {
		return lambdaQuery()
			.eq(AgentSkillRel::getAgentId, agentId)
			.list()
			.stream()
			.map(AgentSkillRel::getSkillId)
			.toList();
	}

	@Override
	public List<AgentSkillRel> listBySkillId(Long skillId) {
		return lambdaQuery()
			.eq(AgentSkillRel::getSkillId, skillId)
			.list();
	}
}
