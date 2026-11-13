/**
 * 一轮 assistant 回复的状态机（纯函数，不依赖 React）。
 *
 * 帧协议（/api/agent/chat/start/sse 的 data 帧）：
 *   { chatId }         首帧，回传会话 id
 *   { message, chunk } 业务帧，message.messageType 决定类型
 *     assistant       流式 LLM 内容
 *     tool-request    工具调用请求
 *     tool-response   工具返回结果
 *     final-assistant 本轮最终结果（流式中只缓存，流关闭时合并展示）
 *     tool-confirm    工具确认（前端忽略）
 */

export const PHASE = {
  PROCESSING: 'processing',
  FINAL: 'final',
  ERROR: 'error'
} as const;

export type Phase = (typeof PHASE)[keyof typeof PHASE];

export const MSG_TYPE = {
  ASSISTANT: 'assistant',
  FINAL: 'final-assistant',
  TOOL_REQUEST: 'tool-request',
  TOOL_RESPONSE: 'tool-response',
  TOOL_CONFIRM: 'tool-confirm'
} as const;

export interface ToolCallItem {
  id?: string;
  name?: string;
  arguments?: string;
  [key: string]: unknown;
}

export interface ToolResponseItem {
  id?: string;
  name?: string;
  /** 后端 ToolResponseMessageDTO.responses[].responseData 原样透传 */
  responseData?: string;
  [key: string]: unknown;
}

export interface ProcessSegment {
  id: string;
  type: string;
  text?: string;
  signature?: string;
  content?: string;
  toolCalls?: ToolCallItem[];
  responses?: ToolResponseItem[];
}

export interface AssistantTurn {
  content: string;
  process: ProcessSegment[];
  phase: Phase;
}

export interface StreamMessage {
  messageType?: string;
  content?: string;
  toolCalls?: ToolCallItem[];
  responses?: ToolResponseItem[];
}

export function normalizeMessageType(type?: string): string {
  const t = String(type || '').trim();
  if (t === 'tool') return MSG_TYPE.TOOL_RESPONSE;
  return t;
}

let seq = 0;
function nextSegId() {
  seq += 1;
  return `seg-${seq}`;
}

export function createAssistantTurn(): AssistantTurn {
  return { content: '', process: [], phase: PHASE.PROCESSING };
}

export function appendProcessText(turn: AssistantTurn, delta?: string): AssistantTurn {
  if (!delta) return turn;
  const process = (turn.process || []).slice();
  const last = process[process.length - 1];
  if (last && last.type === MSG_TYPE.ASSISTANT) {
    process[process.length - 1] = { ...last, text: `${last.text || ''}${delta}` };
  } else {
    process.push({ id: nextSegId(), type: MSG_TYPE.ASSISTANT, text: delta });
  }
  return { ...turn, process };
}

export function replaceProcessText(turn: AssistantTurn, text: string): AssistantTurn {
  const process = (turn.process || []).slice();
  const last = process[process.length - 1];
  if (last && last.type === MSG_TYPE.ASSISTANT) {
    process[process.length - 1] = { ...last, text };
  } else {
    process.push({ id: nextSegId(), type: MSG_TYPE.ASSISTANT, text });
  }
  return { ...turn, process };
}

export function appendFinalContent(turn: AssistantTurn, delta?: string): AssistantTurn {
  if (!delta) return turn;
  return { ...turn, content: `${turn.content || ''}${delta}` };
}

function toolSignature(msg: StreamMessage, type: string): string {
  const list = type === MSG_TYPE.TOOL_REQUEST ? msg.toolCalls : msg.responses;
  return (list || []).map((item) => item.id || item.name || '').join('|');
}

export function addToolSegment(turn: AssistantTurn, msg: StreamMessage, type: string): AssistantTurn {
  const signature = toolSignature(msg, type);
  const process = turn.process || [];
  if (signature) {
    const duplicated = process.some((seg) => seg.type === type && seg.signature === signature);
    if (duplicated) return turn;
  }
  const segment: ProcessSegment = {
    id: nextSegId(),
    type,
    signature,
    content: msg.content || '',
    toolCalls: type === MSG_TYPE.TOOL_REQUEST ? msg.toolCalls || [] : [],
    responses: type === MSG_TYPE.TOOL_RESPONSE ? msg.responses || [] : []
  };
  return { ...turn, process: [...process, segment] };
}

export function appendError(turn: AssistantTurn, errText: string): AssistantTurn {
  const processText = (turn.process || [])
    .filter((seg) => seg.type === MSG_TYPE.ASSISTANT)
    .map((seg) => seg.text || '')
    .join('\n\n');
  const base = turn.content || processText;
  return {
    ...turn,
    content: base ? `${base}\n\n${errText}` : errText,
    process: [],
    phase: PHASE.ERROR
  };
}

/**
 * 文本增量判定，兼容两种后端语义：
 *   chunk 是纯增量；message.content 可能是累积快照，也可能是增量。
 */
export function resolveDelta(
  msg: StreamMessage,
  data: { chunk?: string },
  committed: string
): { mode: 'append' | 'replace'; text: string } {
  const chunk = typeof data.chunk === 'string' ? data.chunk : '';
  if (chunk) return { mode: 'append', text: chunk };

  const content = typeof msg.content === 'string' ? msg.content : '';
  const base = committed || '';
  if (!content || content === base) return { mode: 'append', text: '' };
  if (!base) return { mode: 'append', text: content };
  if (content.indexOf(base) === 0) return { mode: 'append', text: content.slice(base.length) };
  if (content.length < base.length) return { mode: 'append', text: content };
  return { mode: 'replace', text: content };
}
