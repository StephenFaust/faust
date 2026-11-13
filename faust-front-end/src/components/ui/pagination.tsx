import * as React from 'react';
import { ChevronLeft, ChevronRight } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';

export interface PaginationProps {
  current: number;
  pageSize: number;
  total: number;
  onChange: (page: number) => void;
  className?: string;
}

export function Pagination({ current, pageSize, total, onChange, className }: PaginationProps) {
  const pageCount = Math.max(1, Math.ceil(total / pageSize));
  if (total <= 0) return null;

  const start = (current - 1) * pageSize + 1;
  const end = Math.min(current * pageSize, total);

  const pages: (number | '...')[] = [];
  for (let page = 1; page <= pageCount; page += 1) {
    if (page === 1 || page === pageCount || Math.abs(page - current) <= 1) {
      pages.push(page);
    } else if (pages[pages.length - 1] !== '...') {
      pages.push('...');
    }
  }

  return (
    <div className={cn('flex flex-wrap items-center justify-between gap-4 pt-4', className)}>
      <span className="text-[12px] tracking-tight text-muted-foreground">
        第 <span className="tabular font-medium text-foreground/75">{start}-{end}</span> 条 · 共{' '}
        <span className="tabular font-medium text-foreground/75">{total}</span> 条
      </span>
      <div className="flex items-center gap-1">
        <Button
          variant="outline"
          size="icon"
          className="h-7 w-7 rounded-lg"
          disabled={current <= 1}
          onClick={() => onChange(current - 1)}
          aria-label="上一页"
        >
          <ChevronLeft className="h-3.5 w-3.5" />
        </Button>
        {pages.map((page, index) =>
          page === '...' ? (
            <span key={`gap-${index}`} className="px-1 text-[11px] text-muted-foreground/60">
              ···
            </span>
          ) : (
            <button
              key={page}
              type="button"
              onClick={() => onChange(page)}
              className={cn(
                'tabular h-7 min-w-7 rounded-lg px-2 text-[12.5px] font-medium transition-all duration-200 ease-expo',
                page === current
                  ? 'bg-primary text-primary-foreground shadow-[0_4px_12px_-4px_hsl(var(--primary)/0.7)]'
                  : 'text-muted-foreground hover:bg-muted hover:text-foreground'
              )}
            >
              {page}
            </button>
          )
        )}
        <Button
          variant="outline"
          size="icon"
          className="h-7 w-7 rounded-lg"
          disabled={current >= pageCount}
          onClick={() => onChange(current + 1)}
          aria-label="下一页"
        >
          <ChevronRight className="h-3.5 w-3.5" />
        </Button>
      </div>
    </div>
  );
}
