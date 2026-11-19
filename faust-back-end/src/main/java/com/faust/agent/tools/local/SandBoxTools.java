package com.faust.agent.tools.local;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.faust.agent.tools.local.annotation.NativeTool;
import com.faust.common.function.ThrowExceptionFunction;
import com.faust.constant.AgentConstants;
import com.faust.enums.ErrorMessageEnum;
import io.agentscope.runtime.sandbox.box.BaseSandbox;
import io.agentscope.runtime.sandbox.manager.SandboxService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;


@Slf4j
@NativeTool(name = "沙箱工具集", code = "sand_box_tools",
		desc = "沙箱中执行代码工具集，可在沙箱内执行代码，用于复杂计算、文本处理、数据加工等任务",
		tags = {"常用工具", "代码执行"}, version = "1.1.0")
public class SandBoxTools {


	private final SandboxService sandboxService;

	private static final String DEFAULT_CHAT_ID = "DefaultChatId-100000000001";

	public SandBoxTools(SandboxService sandboxService) {
		this.sandboxService = sandboxService;
	}


	@Tool(name = "run_python_code",
			description = "在沙箱中执行Python代码并返回执行结果。")
	public String runPythonCode(@ToolParam(description = "要执行的 Python 3 代码，支持多行，注意缩进必须正确。") String code,
								ToolContext toolContext) {
		try {
			RunnableConfig config = (RunnableConfig) toolContext.getContext().get(AgentConstants.AGENT_CONFIG);
			String ucId = getUcId(config);
			String chatId = config.threadId().orElse(DEFAULT_CHAT_ID);
			BaseSandbox sandbox = new BaseSandbox(sandboxService, ucId, chatId);
			return sandbox.runIpythonCell(code);
		} catch (Exception e) {
			return "Run python code error,errorMsg:" + e.getMessage();
		}

	}


	@Tool(name = "run_shell_command",
			description = "在沙箱中执行Shell命令并返回执行结果。")
	public String runShellCommand(@ToolParam(description = "要执行的Shell命令，支持多行，注意缩进必须正确。") String command,
								  ToolContext toolContext) {
		try {
			RunnableConfig config = (RunnableConfig) toolContext.getContext().get(AgentConstants.AGENT_CONFIG);
			String ucId = getUcId(config);
			String chatId = config.threadId().orElse(DEFAULT_CHAT_ID);
			BaseSandbox sandbox = new BaseSandbox(sandboxService, ucId, chatId);
			return sandbox.runShellCommand(command);
		} catch (Exception e) {
			return " Run shell command error,errorMsg:" + e.getMessage();
		}
	}


	private String getUcId(RunnableConfig config) {
		String ucId = config.metadata(AgentConstants.UCID)
				.map(String::valueOf)
				.orElse(null);
		ThrowExceptionFunction.isTrue(StringUtils.isBlank(ucId))
				.throwMessage(ErrorMessageEnum.NO_LOGIN_USER);
		return ucId;
	}

}
