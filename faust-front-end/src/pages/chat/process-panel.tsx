import * as React from 'react';
import { Check, ChevronDown, Wrench } from 'lucide-react';
import { Markdown } from '@/components/markdown';
import { MSG_TYPE, type ProcessSegment } from '@/lib/streamReducer';
import { cn } from '@/lib/utils';

const COLLAPSE_LIMIT = 160;

function formatJson(value?: string) {
  if (!value) return '';
  try {
    return JSON.stringify(JSON.parse(value), null, 2);
  } catch {
    return String(value);
  }
}

function CollapsibleText({ text }: { text: string }) {
  const [open, setOpen] = React.useState(false);
  const isLong = text.length > COLLAPSE_LIMIT;
  const shown = isLong && !open ? `${text.slice(0, COLLAPSE_LIMIT)}…` : text;
  return (
    <div>
      <pre className="max-h-[240px] overflow-auto whitespace-pre-wrap break-words rounded-lg bg-muted/50 px-2.5 py-2 font-mono text-[11.5px] leading-relaxed text-foreground/80 ring-1 ring-inset ring-border/40">
        {shown}
      </pre>
      {isLong ? (
        <button
          type="button"
          onClick={() => setOpen((prev) => !prev)}
          className="mt-1 text-[11px] text-primary hover:underline"
        >
          {open ? '收起' : '展开全部'}
        </button>
      ) : null}
    </div>
  );
}

function ToolTrace({ segment }: { segment: ProcessSegment }) {
  const isRequest = segment.type === MSG_TYPE.TOOL_REQUEST;
  const items = Array.isArray(isRequest ? segment.toolCalls : segment.responses)
    ? (isRequest ? segment.toolCalls ?? [] : segment.responses ?? [])
    : [];

  return (
    <div className="rounded-xl border border-border/60 bg-card/70 px-2.5 py-2">
      <div className="mb-1.5 flex items-center gap-1.5 text-[11px] font-medium text-muted-foreground">
        {isRequest ? (
          <Wrench className="h-3 w-3" />
        ) : (
          <Check className="h-3 w-3 text-emerald-600 dark:text-emerald-400" />
        )}
        {isRequest ? '调用工具' : '工具返回'}
      </div>
      {items.length === 0 ? (
        <p className="text-[11.5px] text-muted-foreground">
          {segment.content || (isRequest ? '（无工具参数）' : '（无返回内容）')}
        </p>
      ) : (
        <div className="flex flex-col gap-1.5">
          {items.map((item, index) => {
            const name = typeof item.name === 'string' ? item.name : '';
            const args = typeof item.arguments === 'string' ? item.arguments : '';
            const responseData = typeof item.responseData === 'string' ? item.responseData : '';
            return (
              <div key={`${item.id || name || 'tool'}-${index}`} className="flex flex-col gap-1">
                <span className="truncate font-mono text-[11.5px] text-foreground/80" title={name}>
                  {name || '—'}
                </span>
                {isRequest && args ? <CollapsibleText text={formatJson(args)} /> : null}
                {!isRequest && responseData ? <CollapsibleText text={responseData} /> : null}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}

/**
 * 思考过程区：流式过程中展示模型正文与工具调用轨迹。
 * 流结束（active=false）后上层清空 process，由最终正文承接，本组件自动收起/消失。
 */
export const ProcessPanel = React.memo(function ProcessPanel({
  process,
  active,
  startedAt
}: {
  process: ProcessSegment[];
  active: boolean;
  startedAt?: number;
}) {
  const [collapsed, setCollapsed] = React.useState(!active);
  const [elapsed, setElapsed] = React.useState(0);
  const bodyRef = React.useRef<HTMLDivElement>(null);
  const stickRef = React.useRef(true);

  React.useEffect(() => {
    setCollapsed(!active);
  }, [active]);

  React.useEffect(() => {
    if (!active || !startedAt) return undefined;
    const raw = Number(startedAt);
    const base = Number.isFinite(raw) && raw > 0 ? (raw < 1e12 ? raw * 1000 : raw) : Date.now();
    const calc = () => Math.max(0, Math.round((Date.now() - base) / 1000));
    setElapsed(calc());
    const timer = setInterval(() => setElapsed(calc()), 1000);
    return () => clearInterval(timer);
  }, [active, startedAt]);

  React.useEffect(() => {
    const el = bodyRef.current;
    if (!el || !active || collapsed || !stickRef.current) return;
    el.scrollTop = el.scrollHeight;
  }, [process, active, collapsed]);

  if (!active && process.length === 0) return null;

  return (
    <div
      className={cn(
        // 思考块最宽占聊天容器的 2/3，避免长文本把消息流撑满
        'mb-2 max-w-[66.6667%] overflow-hidden rounded-2xl border transition-colors duration-300',
        active ? 'border-primary/25 bg-primary/[0.045]' : 'border-border/70 bg-muted/20'
      )}
    >
      <button
        type="button"
        onClick={() => setCollapsed((prev) => !prev)}
        aria-expanded={!collapsed}
        className="flex w-full items-center gap-2 px-3 py-2 text-left"
      >
        <span
          className={cn(
            'h-1.5 w-1.5 shrink-0 rounded-full transition-all duration-300',
            active
              ? 'animate-pulse bg-primary shadow-[0_0_8px_2px_hsl(var(--primary)/0.5)]'
              : 'bg-muted-foreground/45'
          )}
        />
        <span className="text-[12px] font-medium text-foreground/80">{active ? '思考中…' : '思考过程'}</span>
        {elapsed > 0 ? (
          <span className="tabular rounded-full bg-background/60 px-1.5 py-px text-[10.5px] text-muted-foreground ring-1 ring-inset ring-border/50">
            {elapsed}s
          </span>
        ) : null}
        <span className="ml-auto flex items-center gap-1 text-[11px] text-muted-foreground">
          {collapsed ? '展开' : '收起'}
          <ChevronDown className={cn('h-3 w-3 transition-transform', collapsed ? '' : 'rotate-180')} />
        </span>
      </button>

      {collapsed ? null : (
        <div
          ref={bodyRef}
          onScroll={() => {
            const el = bodyRef.current;
            if (!el) return;
            stickRef.current = el.scrollHeight - el.scrollTop - el.clientHeight < 24;
          }}
          className="max-h-[320px] overflow-y-auto border-t border-border/70 px-3 py-2.5"
        >
          <div className="flex flex-col gap-2">
            {process.map((segment) =>
              segment.type === MSG_TYPE.ASSISTANT ? (
                <div key={segment.id} className="text-[13px] text-muted-foreground">
                  <Markdown content={segment.text} streaming={active} />
                </div>
              ) : (
                <ToolTrace key={segment.id} segment={segment} />
              )
            )}
          </div>
        </div>
      )}
    </div>
  );
});
