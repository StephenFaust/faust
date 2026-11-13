package com.faust.repository;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.faust.entity.AgentTool;

import java.util.List;

public interface AgentToolRepository extends IService<AgentTool> {

	Long createTool(AgentTool tool, Long ucId);

	void publishTool(Long toolId, String publishVersion, Long ucId);

	/**
	 * 根据 toolCode 查询已发布的工具
	 */
	AgentTool getPublishedToolByCode(String toolCode);

	AgentTool getToolByCode(String toolCode);

	List<AgentTool> publishedToolList(List<Long> toolIdList);

	Page<AgentTool> listPublishedTools(Page<AgentTool> page, String keyword);

	List<AgentTool> getByDbConfigId(Long dbConfigId);
}
