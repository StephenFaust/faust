import * as React from 'react';
import { ChevronDown } from 'lucide-react';
import { cn } from '@/lib/utils';

/**
 * 折叠面板。默认收起，避免长文本把详情页 / 弹窗撑爆。
 *
 * 用原生 `<details>` 实现，键盘操作与无障碍语义都由浏览器兜底，
 * 不需要额外维护展开状态。
 */
export function Disclosure({
  title,
  meta,
  className,
  children
}: {
  title: React.ReactNode;
  meta?: React.ReactNode;
  className?: string;
  children: React.ReactNode;
}) {
  return (
    <details
      className={cn(
        'group rounded-lg border border-border bg-card [&[open]>summary_.chev]:rotate-180',
        className
      )}
    >
      <summary className="flex cursor-pointer list-none items-center justify-between gap-2 px-3 py-2.5 text-[12.5px] transition-colors hover:bg-muted/40">
        <span className="flex min-w-0 items-center gap-2">{title}</span>
        <span className="flex shrink-0 items-center gap-2">
          {meta}
          <ChevronDown className="chev h-3.5 w-3.5 text-muted-foreground transition-transform" />
        </span>
      </summary>
      <div className="border-t border-border/70 px-3 py-3">{children}</div>
    </details>
  );
}

/** 「展开 / 收起」提示文案，依赖 Disclosure 上的 group-open 切换 */
export function ExpandHint() {
  return (
    <span className="text-[11px] text-muted-foreground">
      <span className="group-open:hidden">展开</span>
      <span className="hidden group-open:inline">收起</span>
    </span>
  );
}
