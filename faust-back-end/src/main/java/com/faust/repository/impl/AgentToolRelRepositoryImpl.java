package com.faust.repository.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.faust.entity.AgentTool;
import com.faust.entity.AgentToolRel;
import com.faust.mapper.AgentToolRelMapper;
import com.faust.repository.AgentToolRelRepository;
import com.faust.repository.AgentToolRepository;
import com.faust.enums.ErrorMessageEnum;
import com.faust.enums.ToolStatusEnum;
import com.faust.exception.AgentBusinessException;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.List;

@Service
public class AgentToolRelRepositoryImpl extends ServiceImpl<AgentToolRelMapper, AgentToolRel> implements AgentToolRelRepository {

	@Resource
	private AgentToolRepository agentToolService;

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void bindToolToAgent(Long agentId, Long toolId, Long ucId) {
		// 原子插入：已发布 + 同空间才能落库，避免「先查状态再插入」的窗口
		try {
			if (this.baseMapper.bindIfPublished(agentId, toolId) > 0) {
				return;
			}
		} catch (DuplicateKeyException e) {
			// 已经绑定过，接口语义是幂等
			return;
		}
		throwBindFailure(toolId);
	}

	/**
	 * 插入 0 行时，回查一次给出准确原因（此时工具状态不会再变，报错文案可信）
	 */
	private void throwBindFailure(Long toolId) {
		AgentTool tool = agentToolService.getById(toolId);
		if (tool == null) {
			throw new AgentBusinessException(ErrorMessageEnum.TOOL_NOT_FOUND.getErrorCode(),
				ErrorMessageEnum.TOOL_NOT_FOUND.getErrorMsg());
		}
		if (!ToolStatusEnum.PUBLISHED.getValue().equals(tool.getStatus())) {
			throw new AgentBusinessException(ErrorMessageEnum.TOOL_NOT_PUBLISHED.getErrorCode(),
				ErrorMessageEnum.TOOL_NOT_PUBLISHED.getErrorMsg());
		}
		throw new AgentBusinessException(ErrorMessageEnum.RESOURCE_SPACE_MISMATCH.getErrorCode(),
			ErrorMessageEnum.RESOURCE_SPACE_MISMATCH.getErrorMsg());
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void unbindTool(Long agentId, Long toolId) {
		List<AgentToolRel> toolRelList = lambdaQuery()
			.eq(AgentToolRel::getAgentId, agentId)
			.eq(AgentToolRel::getToolId, toolId).list();
		if (CollectionUtils.isEmpty(toolRelList)) {
			return;
		}
		List<Long> toolRelIds = toolRelList.stream().map(AgentToolRel::getId).toList();
		this.baseMapper.deleteByIds(toolRelIds);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void unbindAllTool(Long agentId) {
		List<AgentToolRel> toolRelList = lambdaQuery()
			.eq(AgentToolRel::getAgentId, agentId)
			.list();
		if (CollectionUtils.isEmpty(toolRelList)) {
			return;
		}
		List<Long> toolRelIds = toolRelList.stream().map(AgentToolRel::getId).toList();
		this.baseMapper.deleteByIds(toolRelIds);
	}

	@Override
	public List<Long> listToolIdsByAgentId(Long agentId) {
		return lambdaQuery()
			.eq(AgentToolRel::getAgentId, agentId)
			.list()
			.stream()
			.map(AgentToolRel::getToolId)
			.toList();
	}

	@Override
	public List<AgentToolRel> listByToolId(Long toolId) {
		return lambdaQuery()
			.eq(AgentToolRel::getToolId, toolId)
			.list();
	}
}
