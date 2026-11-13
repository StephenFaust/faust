import * as React from 'react';
import { Check } from 'lucide-react';
import { cn } from '@/lib/utils';

export interface StepsProps {
  steps: string[];
  /** 从 1 开始 */
  current: number;
  onChange?: (step: number) => void;
}

/** 步骤条：对应 Agent 表单的四步向导 */
export function Steps({ steps, current, onChange }: StepsProps) {
  return (
    <ol className="flex items-center gap-2">
      {steps.map((label, index) => {
        const step = index + 1;
        const done = step < current;
        const active = step === current;
        return (
          <React.Fragment key={label}>
            <li className="flex min-w-0 items-center gap-2">
              <button
                type="button"
                disabled={!onChange || step > current}
                onClick={() => onChange?.(step)}
                className={cn(
                  'flex h-6 w-6 shrink-0 items-center justify-center rounded-full border text-[11.5px] font-semibold transition-all duration-300 ease-expo',
                  done &&
                    'border-primary bg-primary text-primary-foreground shadow-[0_3px_10px_-3px_hsl(var(--primary)/0.65)]',
                  active &&
                    'border-primary bg-primary/10 text-primary shadow-[0_0_0_4px_hsl(var(--primary)/0.10)]',
                  !done && !active && 'border-border bg-card text-muted-foreground',
                  onChange && step <= current ? 'cursor-pointer' : 'cursor-default'
                )}
              >
                {done ? <Check className="h-3 w-3" /> : step}
              </button>
              <span
                className={cn(
                  'whitespace-nowrap text-[12.5px]',
                  active ? 'font-medium text-foreground' : 'text-muted-foreground'
                )}
              >
                {label}
              </span>
            </li>
            {index < steps.length - 1 ? (
              <li
                aria-hidden
                className={cn(
                  'h-px min-w-4 flex-1 transition-colors duration-300',
                  done ? 'bg-gradient-to-r from-primary/55 to-primary/20' : 'bg-border'
                )}
              />
            ) : null}
          </React.Fragment>
        );
      })}
    </ol>
  );
}
