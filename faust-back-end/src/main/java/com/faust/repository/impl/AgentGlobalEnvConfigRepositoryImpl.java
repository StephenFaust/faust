package com.faust.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.faust.entity.AgentGlobalEnvConfig;
import com.faust.mapper.AgentGlobalEnvConfigMapper;
import com.faust.repository.AgentGlobalEnvConfigRepository;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
public class AgentGlobalEnvConfigRepositoryImpl
	extends ServiceImpl<AgentGlobalEnvConfigMapper, AgentGlobalEnvConfig>
	implements AgentGlobalEnvConfigRepository {

	/**
	 * JSON 字段的写回方式，与 conversation_record.env_var 保持一致
	 */
	private static final String ENV_VAR_TYPE_HANDLER =
		"javaType=java.util.Map,jdbcType=VARCHAR,typeHandler=com.faust.common.typehandler.JsonMapTypeHandler";

	@Override
	public Map<String, String> getEnvVar(String agentCode, Long ucId) {
		if (StringUtils.isBlank(agentCode) || ucId == null) {
			return new HashMap<>(0);
		}
		LambdaQueryWrapper<AgentGlobalEnvConfig> wrapper = Wrappers.lambdaQuery();
		wrapper.select(AgentGlobalEnvConfig::getEnvVar);
		wrapper.eq(AgentGlobalEnvConfig::getAgentCode, agentCode);
		wrapper.eq(AgentGlobalEnvConfig::getUcId, ucId);
		wrapper.last("LIMIT 1");
		AgentGlobalEnvConfig config = getOne(wrapper);
		if (config == null || MapUtils.isEmpty(config.getEnvVar())) {
			return new HashMap<>(0);
		}
		return new HashMap<>(config.getEnvVar());
	}

	@Override
	public void saveEnvVar(String agentCode, Long ucId, Map<String, String> envVar) {
		Map<String, String> value = envVar == null ? new HashMap<>(0) : envVar;
		if (updateByAgentAndUc(agentCode, ucId, value)) {
			return;
		}
		AgentGlobalEnvConfig config = AgentGlobalEnvConfig.builder()
			.agentCode(agentCode)
			.ucId(ucId)
			.envVar(value)
			.build();
		try {
			save(config);
		} catch (DuplicateKeyException e) {
			// 并发首次保存：另一请求已插入（uk_agent_code_uc_id），退化为更新
			updateByAgentAndUc(agentCode, ucId, value);
		}
	}

	private boolean updateByAgentAndUc(String agentCode, Long ucId, Map<String, String> envVar) {
		LambdaUpdateWrapper<AgentGlobalEnvConfig> wrapper = Wrappers.lambdaUpdate();
		wrapper.eq(AgentGlobalEnvConfig::getAgentCode, agentCode)
			.eq(AgentGlobalEnvConfig::getUcId, ucId)
			.set(AgentGlobalEnvConfig::getEnvVar, envVar, ENV_VAR_TYPE_HANDLER)
			.set(AgentGlobalEnvConfig::getUpdateTime, LocalDateTime.now());
		return update(wrapper);
	}
}
