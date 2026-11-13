import * as React from 'react';
import { Inbox } from 'lucide-react';
import { cn } from '@/lib/utils';

export function EmptyState({
  title = '暂无数据',
  description,
  icon,
  action,
  className
}: {
  title?: React.ReactNode;
  description?: React.ReactNode;
  icon?: React.ReactNode;
  action?: React.ReactNode;
  className?: string;
}) {
  return (
    <div
      className={cn(
        'relative flex flex-col items-center justify-center gap-4 px-6 py-20 text-center',
        className
      )}
    >
      <div className="relative">
        {/* 背后的柔光：让空状态不"空" */}
        <span
          aria-hidden
          className="absolute left-1/2 top-1/2 h-16 w-16 -translate-x-1/2 -translate-y-1/2 rounded-full bg-primary/15 blur-2xl"
        />
        <span
          className={cn(
            'relative flex h-12 w-12 items-center justify-center rounded-2xl',
            'bg-gradient-to-b from-muted to-card text-muted-foreground/70',
            'ring-1 ring-inset ring-border',
            'shadow-[0_1px_2px_hsl(240_30%_12%/0.05),inset_0_1px_0_0_hsl(0_0%_100%/0.06)]'
          )}
        >
          {icon ?? <Inbox className="h-5 w-5" />}
        </span>
      </div>
      <div className="flex flex-col gap-1.5">
        <p className="font-display text-[14px] font-semibold tracking-[-0.012em] text-foreground/85">
          {title}
        </p>
        {description ? (
          <p className="max-w-[38ch] text-[12.5px] leading-relaxed text-muted-foreground">
            {description}
          </p>
        ) : null}
      </div>
      {action}
    </div>
  );
}
