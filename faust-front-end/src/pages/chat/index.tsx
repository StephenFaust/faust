import * as React from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  ArrowLeft,
  Bot,
  FileText,
  Loader2,
  Menu,
  MessageSquare,
  Plus,
  Send,
  Square,
  User
} from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Markdown } from '@/components/markdown';
import { cn } from '@/lib/utils';
import {
  fetchAgentBase,
  fetchChatEnvVar,
  fetchChatHistory,
  fetchChatList,
  fetchGlobalEnvVar,
  type ChatHistoryMessage,
  type ChatSession
} from '@/services/agent-center';
import {
  PHASE,
  MSG_TYPE,
  appendError,
  appendFinalContent,
  appendProcessText,
  addToolSegment,
  createAssistantTurn,
  normalizeMessageType,
  replaceProcessText,
  resolveDelta,
  type AssistantTurn,
  type Phase,
  type ProcessSegment
} from '@/lib/streamReducer';
import { startChatSSE } from '@/lib/sse';
import { useTypewriter } from '@/lib/hooks/use-typewriter';
import { toast } from 'sonner';
import { ChatInputTools, type UploadedFile } from './chat-input';
import { envListToMap, envMapToList, type EnvRow } from '@/lib/env';
import { ProcessPanel } from './process-panel';

// ---------------- 类型 ----------------
interface ChatFile {
  fileName?: string;
  fileType?: string;
  url?: string;
}

interface ChatMessage {
  /** 历史消息的服务端 id；本地乐观消息没有（翻页游标依赖它） */
  id?: number;
  role: 'user' | 'assistant';
  content: string;
  process: ProcessSegment[];
  phase: Phase;
  createdAt: number;
  files?: ChatFile[];
  startedAt?: number;
}

/** 状态机返回的是 AssistantTurn，合并回带渲染元信息的 ChatMessage */
function mergeTurn(message: ChatMessage, turn: AssistantTurn): ChatMessage {
  return { ...message, content: turn.content, process: turn.process, phase: turn.phase };
}

interface Session {
  id: string;
  chatId: string;
  agentCode: string;
  title: string;
  updatedAt: number;
  messages: ChatMessage[];
  /**
   * 本会话的环境变量，跟随会话走而不是页面走：
   * `undefined` 表示还没初始化（新会话等默认变量预填、历史会话等接口回显），
   * 一旦有值就不再被重新拉取覆盖，避免切换会话丢掉用户没发送的修改。
   */
  envVars?: EnvRow[];
  /** 本地新建、尚未出现在服务端列表里的会话 */
  local?: boolean;
}

const EMPTY_ENV: EnvRow[] = [];

const HISTORY_PAGE_SIZE = 20;
const LIST_PAGE_SIZE = 20;
const ANCHOR_SETTLE_FRAMES = 12;
/** 贴底后继续压几帧，等 Markdown / 图片把高度撑开 */
const BOTTOM_SETTLE_FRAMES = 24;

/**
 * 消息流与输入框共用的内容列宽。
 * 铺满对话主区，不再做居中限宽；若日后想恢复阅读宽度，把这里改回
 * `mx-auto w-full max-w-[1080px]` 即可（消息列与输入框同步生效）。
 */
const CHAT_COLUMN = 'w-full';

function normalizeRole(role?: string): 'user' | 'assistant' {
  const value = String(role || '').trim().toUpperCase();
  if (value === 'USER' || value === 'HUMAN') return 'user';
  return 'assistant';
}

function mapHistoryItem(item: ChatHistoryMessage): ChatMessage | null {
  if (!item || typeof item !== 'object') return null;
  const raw = item.message;
  const content = raw === null || raw === undefined ? '' : typeof raw === 'string' ? raw : String(raw);
  return {
    id: item.id,
    role: normalizeRole(item.role),
    content,
    process: [],
    phase: PHASE.FINAL,
    createdAt: Date.now(),
    files: Array.isArray(item.fileInfoRespList) ? item.fileInfoRespList : []
  };
}

/** 已加载内容里最旧一条服务端消息的 id，作为翻页游标 */
function oldestMessageId(messages: ChatMessage[]): number | null {
  for (const message of messages) {
    if (message.id !== null && message.id !== undefined) return message.id;
  }
  return null;
}

function contentOffset(container: HTMLElement, node: HTMLElement) {
  return node.getBoundingClientRect().top - container.getBoundingClientRect().top + container.scrollTop;
}

interface ScrollAnchor {
  node: HTMLElement;
  top: number;
  applied: number;
}

function captureScrollAnchor(container: HTMLElement | null): ScrollAnchor | null {
  if (!container) return null;
  const children = container.children;
  for (let i = 0; i < children.length; i += 1) {
    const node = children[i] as HTMLElement;
    if (node.getBoundingClientRect().height <= 0) continue;
    return { node, top: contentOffset(container, node), applied: 0 };
  }
  return null;
}

function applyScrollAnchor(container: HTMLElement | null, anchor: ScrollAnchor | null) {
  if (!container || !anchor || !anchor.node || !anchor.node.isConnected) return false;
  const added = contentOffset(container, anchor.node) - anchor.top;
  const increase = added - anchor.applied;
  if (Math.abs(increase) < 0.5) return false;
  container.scrollTop += increase;
  anchor.applied = added;
  return true;
}

function newChatId() {
  const random = Math.random().toString(36).slice(2, 10);
  return `chat-${Date.now()}-${random}`;
}

