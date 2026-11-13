package com.faust.repository;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.faust.entity.AgentInfo;

import java.util.List;

public interface AgentInfoRepository extends IService<AgentInfo> {

	Long saveAgent(AgentInfo agentInfo, Long ucId);

	List<AgentInfo> getPublishedAgents();

	AgentInfo getPublishedAgentByCodeAndVersion(String agentCode, String version);

	/**
	 * 更新 Agent（版本 CAS：仅当库里仍是 expectedVersion 时才更新成功）
	 *
	 * @param expectedVersion 编辑前的版本号，用于判断是否有人抢先提交
	 */
	void updateAgent(AgentInfo agentInfo, Long ucId, String expectedVersion);

	/**
	 * 条件更新状态：仅当当前状态不等于 targetStatus 时才更新。
	 *
	 * @return false 表示状态已是目标值（被并发改过或重复操作）
	 */
	boolean updateStatusIfChanged(Long agentId, String targetStatus, Long ucId);

	/**
	 * 条件删除：仅删除未发布的 Agent，避免「检查时未发布 → 删除时已发布」
	 *
	 * @return false 表示已被并发改为发布态或已删除
	 */
	boolean deleteIfNotPublished(Long agentId);

	Page<AgentInfo> listAgents(Page<AgentInfo> page, Long workspaceId, String keyword, List<String> status,
							  String classification, List<String> tags);

	/**
	 * 查询空间内「已发布」的 Agent（智能体页 / 卡片列表使用）。
	 *
	 * @param workspaceId 当前工作空间
	 */
	Page<AgentInfo> listPublished(Page<AgentInfo> page, Long workspaceId, String keyword, List<String> status,
								  String classification, List<String> tags);

	/**
	 * 查询空间内可作为子智能体的已发布 Agent
	 *
	 * @param workspaceId 当前工作空间
	 */
	Page<AgentInfo> listPublishedSubAgent(Page<AgentInfo> page, Long workspaceId);

	AgentInfo getAgentByCode(String agentCode);


	void increaseUseNum(String agentCode, String version);


}
