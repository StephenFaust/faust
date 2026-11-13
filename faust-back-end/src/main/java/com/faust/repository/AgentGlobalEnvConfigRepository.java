package com.faust.repository;

import com.baomidou.mybatisplus.extension.service.IService;
import com.faust.entity.AgentGlobalEnvConfig;

import java.util.Map;

/**
 * Agent 全局环境变量仓储（Agent + 用户维度）
 */
public interface AgentGlobalEnvConfigRepository extends IService<AgentGlobalEnvConfig> {

	/**
	 * 查询某用户在某 Agent 下的全局环境变量
	 *
	 * @param agentCode Agent 编码
	 * @param ucId      用户 UCID
	 * @return key-value，未配置时返回空 Map
	 */
	Map<String, String> getEnvVar(String agentCode, Long ucId);

	/**
	 * 保存（全量替换）某用户在某 Agent 下的全局环境变量，不存在则插入
	 *
	 * @param agentCode Agent 编码
	 * @param ucId      用户 UCID
	 * @param envVar    环境变量，空 Map 表示清空
	 */
	void saveEnvVar(String agentCode, Long ucId, Map<String, String> envVar);
}
