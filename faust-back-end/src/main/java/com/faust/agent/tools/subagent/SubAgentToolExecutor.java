package com.faust.agent.tools.subagent;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.serializer.AgentInstructionMessage;
import com.alibaba.fastjson.JSON;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.faust.constant.AgentConstants;
import com.faust.dto.agent.ToolMateInfo;
import com.faust.agent.tools.AbstractToolExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.util.json.JsonParser;
import org.springframework.util.StringUtils;

import java.util.*;

@Slf4j
public class SubAgentToolExecutor extends AbstractToolExecutor {

	/**
	 * 模型填写任务描述的字段名
	 */
	private static final String ARG_TASK = "task";

	private final ReactAgent reactAgent;


	public SubAgentToolExecutor(ReactAgent reactAgent, ToolMateInfo toolMateInfo) {
		super(toolMateInfo);
		this.reactAgent = reactAgent;
	}


	/**
	 * 子 Agent 工具只吃一段任务描述：子 Agent 是独立会话，看不到父 Agent 的历史消息，
	 * 因此在参数说明里强调「自包含」，避免模型只丢一个「继续」「按上面说的做」。
	 */
	@Override
	public String getInputSchema() {
		return textParamSchema(ARG_TASK, "交给子 Agent 的任务内容或问题，必须自包含：子 Agent 看不到当前会话的历史消息，"
				+ "请在文本中写清背景、已知信息、约束条件与期望的输出形式；需要多轮澄清时也可在原样文本中说明。");
	}


	@Override
	public String execute(Map<String, Object> args, ToolContext toolContext) {
		String input = textArg(args, ARG_TASK);
		if (!StringUtils.hasText(input)) {
			return missingParamResult(ARG_TASK);
		}
		String actualInput = extractInputValue(input);
		RunnableConfig config = (RunnableConfig) toolContext.getContext().get(AgentConstants.AGENT_CONFIG);
		List<Message> messagesToAdd = new ArrayList<>();
		if (StringUtils.hasLength(reactAgent.instruction())) {
			messagesToAdd.add(AgentInstructionMessage.builder().text(reactAgent.instruction()).build());
		}
		messagesToAdd.add(new UserMessage(actualInput));
		RunnableConfig subAgentConfig = RunnableConfig.builder()
				.addMetadata(AgentConstants.UCID, config.metadata(AgentConstants.UCID).orElse(null))
				.addMetadata(AgentConstants.ENV_VAR, config.metadata(AgentConstants.ENV_VAR).orElse(null))
				.build();
		LOGGER.info("Execute agent tool,input:{},toolCode:{}", actualInput, toolMateInfo.getToolCode());
		Optional<OverAllState> resultState = reactAgent.getAndCompileGraph()
				.invoke(Map.of("messages", messagesToAdd), subAgentConfig);
		if (resultState.isPresent()) {
			List<Message> messageList = getMessages(resultState.get());
			LOGGER.info("Execute agent tool,result:{}", JSON.toJSONString(messageList));
			return messageList.get(messageList.size() - 1).getText();
		}
		return "Failed to execute agent tool or failed to get agent tool result";
	}


	@SuppressWarnings("unchecked")
	private List<Message> getMessages(OverAllState state) {
		return (List<Message>) state.data().getOrDefault("messages", List.of());
	}

	private String extractInputValue(String input) {
		if (!StringUtils.hasText(input)) {
			return input;
		}
		try {
			ObjectMapper objectMapper = JsonParser.getObjectMapper();
			Map<String, Object> jsonMap = objectMapper.readValue(input, new TypeReference<HashMap<String, Object>>() {
			});
			if (jsonMap != null && jsonMap.containsKey("input")) {
				Object inputValue = jsonMap.get("input");
				if (inputValue != null) {
					if (inputValue instanceof String value) {
						return value;
					}

					return JsonParser.getObjectMapper().writeValueAsString(inputValue);
				}
			}
		} catch (Exception e) {
			// Not a JSON object or parsing failed, use input as-is
		}
		return input;
	}
}
