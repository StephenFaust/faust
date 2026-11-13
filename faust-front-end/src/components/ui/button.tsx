import * as React from 'react';
import { Slot } from '@radix-ui/react-slot';
import { cva, type VariantProps } from 'class-variance-authority';
import { cn } from '@/lib/utils';

const buttonVariants = cva(
  [
    'relative inline-flex items-center justify-center gap-2 whitespace-nowrap rounded-[10px] text-sm font-medium',
    'transition-all duration-200 ease-expo',
    'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring/45 focus-visible:ring-offset-2 focus-visible:ring-offset-background',
    'disabled:pointer-events-none disabled:opacity-45',
    '[&_svg]:pointer-events-none [&_svg]:size-4 [&_svg]:shrink-0'
  ].join(' '),
  {
    variants: {
      variant: {
        /* 主按钮：实心品牌色 + 内高光 + 外辉光 + hover 高光扫过 */
        default: [
          'btn-sheen bg-primary text-primary-foreground',
          'shadow-[inset_0_1px_0_0_hsl(0_0%_100%/0.18),0_6px_18px_-8px_hsl(var(--primary)/0.65)]',
          'hover:brightness-[1.07] hover:shadow-[inset_0_1px_0_0_hsl(0_0%_100%/0.22),0_10px_26px_-8px_hsl(var(--primary)/0.7)]',
          'active:scale-[0.985] active:brightness-[0.97]'
        ].join(' '),
        secondary:
          'bg-secondary text-secondary-foreground hover:bg-secondary/70 active:scale-[0.985]',
        /* 线性按钮：静默的玻璃质感，hover 才浮起 */
        outline: [
          'border border-input bg-card/80 text-foreground/85 backdrop-blur-sm',
          'shadow-[0_1px_2px_hsl(240_30%_12%/0.04)]',
          'hover:border-primary/35 hover:bg-card hover:text-foreground',
          'hover:shadow-[0_2px_10px_-3px_hsl(240_30%_12%/0.12)]',
          'active:scale-[0.985]'
        ].join(' '),
        ghost: 'text-foreground/70 hover:bg-muted hover:text-foreground active:scale-[0.985]',
        destructive: [
          'bg-destructive text-destructive-foreground',
          'shadow-[inset_0_1px_0_0_hsl(0_0%_100%/0.16),0_6px_18px_-8px_hsl(var(--destructive)/0.6)]',
          'hover:brightness-[1.06] active:scale-[0.985]'
        ].join(' '),
        link: 'text-primary underline-offset-4 hover:underline'
      },
      size: {
        default: 'h-9 px-4 py-2',
        sm: 'h-8 rounded-lg px-3 text-[13px]',
        xs: 'h-7 rounded-md px-2 text-xs',
        lg: 'h-10 rounded-[11px] px-6',
        icon: 'h-9 w-9'
      }
    },
    defaultVariants: { variant: 'default', size: 'default' }
  }
);

export interface ButtonProps
  extends React.ButtonHTMLAttributes<HTMLButtonElement>,
    VariantProps<typeof buttonVariants> {
  asChild?: boolean;
}

const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(
  ({ className, variant, size, asChild = false, ...props }, ref) => {
    const Comp = asChild ? Slot : 'button';
    return <Comp className={cn(buttonVariants({ variant, size, className }))} ref={ref} {...props} />;
  }
);
Button.displayName = 'Button';

export { Button, buttonVariants };
