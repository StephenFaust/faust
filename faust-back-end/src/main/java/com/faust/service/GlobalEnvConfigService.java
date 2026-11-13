package com.faust.service;

import com.faust.dto.env.req.GlobalEnvVarSaveReq;
import com.faust.enums.ErrorMessageEnum;
import com.faust.exception.AgentBusinessException;
import com.faust.repository.AgentGlobalEnvConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Agent 全局环境变量（Agent + 用户维度）
 *
 * <p>语义：</p>
 * <ul>
 *   <li>每个「Agent × 用户」各自维护一份默认变量，互不影响；</li>
 *   <li>全局变量只是"新建会话时的默认值"，保存后不会回溯修改已有会话；</li>
 *   <li>新建该 Agent 的会话时由 {@code ChatService} 复制进 {@code conversation_record.env_var}，
 *       之后会话内的增删改只作用于该会话；</li>
 *   <li>加载历史会话时读的仍是 {@code conversation_record.env_var}。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GlobalEnvConfigService {

	private static final int MAX_SIZE = 50;
	private static final int KEY_MAX_LEN = 100;
	private static final int VALUE_MAX_LEN = 300;
	private static final Pattern KEY_PATTERN = Pattern.compile("^[A-Za-z](?:[A-Za-z0-9_-]*[A-Za-z0-9])?$");

	private final AgentGlobalEnvConfigRepository globalEnvConfigRepository;

	/**
	 * 查询当前用户在某 Agent 下的全局环境变量
	 */
	public Map<String, String> getEnvVar(String agentCode, Long ucId) {
		if (StringUtils.isBlank(agentCode)) {
			return new LinkedHashMap<>();
		}
		return globalEnvConfigRepository.getEnvVar(agentCode.trim(), ucId);
	}

	/**
	 * 全量保存当前用户在某 Agent 下的全局环境变量，空集合表示清空
	 */
	public boolean save(GlobalEnvVarSaveReq req, Long ucId) {
		String agentCode = StringUtils.trimToEmpty(req == null ? null : req.getAgentCode());
		if (StringUtils.isBlank(agentCode)) {
			throw parameterError("agentCode 不能为空");
		}
		Map<String, String> normalized = normalize(req == null ? null : req.getEnvVar());
		globalEnvConfigRepository.saveEnvVar(agentCode, ucId, normalized);
		LOGGER.info("Global env config saved, agentCode={}, ucId={}, size={}", agentCode, ucId, normalized.size());
		return true;
	}

	/**
	 * 校验并归一化：去掉空 key、校验 key 格式与长度、value 长度
	 */
	private Map<String, String> normalize(Map<String, String> source) {
		Map<String, String> result = new LinkedHashMap<>();
		if (MapUtils.isEmpty(source)) {
			return result;
		}
		if (source.size() > MAX_SIZE) {
			throw parameterError("环境变量最多 " + MAX_SIZE + " 个");
		}
		source.forEach((rawKey, rawValue) -> {
			String key = StringUtils.trimToEmpty(rawKey);
			if (StringUtils.isBlank(key)) {
				return;
			}
			if (key.length() > KEY_MAX_LEN) {
				throw parameterError("环境变量 key 最多 " + KEY_MAX_LEN + " 个字符：" + key);
			}
			if (!KEY_PATTERN.matcher(key).matches()) {
				throw parameterError("环境变量 key 需以字母开头，只能包含字母、数字、下划线、短横线：" + key);
			}
			String value = rawValue == null ? "" : rawValue;
			if (value.length() > VALUE_MAX_LEN) {
				throw parameterError("环境变量 value 最多 " + VALUE_MAX_LEN + " 个字符：" + key);
			}
			result.put(key, value);
		});
		return result;
	}

	private AgentBusinessException parameterError(String message) {
		return new AgentBusinessException(ErrorMessageEnum.PARAMETER_ERROR.getErrorCode(), message);
	}
}
