package com.faust.agent.tools.local;

import com.alibaba.fastjson.JSONObject;
import com.faust.agent.tools.local.annotation.NativeTool;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.IOAccess;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 脚本执行工具集在 JVM 进程内执行代码。
 * <p>
 * 设计要点：
 * <ul>
 *     <li>沙箱运行：{@code allowAllAccess(false)} + {@link IOAccess}，脚本可访问网络（HTTP/HTTPS、socket），
 *         但不能读写本地文件、拉起子进程，环境变量不可见；</li>
 *     <li>独立 Context：Polyglot 的 Context 非线程安全，每次调用新建并在结束后关闭，避免并发串扰；</li>
 *     <li>超时控制：由独立看门狗线程在超时后调用 {@link Context#interrupt(Duration)} 强制中断死循环；</li>
 *     <li>结果回传：捕获 stdout/stderr，并支持脚本通过 {@code result} 变量返回结构化结果。</li>
 * </ul>
 */
@Slf4j
@NativeTool(name = "代码执行工具集", code = "code_execute_tools",
	desc = "代码脚本执行工具集，可在沙箱内执行代码，用于复杂计算、文本处理、数据加工等任务",
	tags = {"常用工具", "代码执行"}, version = "1.1.0")
public class CodeExecuteTools {

	/**
	 * Polyglot 语言标识
	 */
	private static final String PYTHON = "python";

	/**
	 * 约定用于回传结果的脚本变量名
	 */
	private static final String RESULT_VAR = "result";
	private static final String SUCCESS_VAR = "success";
	private static final String ERROR_VAR = "error";

	/**
	 * 默认超时时间（秒）
	 */
	private static final int DEFAULT_TIMEOUT_SECONDS = 30;

	/**
	 * 允许的最大超时时间（秒）
	 */
	private static final int MAX_TIMEOUT_SECONDS = 300;

	/**
	 * 单段输出最大字符数，超出截断，避免过长内容撑爆模型上下文
	 */
	private static final int MAX_OUTPUT_CHARS = 20000;

	/**
	 * interrupt 时等待脚本响应取消的时间
	 */
	private static final Duration INTERRUPT_TIMEOUT = Duration.ofSeconds(2);

	/**
	 * 沙箱 IO 策略：放行主机 socket（即网络访问），本地文件访问仍然禁止。
	 * <p>
	 * 注意：polyglot 没有独立的网络开关，网络通路由 {@link IOAccess} 的 socket 权限控制；
	 * 直接写 {@code allowIO(true)} 会把本地文件一起放开，所以这里显式构造 IOAccess。
	 */
	private static final IOAccess NETWORK_ONLY_IO = IOAccess.newBuilder()
		.allowHostFileAccess(false)
		.allowHostSocketAccess(true)
		.build();

	/**
	 * 网络兼容层：补齐 GraalPy 在 JVM 模式下缺失的 {@code encodings.idna} 编解码器。
	 * <p>
	 * ssl 在设置 server_hostname 时需要按 idna 编码域名，而该模块依赖 CPython 扩展
	 * {@code _cpython_unicodedata}，纯 JVM 模式的 GraalPy 并不提供，缺失时所有 HTTPS 请求都会抛
	 * {@code LookupError: unknown encoding idna}。这里用 punycode 兜底实现一个最小 codec；
	 * 若运行环境本身已带原生模块，则直接返回、不做任何替换。
	 */
	private static final String NETWORK_BOOTSTRAP = """
		def __code_execute_network_bootstrap():
		    import sys, types, codecs
		    try:
		        import encodings.idna
		        return
		    except Exception:
		        pass
		    def to_ascii(label):
		        try:
		            label.encode('ascii')
		            return label
		        except UnicodeEncodeError:
		            return 'xn--' + codecs.getencoder('punycode')(label)[0].decode('ascii')
		    def encode(input, errors='strict'):
		        text = input if isinstance(input, str) else bytes(input).decode('ascii')
		        return '.'.join(to_ascii(part) for part in text.split('.')).encode('ascii'), len(input)
		    def decode(input, errors='strict'):
		        return bytes(input).decode('ascii'), len(input)
		    module = types.ModuleType('encodings.idna')
		    # 直接传函数给 CodecInfo：增量编解码器由 codecs 自动派生，
		    # 不写类体是因为 GraalPy 下类体方法取不到外层函数的局部名（NameError）
		    module.getregentry = lambda: codecs.CodecInfo(
		        name='idna', encode=encode, decode=decode)
		    sys.modules['encodings.idna'] = module
		__code_execute_network_bootstrap()
		del __code_execute_network_bootstrap
		""";

	static {
		// 非 GraalVM JDK 只能以解释模式运行 Python，关闭 "InterpreterOnly" 告警噪音
		System.setProperty("polyglot.engine.WarnInterpreterOnly", "false");
	}

	/**
	 * 超时看门狗：必须在与执行线程不同的线程上才能中断卡死的脚本，线程池仅用于调度 interrupt，开销可忽略
	 */
	private static final ScheduledExecutorService TIMEOUT_WATCHDOG = Executors.newSingleThreadScheduledExecutor(runnable -> {
		Thread thread = new Thread(runnable, "code-execute-tool-watchdog");
		thread.setDaemon(true);
		return thread;
	});

	@Tool(name = "run_python3",
		description = "在沙箱中执行 Python 3 代码并返回执行结果。"
			+ "运行环境为 GraalVM GraalPy，支持 Python 3 标准库中的常用模块（如 math、json、re、datetime、collections、itertools、textwrap），"
			+ "不支持 pip 安装的第三方库（如 numpy、pandas、requests），禁止读写本地文件、拉起子进程，环境变量不可见；"
			+ "可以访问网络：标准库的 urllib.request、http.client、socket 均可用，支持 HTTP 与 HTTPS（如需 POST/JSON 可直接用 urllib.request）。"
			+ "print 的内容会作为 stdout 一并返回；如需回传最终结果，请把结果赋值给 result 变量（例如 result = {\"total\": 42}），"
			+ "result 会按 Python 字面量输出，若需要严格的 JSON 文本可先执行 json.dumps 再赋值。"
			+ "适用于复杂数值计算、批量文本处理、字符串解析、数据转换等场景。")
	public String runPython3Code(
		@ToolParam(description = "要执行的 Python 3 代码，支持多行，注意缩进必须正确。需要回传结果时请赋值给 result 变量") String code,
		@ToolParam(description = "执行超时时间（秒），默认 30，最大 300。常规计算无需传入") Integer timeoutSeconds) {
		JSONObject result = new JSONObject();
		long startAt = System.currentTimeMillis();
		if (StringUtils.isBlank(code)) {
			result.put(SUCCESS_VAR, false);
			result.put(ERROR_VAR, "Python 代码不能为空");
			return result.toJSONString();
		}

		int timeout = normalizeTimeout(timeoutSeconds);
		ByteArrayOutputStream stdout = new ByteArrayOutputStream();
		ByteArrayOutputStream stderr = new ByteArrayOutputStream();

		try (Context context = Context.newBuilder(PYTHON)
			.allowAllAccess(false)
			.allowIO(NETWORK_ONLY_IO)
			.out(stdout)
			.err(stderr)
			.build()) {

			initNetwork(context);
			ScheduledFuture<?> watchdog = TIMEOUT_WATCHDOG.schedule(
				() -> interruptQuietly(context, timeout), timeout, TimeUnit.SECONDS);
			try {
				LOGGER.debug("execute code:{}", code);
				Value evalValue = context.eval(PYTHON, code);
				result.put(SUCCESS_VAR, true);
				String text = resolveResult(context, evalValue);
				if (text != null) {
					result.put(RESULT_VAR, text);
				}
			} catch (PolyglotException e) {
				// 脚本本身写错属常见业务情况，warn 只留摘要，完整堆栈走 debug 避免日志噪音
				LOGGER.warn("执行 Python 代码异常, errMsg:{}, code={}", e.getMessage(), abbreviate(code));
				result.put(SUCCESS_VAR, false);
				result.put(ERROR_VAR, buildErrorMessage(e, timeout));
			} finally {
				watchdog.cancel(false);
			}
		} catch (Exception e) {
			LOGGER.error("执行 Python 代码失败, code={}", abbreviate(code), e);
			result.put(SUCCESS_VAR, false);
			result.put(ERROR_VAR, buildFatalMessage(e));
		}

		String stdoutText = limitOutput(stdout);
		if (StringUtils.isNotEmpty(stdoutText)) {
			result.put("stdout", stdoutText);
		}
		String stderrText = limitOutput(stderr);
		if (StringUtils.isNotEmpty(stderrText)) {
			result.put("stderr", stderrText);
		}
		result.put("durationMs", System.currentTimeMillis() - startAt);
		return result.toJSONString();
	}

	/**
	 * 安装网络兼容层（idna codec）。失败只影响 HTTPS，不影响纯计算脚本，因此不向上抛异常
	 */
	private void initNetwork(Context context) {
		try {
			context.eval(PYTHON, NETWORK_BOOTSTRAP);
		} catch (PolyglotException e) {
			LOGGER.debug("网络兼容层安装失败, errMsg:{}", e.getMessage());
		}
	}

	/**
	 * 取脚本执行结果：优先取脚本显式赋值的 result 变量，其次取最后一条表达式的求值结果。
	 * 多行脚本的 eval 返回值是 {@code __main__} 模块对象，没有回传价值，需过滤掉。
	 */
	private String resolveResult(Context context, Value evalValue) {
		Value declared = context.getBindings(PYTHON).getMember(RESULT_VAR);
		if (!Objects.isNull(declared)) {
			return toText(declared);
		}
		if (Objects.isNull(evalValue) || isModuleObject(evalValue)) {
			return null;
		}
		return toText(evalValue);
	}

	/**
	 * 判断 Value 是否为 Python 模块对象（模块的 __name__ 是字符串）
	 */
	private boolean isModuleObject(Value value) {
		try {
			Value name = value.getMember("__name__");
			return !Objects.isNull(name) && name.isString();
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * Value 转文本：字符串原样返回，其余（数字/布尔/列表/字典等）取 Python 字面量表示
	 */
	private String toText(Value value) {
		if (Objects.isNull(value)) {
			return null;
		}
		try {
			if (value.isNull()) {
				return "None";
			}
			return value.isString() ? value.asString() : value.toString();
		} catch (Exception e) {
			LOGGER.warn("Python 返回值转换失败, errMsg:{}", e.getMessage());
			return null;
		}
	}

	/**
	 * 超时中断：由看门狗线程调用，interrupt 会让正在执行的 guest 代码抛出带 isInterrupted 标记的 PolyglotException
	 */
	private void interruptQuietly(Context context, int timeout) {
		try {
			LOGGER.warn("Python 脚本执行超过 {} 秒，触发强制中断", timeout);
			context.interrupt(INTERRUPT_TIMEOUT);
		} catch (Exception e) {
			// Context 可能已正常执行完毕并关闭，此时中断失败属预期情况
			LOGGER.debug("中断 Python 脚本失败, errMsg:{}", e.getMessage());
		}
	}

	private String buildErrorMessage(PolyglotException e, int timeout) {
		if (e.isInterrupted()) {
			return "执行超时：Python 脚本运行超过 " + timeout + " 秒已被强制中断，请检查是否存在死循环或过重的计算";
		}
		if (e.isSyntaxError()) {
			return "Python 语法错误：" + defaultMessage(e);
		}
		if (e.isInternalError()) {
			return "Python 运行时内部错误：" + defaultMessage(e);
		}
		return "Python 执行异常：" + defaultMessage(e);
	}

	/**
	 * 非脚本异常（多为环境问题），单独给出可定位的提示
	 */
	private String buildFatalMessage(Exception e) {
		String message = e.getMessage();
		if (StringUtils.contains(message, "No language")) {
			return "执行环境缺少 Python 运行时（GraalPy），请确认 classpath 中存在 org.graalvm.polyglot:python-community 依赖";
		}
		return "执行 Python 代码失败：" + message;
	}

	private String defaultMessage(PolyglotException e) {
		return StringUtils.defaultIfBlank(e.getMessage(), "未知错误");
	}

	private int normalizeTimeout(Integer timeoutSeconds) {
		if (Objects.isNull(timeoutSeconds) || timeoutSeconds <= 0) {
			return DEFAULT_TIMEOUT_SECONDS;
		}
		return Math.min(timeoutSeconds, MAX_TIMEOUT_SECONDS);
	}

	/**
	 * 输出截断，避免超长日志被整体灌入模型上下文
	 */
	private String limitOutput(ByteArrayOutputStream buffer) {
		String text = buffer.toString(StandardCharsets.UTF_8).trim();
		if (text.length() <= MAX_OUTPUT_CHARS) {
			return text;
		}
		return text.substring(0, MAX_OUTPUT_CHARS) + "\n...(输出过长已截断，原始长度 " + text.length() + " 字符)";
	}

	private String abbreviate(String code) {
		return StringUtils.abbreviate(code, 500);
	}
}
