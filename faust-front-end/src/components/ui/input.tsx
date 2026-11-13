import * as React from 'react';
import { cn } from '@/lib/utils';

export type InputProps = React.InputHTMLAttributes<HTMLInputElement>;

const Input = React.forwardRef<HTMLInputElement, InputProps>(({ className, type, ...props }, ref) => (
  <input
    type={type}
    ref={ref}
    className={cn(
      'flex h-9 w-full rounded-[10px] border border-input bg-card/70 px-3 py-1 text-[13.5px] text-foreground',
      'shadow-[inset_0_1px_2px_hsl(240_30%_12%/0.03)]',
      'transition-all duration-200 ease-expo',
      'placeholder:text-muted-foreground/55',
      'hover:border-input/80',
      'focus-visible:outline-none focus-visible:border-primary/60 focus-visible:bg-card',
      'focus-visible:ring-4 focus-visible:ring-primary/10',
      'disabled:cursor-not-allowed disabled:opacity-50',
      className
    )}
    {...props}
  />
));
Input.displayName = 'Input';

const Textarea = React.forwardRef<HTMLTextAreaElement, React.TextareaHTMLAttributes<HTMLTextAreaElement>>(
  ({ className, ...props }, ref) => (
    <textarea
      ref={ref}
      className={cn(
        'flex min-h-[80px] w-full rounded-[10px] border border-input bg-card/70 px-3 py-2.5 text-[13.5px] leading-relaxed text-foreground',
        'shadow-[inset_0_1px_2px_hsl(240_30%_12%/0.03)]',
        'transition-all duration-200 ease-expo',
        'placeholder:text-muted-foreground/55',
        'hover:border-input/80',
        'focus-visible:outline-none focus-visible:border-primary/60 focus-visible:bg-card',
        'focus-visible:ring-4 focus-visible:ring-primary/10',
        'disabled:cursor-not-allowed disabled:opacity-50',
        className
      )}
      {...props}
    />
  )
);
Textarea.displayName = 'Textarea';

export { Input, Textarea };
