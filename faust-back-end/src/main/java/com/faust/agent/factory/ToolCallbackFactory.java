package com.faust.agent.factory;

import cn.hutool.extra.spring.SpringUtil;
import com.alibaba.cloud.ai.graph.agent.MessageToolCallResultConverter;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.faust.dto.agent.SubAgentInfo;
import com.faust.dto.agent.ToolMateInfo;
import com.faust.enums.ToolTypeEnum;
import com.faust.agent.tools.AbstractToolExecutor;
import com.faust.agent.tools.ToolExecutorCallback;
import com.faust.agent.tools.db.DbToolExecutor;
import com.faust.agent.tools.http.HttpToolExecutor;
import com.faust.agent.tools.subagent.SubAgentToolExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.execution.ToolCallResultConverter;
import org.springframework.stereotype.Component;

import java.net.http.HttpClient;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
@Slf4j
@RequiredArgsConstructor
public class ToolCallbackFactory {


	private final HttpClient httpClient;

	private final DataSourceFactory dataSourceFactory;


	public ToolCallback getToolCallback(ToolMateInfo toolMateInfo) {
		ToolTypeEnum toolType = ToolTypeEnum.getByName(toolMateInfo.getType());
		switch (toolType) {
			case DB -> {
				return getDbToolCallBack(toolMateInfo);
			}
			case HTTP -> {
				return getHttpToolCallback(toolMateInfo);
			}
			case SUB_AGENT -> {
				return getSubAgentToolCallback(toolMateInfo);
			}
			default -> throw new IllegalArgumentException("未知的工具类型");
		}
	}


	private ToolCallback getHttpToolCallback(ToolMateInfo mateInfo) {
		HttpToolExecutor httpToolExecutor = new HttpToolExecutor(httpClient, mateInfo);
		return buildCallback(mateInfo, httpToolExecutor, null);
	}


	private ToolCallback getSubAgentToolCallback(ToolMateInfo mateInfo) {
		SubAgentInfo subAgentInfo = mateInfo.getSubAgentInfo();
		if (Objects.isNull(subAgentInfo) || StringUtils.isBlank(subAgentInfo.getAgentCode())
				|| StringUtils.isBlank(subAgentInfo.getVersion())) {
			throw new IllegalArgumentException("Sub agent tool info empty,toolCode:" + mateInfo.getToolCode());
		}
		AgentFactory agentFactory = SpringUtil.getBean(AgentFactory.class);
		ReactAgent subAgent = agentFactory.getSubAgent(subAgentInfo.getAgentCode(), subAgentInfo.getVersion());
		SubAgentToolExecutor subAgentToolExecutor = new SubAgentToolExecutor(subAgent, mateInfo);
		return buildCallback(mateInfo, subAgentToolExecutor, new MessageToolCallResultConverter());
	}


	private ToolCallback getDbToolCallBack(ToolMateInfo mateInfo) {
		DbToolExecutor dbToolExecutor = new DbToolExecutor(dataSourceFactory, mateInfo);
		return buildCallback(mateInfo, dbToolExecutor, null);
	}


	/**
	 * 组装工具回调。
	 * <p>
	 * 用 {@link ToolExecutorCallback} 而不是 Spring AI 的 {@code MethodToolCallback}：
	 * 后者按 Java 形参名从模型入参里取值，会逼着 Schema 多包一层与形参同名的字段；
	 * 这里直接把模型输出的 JSON 解析成参数表交给执行器，Schema 得以保持扁平。
	 * </p>
	 */
	private ToolCallback buildCallback(ToolMateInfo mateInfo, AbstractToolExecutor executor,
									   ToolCallResultConverter resultConverter) {
		ToolDefinition toolDefinition = ToolDefinition.builder()
				.name(mateInfo.getToolCode())
				.description(mateInfo.getDescription())
				.inputSchema(executor.getInputSchema())
				.build();
		return new ToolExecutorCallback(toolDefinition, executor, resultConverter);
	}

}