/** 清掉还没发过任何消息的本地草稿，避免连点「新对话」堆出一排空会话 */
function dropEmptyDrafts(list: Session[]): Session[] {
  return list.filter((session) => !(session.local && session.messages.length === 0));
}

// ---------------- 历史消息里的文件 ----------------
const IMAGE_RE = /\.(jpe?g|png|gif|bmp|webp|svg)$/i;

function HistoryFileCard({ file }: { file: ChatFile }) {
  const [failed, setFailed] = React.useState(false);
  const isImage = IMAGE_RE.test(file.fileName || '');
  const url = /^https?:\/\//i.test(String(file.url || '')) ? String(file.url) : '';
  const ext =
    file.fileType && file.fileType.trim()
      ? file.fileType.toUpperCase()
      : (file.fileName || '').split('.').pop()?.toUpperCase() || '';

  const body = (
    <>
      <span className="flex h-9 w-9 shrink-0 items-center justify-center overflow-hidden rounded-md bg-muted text-muted-foreground">
        {isImage && url && !failed ? (
          <img src={url} alt={file.fileName} onError={() => setFailed(true)} className="h-full w-full object-cover" />
        ) : (
          <FileText className="h-4 w-4" />
        )}
      </span>
      <span className="flex min-w-0 flex-col">
        <span className="max-w-[200px] truncate text-[11.5px] text-foreground/85" title={file.fileName}>
          {file.fileName || '未命名文件'}
        </span>
        {ext ? <span className="text-[10px] uppercase text-muted-foreground">{ext}</span> : null}
      </span>
    </>
  );

  const className =
    'inline-flex items-center gap-2 rounded-lg border border-border bg-card px-2 py-1.5 transition-colors hover:border-primary/30';

  if (!url) return <div className={className}>{body}</div>;
  return (
    <a className={className} href={url} target="_blank" rel="noopener noreferrer" title={file.fileName}>
      {body}
    </a>
  );
}

