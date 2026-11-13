import * as React from 'react';
import { Label } from '@/components/ui/label';
import { cn } from '@/lib/utils';

/**
 * 表单字段容器：label + 必填星号 + 错误/提示文案。
 *
 * 迁移自 antd `Form.Item`（layout="vertical"）的视觉与校验态，
 * 但校验时机由业务代码控制（与后端字段语义强相关，不引入表单库）。
 */
export interface FieldProps {
  label?: React.ReactNode;
  required?: boolean;
  error?: string;
  hint?: React.ReactNode;
  htmlFor?: string;
  className?: string;
  children: React.ReactNode;
}

export function Field({ label, required, error, hint, htmlFor, className, children }: FieldProps) {
  return (
    <div className={cn('flex flex-col gap-1.5', className)}>
      {label ? (
        <Label htmlFor={htmlFor} className="flex items-center gap-1 text-[12.5px] text-foreground/80">
          {label}
          {required ? <span className="text-rose-500">*</span> : null}
        </Label>
      ) : null}
      <div className={cn(error && '[&_input]:border-rose-400 [&_textarea]:border-rose-400')}>{children}</div>
      {error ? (
        <p className="text-[11.5px] leading-relaxed text-rose-600 dark:text-rose-400">{error}</p>
      ) : hint ? (
        <p className="text-[11.5px] leading-relaxed text-muted-foreground">{hint}</p>
      ) : null}
    </div>
  );
}

/** 表单分组：浅色描边容器，对应 antd 的内嵌 Card */
export function FieldGroup({
  title,
  icon,
  extra,
  children,
  className
}: {
  title?: React.ReactNode;
  icon?: React.ReactNode;
  extra?: React.ReactNode;
  children: React.ReactNode;
  className?: string;
}) {
  return (
    <section
      className={cn(
        'overflow-hidden rounded-2xl border border-border/70 bg-card shadow-panel',
        className
      )}
    >
      {title || extra ? (
        <header className="flex items-center justify-between gap-3 border-b border-hairline px-5 py-3">
          <h3 className="flex items-center gap-2 font-display text-[13.5px] font-semibold tracking-[-0.012em] text-foreground">
            {icon}
            {title}
          </h3>
          {extra}
        </header>
      ) : null}
      <div className="flex flex-col gap-4 px-5 py-4">{children}</div>
    </section>
  );
}
