/**
 * SSE（Server-Sent Events）解析器 + 对话流封装。
 *
 * 后端帧协议（/api/agent/chat/start/sse）：
 *   1. 首帧：data:{"chatId":"<id>"}
 *   2. 普通帧：data:<AgentRunResp JSON> —— { node, agent, tokenUsage, message, chunk }
 *   3. 空帧：data:{} —— 忽略
 *   4. 错误帧：event:error  data:{"error":true,"errorType":"...","errorMessage":"..."}
 *
 * 只负责解析事件流，请求统一走 services/agent-center 的 startChatSSE。
 */
import { startChatSSE as startChatSSERequest } from '@/services/agent-center';

interface ParseErrorMarker {
  __parseError: true;
}

const PARSE_ERROR: ParseErrorMarker = { __parseError: true };

function isParseError(value: SSEEvent | ParseErrorMarker | null): value is ParseErrorMarker {
  return value !== null && (value as ParseErrorMarker).__parseError === true;
}

const MAX_CONSECUTIVE_PARSE_ERRORS = 10;

/** 半开连接检测：超过该时长未收到任何字节即判定连接死亡 */
const INACTIVITY_TIMEOUT_MS = 120000;

export interface SSEEvent {
  event: string;
  data: Record<string, unknown> | null;
  id: string | null;
}

interface SSECallbacks {
  onChatId?: (chatId: string) => void;
  onEvent?: (data: Record<string, unknown>) => void;
  onError?: (error: Error) => void;
  signal?: AbortSignal;
}

function makeParseFailedError(sample: string) {
  const err = new Error('对话数据解析失败，已中止。请重试；若反复出现请联系后端排查。') as Error & {
    sseParseFailed?: boolean;
    sample?: string;
  };
  err.sseParseFailed = true;
  err.sample = sample;
  return err;
}

function parseOneEvent(rawEvent: string): SSEEvent | typeof PARSE_ERROR | null {
  let eventName = 'message';
  let id: string | null = null;
  const dataLines: string[] = [];

  for (const raw of rawEvent.split('\n')) {
    const line = raw.endsWith('\r') ? raw.slice(0, -1) : raw;
    if (line === '') continue;
    if (line[0] === ':') continue;

    const colon = line.indexOf(':');
    let field: string;
    let value: string;
    if (colon === -1) {
      field = line;
      value = '';
    } else {
      field = line.slice(0, colon);
      value = line.slice(colon + 1);
      if (value[0] === ' ') value = value.slice(1);
    }

    if (field === 'data') dataLines.push(value);
    else if (field === 'event') eventName = value;
    else if (field === 'id') id = value;
  }

  if (dataLines.length === 0) return null;

  const dataStr = dataLines.join('\n');
  const trimmed = dataStr.trim();
  let data: Record<string, unknown> | null = null;

  if (trimmed !== '') {
    // 后端偶发以纯文本下发诊断/心跳，这类帧按可忽略处理，不计入解析失败
    if (trimmed[0] !== '{' && trimmed[0] !== '[') return null;
    try {
      data = JSON.parse(dataStr);
    } catch (error) {
      console.warn('[sse] 单条事件 JSON 解析失败:', dataStr, error);
      return PARSE_ERROR;
    }
  }

  return { event: eventName, data, id };
}

