package com.faust.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.faust.entity.AgentTool;
import com.faust.mapper.AgentToolMapper;
import com.faust.repository.AgentToolRepository;
import com.faust.enums.ErrorMessageEnum;
import com.faust.enums.ToolStatusEnum;
import com.faust.exception.AgentBusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AgentToolRepositoryImpl extends ServiceImpl<AgentToolMapper, AgentTool> implements AgentToolRepository {

	@Override
	@Transactional(rollbackFor = Exception.class)
	public Long createTool(AgentTool tool, Long ucId) {
		if (lambdaQuery().eq(AgentTool::getToolCode, tool.getToolCode()).count() > 0) {
			throw new AgentBusinessException(ErrorMessageEnum.TOOL_CODE_DUPLICATE.getErrorCode(),
				ErrorMessageEnum.TOOL_CODE_DUPLICATE.getErrorMsg());
		}
		tool.setStatus(ToolStatusEnum.UNPUBLISHED.getValue());
		tool.setCreateBy(ucId);
		tool.setUpdateBy(ucId);
		save(tool);
		return tool.getId();
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void publishTool(Long toolId, String publishVersion, Long ucId) {
		AgentTool tool = getById(toolId);
		if (tool == null) {
			throw new AgentBusinessException(ErrorMessageEnum.TOOL_NOT_FOUND.getErrorCode(),
				ErrorMessageEnum.TOOL_NOT_FOUND.getErrorMsg());
		}
		if (ToolStatusEnum.PUBLISHED.getValue().equals(tool.getStatus())) {
			throw new AgentBusinessException(ErrorMessageEnum.TOOL_ALREADY_PUBLISHED.getErrorCode(),
				ErrorMessageEnum.TOOL_ALREADY_PUBLISHED.getErrorMsg());
		}
		tool.setStatus(ToolStatusEnum.PUBLISHED.getValue());
		tool.setPublishVersion(publishVersion);
		tool.setUpdateBy(ucId);
		updateById(tool);
	}

	@Override
	public AgentTool getPublishedToolByCode(String toolCode) {
		return lambdaQuery()
			.eq(AgentTool::getToolCode, toolCode)
			.eq(AgentTool::getStatus, ToolStatusEnum.PUBLISHED.getValue())
			.one();
	}

	@Override
	public AgentTool getToolByCode(String toolCode) {
		return lambdaQuery()
			.eq(AgentTool::getToolCode, toolCode)
			.one();
	}

	@Override
	public List<AgentTool> publishedToolList(List<Long> toolIdList) {
		LambdaQueryWrapper<AgentTool> wrapper = (LambdaQueryWrapper<AgentTool>) lambdaQuery()
			.in(AgentTool::getId, toolIdList)
			.eq(AgentTool::getStatus, ToolStatusEnum.PUBLISHED.getValue())
			.getWrapper();


		return list(wrapper);
	}


	@Override
	public Page<AgentTool> listPublishedTools(Page<AgentTool> page, String keyword) {
		LambdaQueryWrapper<AgentTool> wrapper = (LambdaQueryWrapper<AgentTool>) lambdaQuery()
			.eq(AgentTool::getStatus, ToolStatusEnum.PUBLISHED.getValue())
			.getWrapper();

		if (StringUtils.isNotBlank(keyword)) {
			wrapper.and(w -> w.like(AgentTool::getToolCode, keyword)
				.or()
				.like(AgentTool::getName, keyword));
		}
		wrapper.orderByDesc(AgentTool::getCreateTime);
		return page(page, wrapper);
	}

	@Override
	public List<AgentTool> getByDbConfigId(Long dbConfigId) {
		LambdaQueryWrapper<AgentTool> wrapper = (LambdaQueryWrapper<AgentTool>) lambdaQuery()
			.eq(AgentTool::getDataSourceConfigId, dbConfigId)
			.getWrapper();

		return list(wrapper);
	}

}