// ---------------- 页面 ----------------
export default function ChatPage() {
  const { agentCode = '' } = useParams();
  const navigate = useNavigate();

  const [agent, setAgent] = React.useState<{ agentCode: string; name: string; version: string } | null>(null);
  const [sessions, setSessions] = React.useState<Session[]>([]);
  const [currentSessionId, setCurrentSessionId] = React.useState<string | null>(null);
  const [listState, setListState] = React.useState({ current: 0, hasMore: true, loading: false });
  const [historyLoading, setHistoryLoading] = React.useState(false);
  const [historyState, setHistoryState] = React.useState<{ chatId: string | null; loadingMore: boolean }>({
    chatId: null,
    loadingMore: false
  });

  const [input, setInput] = React.useState('');
  const [files, setFiles] = React.useState<UploadedFile[]>([]);
  const [isStreaming, setIsStreaming] = React.useState(false);
  const [isFinalizing, setIsFinalizing] = React.useState(false);
  const [sidebarOpen, setSidebarOpen] = React.useState(false);

  const isBusy = isStreaming || isFinalizing;
  const currentSession = sessions.find((session) => session.id === currentSessionId) ?? null;
  const messages = currentSession?.messages ?? [];
  const envVars = currentSession?.envVars ?? EMPTY_ENV;

  const listRef = React.useRef<HTMLDivElement>(null);
  const endRef = React.useRef<HTMLDivElement>(null);
  const inputRef = React.useRef<HTMLTextAreaElement>(null);
  const abortRef = React.useRef<AbortController | null>(null);

  const stickBottomRef = React.useRef(true);
  const scrollRafRef = React.useRef(0);
  const anchorRef = React.useRef<ScrollAnchor | null>(null);
  const anchorRafRef = React.useRef(0);
  const topArmedRef = React.useRef(false);
  const loadingMoreRef = React.useRef(false);
  const historyChatIdRef = React.useRef<string | null>(null);
  const activeSessionRef = React.useRef<string | null>(null);
  const committedRef = React.useRef({ process: '', final: '' });
  const finalPartsRef = React.useRef<string[]>([]);
  const userStoppedRef = React.useRef(false);
  const errorRef = React.useRef(false);
  const isFinalizingRef = React.useRef(false);
  const agentRef = React.useRef(agent);
  agentRef.current = agent;
  const sessionsRef = React.useRef(sessions);
  sessionsRef.current = sessions;
  const currentIdRef = React.useRef(currentSessionId);
  currentIdRef.current = currentSessionId;

  const setFinalizing = (value: boolean) => {
    isFinalizingRef.current = value;
    setIsFinalizing(value);
  };

  // ---------------- 消息写入 ----------------
  const appendMessage = React.useCallback((sessionId: string, message: ChatMessage) => {
    setSessions((prev) =>
      prev.map((session) =>
        session.id === sessionId ? { ...session, messages: [...session.messages, message] } : session
      )
    );
  }, []);

  const patchLastAssistant = React.useCallback((updater: (message: ChatMessage) => ChatMessage) => {
    const sessionId = activeSessionRef.current;
    if (!sessionId) return;
    setSessions((prev) =>
      prev.map((session) => {
        if (session.id !== sessionId) return session;
        const list = session.messages.slice();
        const lastIndex = list.length - 1;
        if (lastIndex < 0 || list[lastIndex].role !== 'assistant') return session;
        list[lastIndex] = updater(list[lastIndex]);
        return { ...session, messages: list };
      })
    );
  }, []);

  const commitText = React.useCallback(
    (delta: string, target: 'process' | 'final') => {
      committedRef.current[target] += delta;
      if (target === 'final') {
        patchLastAssistant((message) => mergeTurn(message, appendFinalContent(message, delta)));
      } else {
        patchLastAssistant((message) => mergeTurn(message, appendProcessText(message, delta)));
      }
    },
    [patchLastAssistant]
  );

  const commitReplace = React.useCallback(
    (text: string, target: 'process' | 'final') => {
      committedRef.current[target] = text;
      if (target === 'final') {
        patchLastAssistant((message) => ({ ...message, content: text }));
      } else {
        patchLastAssistant((message) => mergeTurn(message, replaceProcessText(message, text)));
      }
    },
    [patchLastAssistant]
  );

  const typewriter = useTypewriter(commitText, () => {
    setFinalizing(false);
    setIsStreaming(false);
  });

  // ---------------- 列表 / 历史 ----------------
  /** 环境变量按会话落库到 Session 上，切换会话各自独立，互不覆盖 */
  const setSessionEnvVars = React.useCallback((sessionId: string, rows: EnvRow[]) => {
    setSessions((prev) =>
      prev.map((session) => (session.id === sessionId ? { ...session, envVars: rows } : session))
    );
  }, []);

  /**
   * 给「还没有服务端记录」的新会话预填该 Agent 在当前用户下的默认环境变量。
   * 这些变量只是会话初始值，用户可以改，发送时落进会话自己的 env_var。
   */
  const prefillGlobalEnv = React.useCallback(
    (sessionId: string) => {
      if (!agentCode) return;
      fetchGlobalEnvVar(agentCode)
        .then((map) => setSessionEnvVars(sessionId, envMapToList(map)))
        .catch(() => undefined);
    },
    [agentCode, setSessionEnvVars]
  );

  const loadChatList = React.useCallback(
    async (reset: boolean) => {
      if (!agentCode) return;
      if (!reset && !listState.hasMore) return;
      setListState((prev) => ({ ...prev, loading: true }));
      try {
        const page = await fetchChatList({
          agentCode,
          current: reset ? 1 : listState.current + 1,
          pageSize: LIST_PAGE_SIZE
        });
        const records: ChatSession[] = page?.records ?? [];
        setSessions((prev) => {
          // 保留本地草稿会话：进入页面时会先落一个「新会话」，不能被列表刷新抹掉
          const base = reset ? prev.filter((session) => session.local) : prev;
          const known = new Set(base.map((session) => session.id));
          const incoming = records
            .filter((record) => record.chatId && !known.has(record.chatId))
            .map((record) => ({
              id: record.chatId,
              chatId: record.chatId,
              agentCode,
              title: record.title || '新会话',
              updatedAt: record.chatTime ? new Date(record.chatTime).getTime() : Date.now(),
              messages: [] as ChatMessage[]
            }));
          // 倒序：最近一次对话在最上方
          return [...base, ...incoming].sort((a, b) => b.updatedAt - a.updatedAt);
        });
        const loaded = (reset ? 0 : listState.current) + 1;
        setListState({
          current: loaded,
          hasMore: records.length > 0 && loaded * LIST_PAGE_SIZE < (page?.total ?? 0),
          loading: false
        });
      } catch (error) {
        setListState((prev) => ({ ...prev, loading: false }));
        toast.error(`加载会话列表失败：${error instanceof Error ? error.message : '未知错误'}`);
      }
    },
    [agentCode, listState.current, listState.hasMore]
  );

  const loadHistory = React.useCallback(
    async (chatId: string) => {
      if (!chatId || !agentCode) return;
      historyChatIdRef.current = chatId;
      topArmedRef.current = false;
      anchorRef.current = null;
      setHistoryLoading(true);
      // 已有环境变量就不再回拉，避免覆盖用户在本会话里还没发送的修改
      const cachedEnv = sessionsRef.current.find((session) => session.id === chatId)?.envVars;
      const needEnv = cachedEnv === undefined;
      try {
        const [history, envMap] = await Promise.all([
          fetchChatHistory({ chatId, agentCode, pageSize: HISTORY_PAGE_SIZE }),
          needEnv
            ? fetchChatEnvVar({ chatId }).catch(() => null)
            : Promise.resolve(null)
        ]);
        if (historyChatIdRef.current !== chatId) return;
        const pageMessages = (history?.chatMessageList ?? []).map(mapHistoryItem).filter(Boolean) as ChatMessage[];
        setSessions((prev) =>
          prev.map((session) =>
            session.id === chatId
              ? {
                  ...session,
                  messages: pageMessages,
                  local: false,
                  envVars: envMap ? envMapToList(envMap as Record<string, string>) : session.envVars
                }
              : session
          )
        );
        setHistoryState({ chatId, loadingMore: false });
      } catch (error) {
        if (historyChatIdRef.current !== chatId) return;
        toast.error(`加载历史消息失败：${error instanceof Error ? error.message : '未知错误'}`);
        setSessions((prev) =>
          prev.map((session) => (session.id === chatId ? { ...session, messages: [] } : session))
        );
      } finally {
        if (historyChatIdRef.current === chatId) setHistoryLoading(false);
      }
    },
    [agentCode]
  );

  const loadMoreHistory = React.useCallback(() => {
    const sessionId = currentIdRef.current;
    const session = sessionsRef.current.find((item) => item.id === sessionId);
    if (!session || isBusy) return;
    if (historyState.chatId !== sessionId) return;
    if (loadingMoreRef.current) return;
    const cursor = oldestMessageId(session.messages);
    if (cursor === null) return;

    loadingMoreRef.current = true;
    anchorRef.current = captureScrollAnchor(listRef.current);
    setHistoryState((prev) => ({ ...prev, loadingMore: true }));

    fetchChatHistory({
      chatId: session.chatId,
      agentCode,
      lastMessageId: cursor,
      pageSize: HISTORY_PAGE_SIZE
    })
      .then((history) => {
        const older = (history?.chatMessageList ?? []).map(mapHistoryItem).filter(Boolean) as ChatMessage[];
        if (!older.length) return;
        setSessions((prev) =>
          prev.map((item) => (item.id === sessionId ? { ...item, messages: [...older, ...item.messages] } : item))
        );
      })
      .catch((error) => {
        anchorRef.current = null;
        toast.error(`加载更早消息失败：${error instanceof Error ? error.message : '未知错误'}`);
      })
      .finally(() => {
        loadingMoreRef.current = false;
        setHistoryState((prev) => ({ ...prev, loadingMore: false }));
      });
  }, [agentCode, historyState.chatId, isBusy]);

  // ---------------- 初始化 ----------------
  React.useEffect(() => {
    let cancelled = false;
    // 从外部进入一律开一段新对话，不再自动续上最近一次历史会话
    const draftId = newChatId();
    setAgent(null);
    setSessions([
      { id: draftId, chatId: draftId, agentCode, title: '新会话', updatedAt: Date.now(), messages: [], local: true }
    ]);
    setCurrentSessionId(draftId);
    setFiles([]);
    // 新会话默认带入「该 Agent × 当前用户」的默认环境变量（后端落库时同样有兜底带入）
    prefillGlobalEnv(draftId);
    setHistoryState({ chatId: null, loadingMore: false });
    setListState({ current: 0, hasMore: true, loading: false });

    fetchAgentBase(agentCode)
      .then((base) => {
        if (cancelled) return;
        setAgent({ agentCode: base.agentCode || agentCode, name: base.name || agentCode, version: base.version || '' });
      })
      .catch((error) => {
        if (cancelled) return;
        setAgent({ agentCode, name: agentCode, version: '' });
        toast.error(`加载 Agent 失败：${error instanceof Error ? error.message : '未知错误'}`);
      })
      .finally(() => {
        if (!cancelled) loadChatList(true);
      });

    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [agentCode]);

  // 首个会话就绪后加载历史
  const bootstrappedRef = React.useRef<string | null>(null);
  React.useEffect(() => {
    if (!currentSessionId) return;
    if (bootstrappedRef.current === currentSessionId) return;
    const session = sessions.find((item) => item.id === currentSessionId);
    if (!session) return;
    // 本地新建的会话（含进入页面时落的新对话草稿）消息就在本地，标记已引导，
    // 避免它落库后又被历史接口回拉覆盖正在打字的回复
    bootstrappedRef.current = currentSessionId;
    if (session.local) return;
    loadHistory(session.chatId);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [currentSessionId, sessions]);

  // 卸载 / 切换 Agent 中止流
  React.useEffect(
    () => () => {
      abortRef.current?.abort();
      if (scrollRafRef.current) cancelAnimationFrame(scrollRafRef.current);
      if (anchorRafRef.current) cancelAnimationFrame(anchorRafRef.current);
    },
    []
  );

  // ---------------- 滚动 ----------------
  React.useLayoutEffect(() => {
    const anchor = anchorRef.current;
    if (!anchor) return;
    if (applyScrollAnchor(listRef.current, anchor)) {
      if (anchorRafRef.current) cancelAnimationFrame(anchorRafRef.current);
      let left = ANCHOR_SETTLE_FRAMES;
      const step = () => {
        anchorRafRef.current = 0;
        if (left <= 0) return;
        left -= 1;
        applyScrollAnchor(listRef.current, anchor);
        anchorRafRef.current = requestAnimationFrame(step);
      };
      anchorRafRef.current = requestAnimationFrame(step);
    }
    if (!historyState.loadingMore) anchorRef.current = null;
  }, [sessions, historyState.loadingMore]);

  /**
   * 切会话 / 历史加载完后直接把消息区拉到底部：
   * 打开一段历史会话时，用户要看的是最近几条消息，而不是停在三年前的第一句。
   * 用 scrollTop 而不是 scrollIntoView —— 后者会连带滚动外层容器且带平滑动画，
   * 高度还在变化时容易被中断，最终停在顶部。
   */
  const pinnedSessionRef = React.useRef<string | null>(null);
  React.useLayoutEffect(() => {
    // 换会话一定重新贴底；同一会话内只有用户没往上翻（stickBottom）时才贴着
    if (pinnedSessionRef.current !== currentSessionId) {
      pinnedSessionRef.current = currentSessionId;
      stickBottomRef.current = true;
    }
    if (!stickBottomRef.current) return undefined;
    const el = listRef.current;
    if (!el) return undefined;

    // 多跑几帧：Markdown / 图片渲染完，容器高度才稳定下来
    let left = BOTTOM_SETTLE_FRAMES;
    let raf = 0;
    const step = () => {
      const node = listRef.current;
      if (!node) return;
      node.scrollTop = node.scrollHeight;
      left -= 1;
      if (left > 0) {
        raf = requestAnimationFrame(step);
        scrollRafRef.current = raf;
      } else {
        scrollRafRef.current = 0;
      }
    };
    raf = requestAnimationFrame(step);
    scrollRafRef.current = raf;

    // 字体 / 图片晚一步撑开高度时再补一次
    const timer = window.setTimeout(() => {
      const node = listRef.current;
      if (node && stickBottomRef.current) node.scrollTop = node.scrollHeight;
    }, 260);

    return () => {
      cancelAnimationFrame(raf);
      window.clearTimeout(timer);
    };
  }, [sessions, currentSessionId]);

  const handleScroll = () => {
    const el = listRef.current;
    if (!el) return;
    stickBottomRef.current = el.scrollHeight - el.scrollTop - el.clientHeight < 40;
    if (el.scrollHeight - el.clientHeight <= 80) return;
    if (el.scrollTop < 80) {
      if (topArmedRef.current) {
        loadMoreHistory();
        topArmedRef.current = false;
      }
      return;
    }
    topArmedRef.current = true;
  };

  // ---------------- SSE ----------------
  const feedText = (message: { content?: string }, data: { chunk?: string }) => {
    const committed = committedRef.current.process || '';
    const delta = resolveDelta(message as never, data, committed);
    if (!delta.text) return;
    if (delta.mode === 'replace') {
      typewriter.clear();
      commitReplace(delta.text, 'process');
      return;
    }
    typewriter.push(delta.text, 'process');
  };

  const handleEvent = (data: Record<string, unknown>) => {
    const message = (data.message ?? {}) as {
      messageType?: string;
      content?: string;
      toolCalls?: unknown[];
      responses?: unknown[];
    };
    if (!message.messageType) {
      if (typeof data.chunk === 'string' && data.chunk) feedText(message, data as { chunk?: string });
      return;
    }

    const type = normalizeMessageType(message.messageType);
    if (type === MSG_TYPE.TOOL_CONFIRM) return;

    if (type === MSG_TYPE.FINAL) {
      const segment =
        (typeof message.content === 'string' && message.content) ||
        (typeof data.chunk === 'string' ? data.chunk : '');
      if (!segment) return;
      const parts = finalPartsRef.current;
      const last = parts[parts.length - 1] || '';
      if (last && segment.startsWith(last)) parts[parts.length - 1] = segment;
      else parts.push(segment);
      return;
    }

    if (type === MSG_TYPE.ASSISTANT) {
      feedText(message, data as { chunk?: string });
      return;
    }

    if (type === MSG_TYPE.TOOL_REQUEST || type === MSG_TYPE.TOOL_RESPONSE) {
      typewriter.enqueue(() => {
        patchLastAssistant((item) => mergeTurn(item, addToolSegment(item, message as never, type)));
      });
    }
  };

  const handleError = (error: Error & { sseTimeout?: boolean }) => {
    typewriter.flush();
    errorRef.current = true;
    const isTimeout = Boolean(error?.sseTimeout);
    const text = isTimeout ? '回复超时，请重试' : `**错误：${error.message || '请求失败'}**`;
    patchLastAssistant((message) => mergeTurn(message, appendError(message, text)));
    toast.error(isTimeout ? '回复超时，请重试' : error?.message || '对话发生错误');
  };

  const finalizeSession = (sessionId: string) => {
    setSessions((prev) => {
      const target = prev.find((session) => session.id === sessionId);
      if (!target) return prev;
      const updated: Session = { ...target, local: false, updatedAt: Date.now() };
      // 首轮对话后标题取用户首句
      if (!updated.title || updated.title === '新会话') {
        const firstUser = updated.messages.find((message) => message.role === 'user');
        if (firstUser?.content) updated.title = firstUser.content.slice(0, 50);
      }
      const rest = prev.filter((session) => session.id !== sessionId);
      return [updated, ...rest].sort((a, b) => b.updatedAt - a.updatedAt);
    });
  };

  const send = () => {
    const text = input.trim();
    if (!text) return;
    if (files.some((file) => file.uploading)) {
      toast.info('文件上传中，请稍候');
      return;
    }
    if (!agentRef.current) {
      toast.error('Agent 信息未就绪');
      return;
    }

    const readyFiles = files.filter((file) => !file.uploading && file.url);
    const fileInfoList = readyFiles.map((file) => ({
      url: file.url,
      fileName: file.fileName,
      fileType: file.fileType
    }));

    const existing = sessionsRef.current.find((session) => session.id === currentIdRef.current);
    // 复用当前会话：服务端会话直接用；本地「新对话」只有还没发过消息时才复用（否则会残留空会话）
    const canReuse = Boolean(existing) && (!existing?.local || existing.messages.length === 0);
    const sessionId = canReuse && existing ? existing.id : newChatId();
    activeSessionRef.current = sessionId;

    if (!canReuse) {
      const draft: Session = {
        id: sessionId,
        chatId: sessionId,
        agentCode,
        title: '新会话',
        updatedAt: Date.now(),
        messages: [],
        local: true
      };
      setSessions((prev) => [draft, ...dropEmptyDrafts(prev)]);
      setCurrentSessionId(sessionId);
    } else if (existing) {
      const touched = { ...existing, updatedAt: Date.now() };
      setSessions((prev) => [touched, ...prev.filter((session) => session.id !== sessionId)]);
    }

    appendMessage(sessionId, {
      role: 'user',
      content: text,
      process: [],
      phase: PHASE.FINAL,
      createdAt: Date.now(),
      files: fileInfoList
    });

    setIsStreaming(true);
    committedRef.current = { process: '', final: '' };
    finalPartsRef.current = [];
    errorRef.current = false;
    stickBottomRef.current = true;

    appendMessage(sessionId, {
      role: 'assistant',
      createdAt: Date.now(),
      startedAt: Date.now(),
      ...createAssistantTurn()
    });

    setInput('');
    setFiles([]);

    abortRef.current?.abort();
    typewriter.clear();
    const controller = new AbortController();
    abortRef.current = controller;

    const finish = () => {
      if (userStoppedRef.current) {
        userStoppedRef.current = false;
        typewriter.clear();
        // 主动停止：移除本轮未完成的回复气泡
        setSessions((prev) =>
          prev.map((session) => {
            if (session.id !== sessionId) return session;
            const list = session.messages.slice();
            if (list.length && list[list.length - 1].role === 'assistant') list.pop();
            return { ...session, messages: list };
          })
        );
        setIsStreaming(false);
        finalizeSession(sessionId);
        abortRef.current = null;
        return;
      }

      if (errorRef.current) {
        errorRef.current = false;
        setIsStreaming(false);
        finalizeSession(sessionId);
        abortRef.current = null;
        return;
      }

      typewriter.clear();
      let merged = finalPartsRef.current.join('\n\n');
      finalPartsRef.current = [];
      if (!merged) {
        const session = sessionsRef.current.find((item) => item.id === sessionId);
        const last = session?.messages[session.messages.length - 1];
        if (last?.process) {
          merged = last.process
            .filter((segment: ProcessSegment) => segment.type === MSG_TYPE.ASSISTANT)
            .map((segment) => segment.text || '')
            .join('\n\n');
        }
      }
      if (!merged) merged = '（本次未返回内容）';

      patchLastAssistant((message) => ({ ...message, process: [], phase: PHASE.FINAL, content: '' }));
      committedRef.current.final = '';
      typewriter.push(merged, 'final', true);
      setFinalizing(true);
      finalizeSession(sessionId);
      typewriter.finish();
      abortRef.current = null;
    };

    const envMap = envListToMap(envVars);

    startChatSSE(
      {
        agentCode: agentRef.current.agentCode,
        version: agentRef.current.version,
        userMsg: text,
        chatId: sessionId,
        fileInfoList: fileInfoList.length ? fileInfoList : undefined,
        // 始终显式传：空对象表示用户主动清空（新会话不再继承默认变量）
        envVar: envMap
      },
      {
        signal: controller.signal,
        onEvent: handleEvent,
        onError: handleError
      }
    )
      .then(finish)
      .catch((error) => {
        if (!error || error.name !== 'AbortError') {
          toast.error(error?.message || '对话流异常');
        }
        finish();
      });
  };

  const stopStream = () => {
    if (isFinalizingRef.current) {
      typewriter.flush();
      return;
    }
    if (!abortRef.current) return;
    userStoppedRef.current = true;
    abortRef.current.abort();
  };

  const selectSession = (session: Session) => {
    if (isBusy) {
      toast.info('正在回复中，请稍候再切换会话');
      return;
    }
    setCurrentSessionId(session.id);
    setSidebarOpen(false);
    bootstrappedRef.current = session.id;
    // 附件与当前会话绑定，切走时清掉，避免带到另一段对话里
    setFiles([]);
    // 本地草稿没有服务端记录，别去拉历史（会把环境变量清成空）；
    // 首次进入时预填该 Agent 的默认变量，之后切回来沿用会话里已改过的值
    if (session.local) {
      if (session.envVars === undefined) prefillGlobalEnv(session.id);
      return;
    }
    loadHistory(session.chatId);
  };

  const startNewChat = () => {
    if (isBusy) {
      toast.info('正在回复中，请稍候再新建会话');
      return;
    }
    const id = newChatId();
    setSessions((prev) => [
      { id, chatId: id, agentCode, title: '新会话', updatedAt: Date.now(), messages: [], local: true },
      ...dropEmptyDrafts(prev)
    ]);
    setCurrentSessionId(id);
    setFiles([]);
    // 新会话带入该 Agent 的默认环境变量
    prefillGlobalEnv(id);
    setSidebarOpen(false);
    inputRef.current?.focus();
  };

  const messageNodes = messages.map((message, index) => {
    const key = message.id !== undefined ? `msg-${message.id}` : `idx-${index}`;
    const fileBlock =
      message.files && message.files.length ? (
        <div className={cn('mb-1.5 flex flex-wrap gap-1.5', message.role === 'user' && 'justify-end')}>
          {message.files.map((file, fileIndex) => (
            <HistoryFileCard key={`${key}-file-${fileIndex}`} file={file} />
          ))}
        </div>
      ) : null;

    if (message.role === 'user') {
      return (
        <React.Fragment key={key}>
          {fileBlock}
          <div className="flex items-start justify-end gap-2.5">
            <div className="max-w-[min(78%,760px)] whitespace-pre-wrap break-words rounded-2xl rounded-tr-[6px] bg-[linear-gradient(135deg,hsl(var(--primary))_0%,hsl(258_86%_58%)_100%)] px-4 py-2.5 text-[13.5px] leading-relaxed text-primary-foreground shadow-[0_4px_16px_-6px_hsl(var(--primary)/0.6),inset_0_1px_0_0_hsl(0_0%_100%/0.18)]">
              {message.content}
            </div>
            <span className="mt-0.5 flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-muted text-muted-foreground">
              <User className="h-3.5 w-3.5" />
            </span>
          </div>
        </React.Fragment>
      );
    }

    const hasProcess = Boolean(message.process && message.process.length);
    const hasContent = Boolean(message.content && message.content.trim());
    const waiting = message.phase === PHASE.PROCESSING && !hasProcess && !hasContent;

    return (
      <React.Fragment key={key}>
        {fileBlock}
        <div className="flex items-start gap-2.5">
          <span className="mt-0.5 flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-[linear-gradient(142deg,hsl(244_92%_70%)_0%,hsl(258_78%_52%)_100%)] text-white shadow-[0_3px_10px_-3px_hsl(var(--primary)/0.6),inset_0_1px_0_0_hsl(0_0%_100%/0.25)]">
            <Bot className="h-3.5 w-3.5" />
          </span>
          <div className="min-w-0 flex-1">
            {hasProcess ? (
              <ProcessPanel
                process={message.process ?? []}
                active={message.phase === PHASE.PROCESSING}
                startedAt={message.startedAt}
              />
            ) : null}
            {waiting ? (
              <span className="inline-flex items-center gap-2 text-[13px] text-muted-foreground">
                <Loader2 className="h-3.5 w-3.5 animate-spin" />
                正在思考中…
              </span>
            ) : null}
            {hasContent ? (
              <div className="w-fit max-w-[66.6667%] break-words rounded-2xl rounded-tl-[6px] border border-border/70 bg-card px-4 py-2.5 shadow-panel">
                <Markdown content={message.content} streaming={message.phase === PHASE.PROCESSING} />
              </div>
            ) : null}
          </div>
        </div>
      </React.Fragment>
    );
  });

  return (
    <div className="relative flex h-full min-h-0 w-full">
      {/* 会话列表 */}
      <aside
        className={cn(
          'flex w-[236px] shrink-0 flex-col border-r border-hairline bg-gradient-to-b from-card/70 to-card/40',
          'max-md:absolute max-md:inset-y-0 max-md:left-0 max-md:z-30 max-md:w-[260px] max-md:shadow-float',
          sidebarOpen ? 'max-md:flex' : 'max-md:hidden'
        )}
      >
        <div className="flex items-center justify-between gap-2 border-b border-border/70 px-3 py-2.5">
          <span className="flex items-center gap-1.5 text-[12.5px] font-semibold text-foreground">
            <MessageSquare className="h-3.5 w-3.5 text-muted-foreground" />
            历史会话
          </span>
          <Button size="xs" onClick={startNewChat} disabled={isBusy}>
            <Plus className="h-3.5 w-3.5" />
            新对话
          </Button>
        </div>

        <div className="flex-1 overflow-y-auto px-2 py-2">
          {sessions.length === 0 ? (
            <p className="px-2 py-6 text-center text-[12px] text-muted-foreground">暂无历史会话</p>
          ) : (
            <div className="flex flex-col gap-1">
              {sessions.map((session) => (
                <button
                  key={session.id}
                  type="button"
                  disabled={isBusy && session.id !== currentSessionId}
                  onClick={() => selectSession(session)}
                  className={cn(
                    'group relative flex flex-col gap-0.5 rounded-[10px] px-2.5 py-2 text-left transition-all duration-200 ease-expo',
                    session.id === currentSessionId
                      ? 'bg-primary/10 text-foreground ring-1 ring-inset ring-primary/20'
                      : 'text-foreground/70 hover:bg-muted/60 hover:text-foreground',
                    isBusy && session.id !== currentSessionId && 'cursor-not-allowed opacity-55'
                  )}
                >
                  <span className="truncate text-[12.5px] font-medium" title={session.title}>
                    {session.title || '新会话'}
                  </span>
                  <span className="text-[10.5px] text-muted-foreground">
                    {new Date(session.updatedAt).toLocaleString()}
                  </span>
                </button>
              ))}
            </div>
          )}

          {listState.loading ? (
            <p className="flex items-center justify-center gap-1.5 py-2 text-[11.5px] text-muted-foreground">
              <Loader2 className="h-3 w-3 animate-spin" />
              加载中
            </p>
          ) : listState.hasMore ? (
            <button
              type="button"
              onClick={() => loadChatList(false)}
              className="mt-1 w-full rounded-md py-1.5 text-[11.5px] text-primary hover:bg-primary/5"
            >
              加载更多
            </button>
          ) : null}
        </div>
      </aside>

      {/* 对话主区 */}
      <div className="flex min-w-0 flex-1 flex-col">
        <header className="flex items-center justify-between gap-3 border-b border-hairline bg-card/70 px-4 py-2.5 backdrop-blur-xl">
          <div className="flex min-w-0 items-center gap-2.5">
            <Button
              variant="ghost"
              size="icon"
              className="h-8 w-8 md:hidden"
              onClick={() => setSidebarOpen((prev) => !prev)}
            >
              <Menu className="h-4 w-4" />
            </Button>
            <span className="flex h-8 w-8 items-center justify-center rounded-[10px] bg-[linear-gradient(142deg,hsl(244_92%_70%)_0%,hsl(258_78%_52%)_100%)] text-white shadow-[0_4px_14px_-4px_hsl(var(--primary)/0.55),inset_0_1px_0_0_hsl(0_0%_100%/0.25)]">
              <Bot className="h-4 w-4" />
            </span>
            <div className="flex min-w-0 flex-col leading-tight">
              <span className="truncate font-display text-[13.5px] font-semibold tracking-[-0.015em] text-foreground">
                {agent?.name || agentCode}
              </span>
              <span className="flex items-center gap-1.5 text-[10.5px] text-muted-foreground">
                <span className="relative flex h-1.5 w-1.5">
                  <span className="absolute inline-flex h-full w-full animate-pulse-ring rounded-full bg-emerald-500" />
                  <span className="relative inline-flex h-1.5 w-1.5 rounded-full bg-emerald-500" />
                </span>
                在线
                {agent?.version ? ` · v${agent.version}` : ''}
              </span>
            </div>
          </div>
          <Button variant="outline" size="sm" onClick={() => navigate('/')}>
            <ArrowLeft className="h-3.5 w-3.5" />
            返回智能体
          </Button>
        </header>

        <div className="relative flex min-h-0 flex-1 flex-col">
          <div ref={listRef} onScroll={handleScroll} className="flex-1 overflow-y-auto px-5 py-5">
            <div className={cn(CHAT_COLUMN, 'flex flex-col gap-4')}>
              {historyLoading ? (
                <div className="flex flex-col items-center justify-center gap-3 py-24 text-muted-foreground">
                  <Loader2 className="h-5 w-5 animate-spin" />
                  <span className="text-[12.5px]">正在加载历史消息…</span>
                </div>
              ) : messages.length === 0 ? (
                <div className="flex flex-col items-center justify-center gap-4 py-24 text-center">
                  <span className="relative">
                    <span
                      aria-hidden
                      className="absolute left-1/2 top-1/2 h-16 w-16 -translate-x-1/2 -translate-y-1/2 rounded-full bg-primary/15 blur-2xl"
                    />
                    <span className="relative flex h-12 w-12 items-center justify-center rounded-2xl bg-gradient-to-b from-card to-muted/60 text-muted-foreground ring-1 ring-inset ring-border shadow-panel">
                      <MessageSquare className="h-5 w-5" />
                    </span>
                  </span>
                  <p className="font-display text-[15px] font-semibold tracking-[-0.015em] text-foreground/85">
                    开始与 {agent?.name || agentCode} 对话吧
                  </p>
                  <p className="max-w-sm text-[12px] leading-relaxed text-muted-foreground">
                    支持上传图片、PDF、Excel，也可以为本次会话配置环境变量。
                  </p>
                </div>
              ) : (
                messageNodes
              )}
              <div ref={endRef} />
            </div>
          </div>

          {historyState.loadingMore ? (
            <div className="pointer-events-none absolute left-1/2 top-2 flex -translate-x-1/2 items-center gap-1.5 rounded-full border border-border bg-card px-2.5 py-1 text-[11px] text-muted-foreground shadow-sm">
              <Loader2 className="h-3 w-3 animate-spin" />
              加载更早的消息…
            </div>
          ) : null}
        </div>

        <div className="border-t border-hairline bg-card/70 px-4 py-3 backdrop-blur-xl">
          <div className={CHAT_COLUMN}>
            <ChatInputTools
              files={files}
              onFilesChange={setFiles}
              envVars={envVars}
              onEnvVarsChange={(rows) => {
                if (currentSessionId) setSessionEnvVars(currentSessionId, rows);
              }}
              disabled={isBusy}
            />

            <div className="mt-1.5 flex items-end gap-2 rounded-2xl border border-input bg-card/70 px-2.5 py-2 shadow-panel transition-colors duration-200 ease-expo focus-within:transition-none focus-within:border-primary/70">
              <textarea
                ref={inputRef}
                rows={1}
                value={input}
                disabled={isBusy}
                onChange={(event) => setInput(event.target.value)}
                onKeyDown={(event) => {
                  if (event.key === 'Enter' && !event.shiftKey) {
                    event.preventDefault();
                    send();
                  }
                }}
                onInput={(event) => {
                  const el = event.currentTarget;
                  el.style.height = 'auto';
                  el.style.height = `${Math.min(el.scrollHeight, 180)}px`;
                }}
                placeholder={isBusy ? '正在回复中，请稍候…' : '输入消息，Enter 发送，Shift + Enter 换行'}
                className="max-h-[180px] min-h-[28px] flex-1 resize-none bg-transparent px-1 py-1 text-[13.5px] leading-relaxed outline-none placeholder:text-muted-foreground/70 disabled:opacity-60"
              />
              {isBusy ? (
                <Button
                  size="icon"
                  onClick={stopStream}
                  title="停止生成"
                  className="h-8 w-8 shrink-0 bg-rose-500 hover:bg-rose-600"
                >
                  <Square className="h-3.5 w-3.5" />
                </Button>
              ) : (
                <Button size="icon" className="h-8 w-8 shrink-0" onClick={send} disabled={!input.trim()} title="发送">
                  <Send className="h-3.5 w-3.5" />
                </Button>
              )}
            </div>
            <p className="mt-1.5 text-center text-[10.5px] text-muted-foreground">
              对话由 AI 生成，请注意甄别
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}