export async function* parseSSEStream(
  body: ReadableStream<Uint8Array>,
  options: { signal?: AbortSignal; timeoutMs?: number } = {}
): AsyncGenerator<SSEEvent> {
  const { signal, timeoutMs } = options;
  if (!body || typeof body.getReader !== 'function') {
    console.warn('[sse] 无效的 body，无法解析 SSE 流');
    return;
  }

  const reader = body.getReader();
  const decoder = new TextDecoder('utf-8');
  let buffer = '';
  let leftoverLine = '';
  let parseFailures = 0;

  let timedOut = false;
  let inactivityTimer: ReturnType<typeof setTimeout> | null = null;

  const armInactivity = () => {
    if (!timeoutMs || timeoutMs <= 0) return;
    if (inactivityTimer) clearTimeout(inactivityTimer);
    inactivityTimer = setTimeout(() => {
      timedOut = true;
      try {
        reader.cancel();
      } catch {
        /* ignore */
      }
    }, timeoutMs);
  };

  const trackFailure = (sample: string) => {
    parseFailures += 1;
    return parseFailures >= MAX_CONSECUTIVE_PARSE_ERRORS ? makeParseFailedError(sample) : null;
  };

  try {
    armInactivity();
    // eslint-disable-next-line no-constant-condition
    while (true) {
      if (signal?.aborted) break;

      const { value, done } = await reader.read();
      if (done) break;

      buffer += decoder.decode(value, { stream: true });
      if (value && value.length) armInactivity();

      let nl = buffer.indexOf('\n');
      while (nl !== -1) {
        let line = buffer.slice(0, nl);
        buffer = buffer.slice(nl + 1);
        if (line.endsWith('\r')) line = line.slice(0, -1);

        if (line === '') {
          const raw = leftoverLine;
          leftoverLine = '';
          const evt = parseOneEvent(raw);
          if (isParseError(evt)) {
            const fatal = trackFailure(raw);
            if (fatal) throw fatal;
          } else if (evt) {
            parseFailures = 0;
            yield evt;
          }
        } else if (line[0] === ':') {
          // 注释行忽略
        } else {
          leftoverLine = leftoverLine ? `${leftoverLine}\n${line}` : line;
        }
        nl = buffer.indexOf('\n');
      }
    }

    if (leftoverLine !== '') {
      const raw = leftoverLine;
      const evt = parseOneEvent(raw);
      if (isParseError(evt)) {
        const fatal = trackFailure(raw);
        if (fatal) throw fatal;
      } else if (evt) {
        yield evt;
      }
    }

    if (timedOut) {
      const error = new Error('连接超时，对话可能已断开，请重试') as Error & { sseTimeout?: boolean };
      error.sseTimeout = true;
      throw error;
    }
  } catch (error) {
    const err = error as Error & { sseParseFailed?: boolean; sseTimeout?: boolean; sseNetworkError?: boolean };
    if (err?.sseParseFailed) throw err;
    if (err?.sseTimeout) throw err;
    if (timedOut) {
      const timeoutErr = new Error('连接超时，对话可能已断开，请重试') as Error & { sseTimeout?: boolean };
      timeoutErr.sseTimeout = true;
      throw timeoutErr;
    }
    if (err?.name === 'AbortError') return;
    const networkErr = new Error('对话连接中断，请重试') as Error & {
      sseNetworkError?: boolean;
      cause?: unknown;
    };
    networkErr.sseNetworkError = true;
    networkErr.cause = error;
    throw networkErr;
  } finally {
    if (inactivityTimer) clearTimeout(inactivityTimer);
    try {
      await reader.cancel();
    } catch {
      /* 流可能已关闭 */
    }
  }
}

/**
 * 发起 SSE 流式对话。
 *
 * @param body { agentCode, version, userMsg, chatId, fileInfoList, envVar }
 */
export async function startChatSSE(body: Record<string, unknown>, handlers: SSECallbacks = {}) {
  const { onChatId, onEvent, onError, signal } = handlers;

  const response = await startChatSSERequest(body, signal);
  if (!response || !response.ok || !response.body) {
    const error = new Error(`SSE 请求失败: ${response.status} ${response.statusText}`);
    onError?.(error);
    throw error;
  }

  try {
    for await (const evt of parseSSEStream(response.body, { signal, timeoutMs: INACTIVITY_TIMEOUT_MS })) {
      if (evt.event === 'error') {
        const errObj = (evt.data || {}) as { errorMessage?: string; errorType?: string };
        const error = new Error(errObj.errorMessage || '对话过程中发生错误') as Error & {
          errorType?: string;
        };
        error.errorType = errObj.errorType;
        onError?.(error);
        break;
      }

      if (evt.data && typeof evt.data.chatId === 'string' && evt.data.chatId) {
        onChatId?.(evt.data.chatId);
        continue;
      }

      if (onEvent && evt.data) onEvent(evt.data);
    }
  } catch (error) {
    const err = error as Error & { sseParseFailed?: boolean; sseTimeout?: boolean; sseNetworkError?: boolean };
    if (err?.sseParseFailed || err?.sseTimeout || err?.sseNetworkError) {
      onError?.(err);
      return;
    }
    throw err;
  }
}
