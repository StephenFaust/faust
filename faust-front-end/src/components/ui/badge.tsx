import * as React from 'react';
import { cva, type VariantProps } from 'class-variance-authority';
import { cn } from '@/lib/utils';

/**
 * 状态 / 类型标签。
 * 统一用「低饱和底色 + 同色内描边」的 tonal 方案，
 * 避免彩色边框在浅色表格里显得毛躁。
 */
const badgeVariants = cva(
  'inline-flex items-center gap-1 whitespace-nowrap rounded-full px-2.5 py-[3px] text-[11px] font-medium leading-[16px] ring-1 ring-inset transition-colors',
  {
    variants: {
      tone: {
        default: 'bg-primary/10 text-primary ring-primary/20',
        secondary: 'bg-secondary text-secondary-foreground ring-border/60',
        outline: 'bg-transparent text-muted-foreground ring-border',
        success: 'bg-emerald-500/12 text-emerald-600 ring-emerald-500/20 dark:text-emerald-400',
        warning: 'bg-amber-500/14 text-amber-700 ring-amber-500/22 dark:text-amber-400',
        danger: 'bg-rose-500/12 text-rose-600 ring-rose-500/20 dark:text-rose-400',
        info: 'bg-sky-500/12 text-sky-700 ring-sky-500/20 dark:text-sky-400',
        purple: 'bg-violet-500/12 text-violet-700 ring-violet-500/20 dark:text-violet-300',
        muted: 'bg-muted text-muted-foreground ring-border/60'
      }
    },
    defaultVariants: { tone: 'default' }
  }
);

export interface BadgeProps
  extends React.HTMLAttributes<HTMLSpanElement>,
    VariantProps<typeof badgeVariants> {}

function Badge({ className, tone, ...props }: BadgeProps) {
  return <span className={cn(badgeVariants({ tone }), className)} {...props} />;
}

export { Badge, badgeVariants };
