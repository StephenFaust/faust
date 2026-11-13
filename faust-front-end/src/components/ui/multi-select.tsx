import * as React from 'react';
import { Check, ChevronDown, Search, X } from 'lucide-react';
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover';
import { Input } from '@/components/ui/input';
import { cn } from '@/lib/utils';

export interface MultiSelectOption {
  label: string;
  value: string;
  disabled?: boolean;
}

export interface MultiSelectProps {
  value: string[];
  onChange: (value: string[]) => void;
  options: MultiSelectOption[];
  placeholder?: string;
  searchPlaceholder?: string;
  emptyText?: string;
  disabled?: boolean;
  className?: string;
  /** 已选项最多展示几个 chip，超出折叠为 +N */
  maxVisibleChips?: number;
}

/**
 * 多选下拉：Popover + 勾选列表。
 *
 * 替代 antd `Select mode="multiple"`。相对裸露的 checkbox 列表，
 * 这里额外做了三件事：已选项以 chip 常驻展示、支持搜索过滤、支持一键清空。
 */
export function MultiSelect({
  value,
  onChange,
  options,
  placeholder = '请选择',
  searchPlaceholder = '搜索…',
  emptyText = '暂无可选项',
  disabled,
  className,
  maxVisibleChips = 6
}: MultiSelectProps) {
  const [open, setOpen] = React.useState(false);
  const [keyword, setKeyword] = React.useState('');

  const selected = Array.isArray(value) ? value : [];
  const selectedSet = new Set(selected);

  const filtered = keyword.trim()
    ? options.filter((option) => option.label.toLowerCase().includes(keyword.trim().toLowerCase()))
    : options;

  const toggle = (next: string) => {
    onChange(selectedSet.has(next) ? selected.filter((item) => item !== next) : [...selected, next]);
  };

  const visible = selected.slice(0, maxVisibleChips);
  const restCount = selected.length - visible.length;
  const labelOf = (val: string) => options.find((option) => option.value === val)?.label ?? val;

  return (
    <Popover open={open} onOpenChange={(next) => {
      setOpen(next);
      if (!next) setKeyword('');
    }}>
      <PopoverTrigger asChild>
        <button
          type="button"
          disabled={disabled}
          className={cn(
            'flex min-h-[36px] w-full items-center justify-between gap-2 rounded-lg border border-input bg-card px-2.5 py-1.5 text-left text-sm shadow-sm transition-colors',
            'focus:outline-none focus:ring-2 focus:ring-ring/30 focus:border-primary',
            'disabled:cursor-not-allowed disabled:opacity-60',
            className
          )}
        >
          <span className="flex min-w-0 flex-1 flex-wrap items-center gap-1">
            {selected.length === 0 ? (
              <span className="px-1 text-muted-foreground/70">{placeholder}</span>
            ) : (
              <>
                {visible.map((item) => (
                  <span
                    key={item}
                    className="inline-flex max-w-[160px] items-center gap-1 rounded-md bg-primary/10 px-1.5 py-0.5 text-[11.5px] text-primary"
                  >
                    <span className="truncate" title={labelOf(item)}>
                      {labelOf(item)}
                    </span>
                  </span>
                ))}
                {restCount > 0 ? (
                  <span className="rounded-md bg-muted px-1.5 py-0.5 text-[11.5px] text-muted-foreground">
                    +{restCount}
                  </span>
                ) : null}
              </>
            )}
          </span>
          <span className="flex shrink-0 items-center gap-1">
            {selected.length > 0 ? (
              <span
                role="button"
                tabIndex={-1}
                aria-label="清空"
                onClick={(event) => {
                  event.stopPropagation();
                  onChange([]);
                }}
                className="rounded p-0.5 text-muted-foreground/60 transition hover:bg-muted hover:text-foreground"
              >
                <X className="h-3.5 w-3.5" />
              </span>
            ) : null}
            <ChevronDown className="h-4 w-4 text-muted-foreground/70" />
          </span>
        </button>
      </PopoverTrigger>

      <PopoverContent align="start" className="w-[var(--radix-popover-trigger-width)] min-w-[220px] p-0">
        <div className="border-b border-border/70 p-2">
          <div className="relative">
            <Search className="pointer-events-none absolute left-2.5 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-muted-foreground/60" />
            <Input
              value={keyword}
              onChange={(event) => setKeyword(event.target.value)}
              placeholder={searchPlaceholder}
              className="h-8 pl-8 text-[13px]"
            />
          </div>
        </div>
        <div className="max-h-64 overflow-y-auto p-1">
          {filtered.length === 0 ? (
            <p className="px-2 py-6 text-center text-[12.5px] text-muted-foreground">{emptyText}</p>
          ) : (
            filtered.map((option) => {
              const active = selectedSet.has(option.value);
              return (
                <button
                  key={option.value}
                  type="button"
                  disabled={option.disabled}
                  onClick={() => toggle(option.value)}
                  className={cn(
                    'flex w-full items-center gap-2 rounded-md px-2 py-1.5 text-left text-[13px] transition-colors',
                    'hover:bg-muted disabled:cursor-not-allowed disabled:opacity-45',
                    active && 'bg-primary/5 text-primary'
                  )}
                >
                  <span
                    className={cn(
                      'flex h-3.5 w-3.5 shrink-0 items-center justify-center rounded-[3px] border',
                      active ? 'border-primary bg-primary text-primary-foreground' : 'border-input bg-card'
                    )}
                  >
                    {active ? <Check className="h-2.5 w-2.5" /> : null}
                  </span>
                  <span className="truncate">{option.label}</span>
                </button>
              );
            })
          )}
        </div>
      </PopoverContent>
    </Popover>
  );
}
