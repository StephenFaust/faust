import * as React from 'react';
import { cn } from '@/lib/utils';

export interface DescriptionItem {
  label: React.ReactNode;
  value: React.ReactNode;
  /** 跨列宽度，默认 1 列；详情里长字段（URL / 描述）用 span 占满整行 */
  span?: 1 | 2 | 3;
}

/**
 * 详情字段块。
 *
 * 与 bop 版 `Descriptions bordered` 的视觉对齐，但采用「等高 + 等宽 + 省略号 + title 悬浮」的
 * 紧凑呈现：长文本不会把同一行的其他字段挤变形。
 */
export function Descriptions({
  items,
  columns = 2,
  className
}: {
  items: DescriptionItem[];
  columns?: 2 | 3;
  className?: string;
}) {
  return (
    <div
      className={cn(
        'grid overflow-hidden rounded-xl border border-border/70',
        columns === 3 ? 'grid-cols-1 sm:grid-cols-3' : 'grid-cols-1 sm:grid-cols-2',
        className
      )}
    >
      {items.map((item, index) => (
        <div
          key={index}
          className={cn(
            'flex min-w-0 items-stretch border-border/70',
            'border-b sm:border-r',
            item.span === 3 && columns === 3 && 'sm:col-span-3',
            item.span === 2 && 'sm:col-span-2'
          )}
        >
          <div className="flex w-[112px] shrink-0 items-center bg-muted/30 px-3 py-2.5 text-[11.5px] tracking-[0.01em] text-muted-foreground">
            {item.label}
          </div>
          <div className="min-w-0 flex-1 px-3 py-2.5 text-[12.5px] leading-relaxed text-foreground/90">
            {typeof item.value === 'string' || typeof item.value === 'number' ? (
              <span className="block truncate" title={String(item.value)}>
                {item.value === '' ? '—' : item.value}
              </span>
            ) : (
              <div className="min-w-0 break-words">{item.value ?? '—'}</div>
            )}
          </div>
        </div>
      ))}
    </div>
  );
}
