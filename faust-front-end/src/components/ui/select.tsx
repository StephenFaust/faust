import * as React from 'react';
import * as SelectPrimitive from '@radix-ui/react-select';
import { Check, ChevronDown, X } from 'lucide-react';
import { cn } from '@/lib/utils';

export interface SelectOption {
  label: React.ReactNode;
  value: string | number;
  disabled?: boolean;
}

export interface SelectProps {
  value?: string | number | null;
  onChange?: (value: string | number | undefined) => void;
  options?: SelectOption[];
  placeholder?: string;
  disabled?: boolean;
  allowClear?: boolean;
  className?: string;
  size?: 'sm' | 'default';
}

/**
 * 面向业务场景的受控下拉，API 贴近后台表单习惯（value / onChange(value) / options）。
 * 底层为 Radix Select，保证键盘可达性与无障碍语义。
 */
export function Select({
  value,
  onChange,
  options = [],
  placeholder = '请选择',
  disabled,
  allowClear,
  className,
  size = 'default'
}: SelectProps) {
  const stringValue = value === undefined || value === null || value === '' ? undefined : String(value);

  return (
    <div className={cn('relative', className)}>
      <SelectPrimitive.Root
        value={stringValue}
        onValueChange={(next) => onChange?.(next)}
        disabled={disabled}
      >
        <SelectPrimitive.Trigger
          className={cn(
            'flex w-full items-center justify-between gap-2 rounded-[10px] border border-input bg-card/70 px-3 text-[13.5px] text-foreground',
            'shadow-[inset_0_1px_2px_hsl(240_30%_12%/0.03)]',
            'transition-all duration-200 ease-expo',
            'hover:border-input/80',
            'focus:outline-none focus:border-primary/60 focus:bg-card focus:ring-4 focus:ring-primary/10',
            'disabled:cursor-not-allowed disabled:opacity-50',
            'data-[placeholder]:text-muted-foreground/55',
            size === 'sm' ? 'h-8 text-[12.5px]' : 'h-9',
            allowClear && stringValue ? 'pr-8' : ''
          )}
        >
          <SelectPrimitive.Value placeholder={placeholder} />
          <SelectPrimitive.Icon asChild>
            <ChevronDown className="h-3.5 w-3.5 shrink-0 text-muted-foreground/50" />
          </SelectPrimitive.Icon>
        </SelectPrimitive.Trigger>

        <SelectPrimitive.Portal>
          <SelectPrimitive.Content
            position="popper"
            sideOffset={6}
            className={cn(
              'z-[60] max-h-72 min-w-[var(--radix-select-trigger-width)] overflow-hidden rounded-xl p-1',
              'border border-border/80 bg-popover/95 shadow-float backdrop-blur-xl animate-fade-in'
            )}
          >
            <SelectPrimitive.Viewport>
              {options.length === 0 ? (
                <div className="px-2 py-6 text-center text-[12.5px] text-muted-foreground">暂无数据</div>
              ) : (
                options.map((option) => (
                  <SelectPrimitive.Item
                    key={String(option.value)}
                    value={String(option.value)}
                    disabled={option.disabled}
                    className={cn(
                      'relative flex cursor-pointer select-none items-center rounded-lg py-1.5 pl-2.5 pr-8 text-[13px] outline-none transition-colors',
                      'data-[highlighted]:bg-primary/[0.07] data-[highlighted]:text-foreground',
                      'data-[state=checked]:font-medium data-[state=checked]:text-primary',
                      'data-[disabled]:pointer-events-none data-[disabled]:opacity-45'
                    )}
                  >
                    <SelectPrimitive.ItemText>{option.label}</SelectPrimitive.ItemText>
                    <SelectPrimitive.ItemIndicator className="absolute right-2.5">
                      <Check className="h-3.5 w-3.5" />
                    </SelectPrimitive.ItemIndicator>
                  </SelectPrimitive.Item>
                ))
              )}
            </SelectPrimitive.Viewport>
          </SelectPrimitive.Content>
        </SelectPrimitive.Portal>
      </SelectPrimitive.Root>

      {allowClear && stringValue ? (
        <button
          type="button"
          onClick={() => onChange?.(undefined)}
          className="absolute right-8 top-1/2 -translate-y-1/2 rounded-md p-0.5 text-muted-foreground/45 transition hover:bg-muted hover:text-foreground"
          aria-label="清空"
        >
          <X className="h-3.5 w-3.5" />
        </button>
      ) : null}
    </div>
  );
}
