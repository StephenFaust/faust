import * as React from 'react';
import { useNavigate } from 'react-router-dom';
import { Bot, ChevronRight, Loader2, MessageSquarePlus, Search } from 'lucide-react';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { fetchAgentCards, type AgentCard } from '@/services/agent-center';
import { cn } from '@/lib/utils';

/** 弹窗一次拉取的 Agent 数量：与「智能体」页同源（/api/agent/card/list，仅已发布） */
const FETCH_SIZE = 100;

/** 头像色相：与「智能体」页保持同一套三色系，避免撞色 */
const HUE_SHIFT = [0, 22, -60];

function avatarStyle(seed: string): React.CSSProperties {
  let hash = 0;
  for (let i = 0; i < seed.length; i += 1) hash = (hash * 31 + seed.charCodeAt(i)) % 9973;
  const base = 244 + HUE_SHIFT[hash % HUE_SHIFT.length];
  return {
    backgroundImage: `linear-gradient(142deg, hsl(${base} 92% 70%) 0%, hsl(${base + 16} 78% 50%) 100%)`
  };
}

export function NewChatDialog({
  open,
  onOpenChange
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const navigate = useNavigate();
  const [cards, setCards] = React.useState<AgentCard[]>([]);
  const [total, setTotal] = React.useState(0);
  const [loading, setLoading] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const [query, setQuery] = React.useState('');
  const seqRef = React.useRef(0);

  const load = React.useCallback(() => {
    const seq = seqRef.current + 1;
    seqRef.current = seq;
    setLoading(true);
    setError(null);
    fetchAgentCards({ current: 1, size: FETCH_SIZE })
      .then((result) => {
        if (seq !== seqRef.current) return;
        setCards(result?.records ?? []);
        setTotal(result?.total ?? 0);
      })
      .catch((err) => {
        if (seq !== seqRef.current) return;
        setError(err instanceof Error ? err.message : '加载失败');
      })
      .finally(() => {
        if (seq === seqRef.current) setLoading(false);
      });
  }, []);

  // 每次打开都重新拉一次：发布 / 下架后列表能立刻反映最新状态
  React.useEffect(() => {
    if (!open) return;
    setQuery('');
    load();
  }, [open, load]);

  const keyword = query.trim().toLowerCase();
  const visible = keyword
    ? cards.filter((card) =>
        [card.name, card.agentCode, card.description].some((value) =>
          String(value ?? '').toLowerCase().includes(keyword)
        )
      )
    : cards;

  const choose = (card: AgentCard) => {
    if (!card.agentCode) return;
    onOpenChange(false);
    navigate(`/chat/${card.agentCode}`);
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-[560px] p-0">
        <DialogHeader className="px-6 pb-3 pt-6">
          <DialogTitle className="flex items-center gap-2 font-display tracking-[-0.015em]">
            <span className="flex h-7 w-7 items-center justify-center rounded-lg bg-primary/10 text-primary">
              <MessageSquarePlus className="h-4 w-4" />
            </span>
            新建对话
          </DialogTitle>
          <DialogDescription className="text-[12.5px]">
            选择一个已发布的 Agent，即可开始一段新对话。
          </DialogDescription>
        </DialogHeader>

        {/* 图标必须与输入框同层定位：若把 padding 加在 relative 容器上，
            top-1/2 会按「输入框 + 底部内边距」的高度取中，图标就会偏下 */}
        <div className="px-6 pb-3">
          <div className="relative">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-muted-foreground/50" />
            <Input
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="搜索 Agent 名称 / 描述"
              className="pl-9"
              autoFocus
            />
          </div>
        </div>

        <div className="max-h-[min(56vh,420px)] overflow-y-auto px-3 pb-3">
          {loading ? (
            <div className="flex flex-col gap-1 py-1">
              {Array.from({ length: 4 }).map((_, index) => (
                <div key={index} className="flex items-center gap-3 rounded-xl px-3 py-2.5">
                  <span className="skeleton-sheen h-8 w-8 shrink-0 rounded-[10px]" />
                  <div className="flex flex-1 flex-col gap-1.5">
                    <span className="skeleton-sheen h-3 w-1/3 rounded-full" />
                    <span className="skeleton-sheen h-2.5 w-2/3 rounded-full" />
                  </div>
                </div>
              ))}
            </div>
          ) : error ? (
            <div className="flex flex-col items-center gap-3 px-6 py-10 text-center">
              <p className="text-[12.5px] text-muted-foreground">{error}</p>
              <Button variant="outline" size="sm" onClick={load}>
                重试
              </Button>
            </div>
          ) : visible.length === 0 ? (
            <div className="flex flex-col items-center gap-2 px-6 py-10 text-center">
              <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-muted/60 text-muted-foreground/70">
                <Bot className="h-4 w-4" />
              </span>
              <p className="text-[12.5px] font-medium text-foreground/80">
                {cards.length === 0 ? '还没有已发布的 Agent' : '没有匹配的 Agent'}
              </p>
              <p className="max-w-[34ch] text-[11.5px] leading-relaxed text-muted-foreground">
                {cards.length === 0
                  ? '到「Agent 管理」中发布一个智能体后，这里就能直接开聊。'
                  : '换个关键词再试试。'}
              </p>
            </div>
          ) : (
            <div className="flex flex-col gap-0.5">
              {visible.map((card) => (
                <button
                  key={card.agentCode || card.agentId}
                  type="button"
                  onClick={() => choose(card)}
                  className={cn(
                    'group flex w-full items-center gap-3 rounded-xl px-3 py-2.5 text-left transition-colors duration-200',
                    'hover:bg-muted/60 focus-visible:bg-muted/60 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring/25'
                  )}
                >
                  <span
                    className="flex h-8 w-8 shrink-0 items-center justify-center rounded-[10px] text-white shadow-[0_3px_10px_-4px_hsl(244_80%_50%/0.6),inset_0_1px_0_0_hsl(0_0%_100%/0.25)]"
                    style={avatarStyle(String(card.agentId || card.agentCode || card.name))}
                  >
                    <Bot className="h-4 w-4" />
                  </span>
                  <span className="min-w-0 flex-1">
                    <span className="flex items-center gap-1.5">
                      <span className="truncate text-[13px] font-medium text-foreground">
                        {card.name}
                      </span>
                      {card.version ? (
                        <span className="shrink-0 rounded bg-muted/70 px-1 py-px font-mono text-[10px] text-foreground/55">
                          v{card.version}
                        </span>
                      ) : null}
                    </span>
                    <span className="mt-0.5 block truncate text-[11.5px] text-muted-foreground">
                      {card.description || card.agentCode}
                    </span>
                  </span>
                  <ChevronRight className="h-4 w-4 shrink-0 text-muted-foreground/35 transition-all duration-200 group-hover:translate-x-0.5 group-hover:text-primary" />
                </button>
              ))}
            </div>
          )}
        </div>

        {!loading && !error && total > cards.length ? (
          <p className="border-t border-hairline px-6 py-3 text-[11.5px] text-muted-foreground">
            仅展示前 {cards.length} 个（共 {total} 个），更多请到「智能体」查看。
          </p>
        ) : null}
      </DialogContent>
    </Dialog>
  );
}
