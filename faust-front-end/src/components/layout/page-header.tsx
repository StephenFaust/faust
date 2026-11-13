import * as React from 'react';
import { Link } from 'react-router-dom';
import { ChevronRight } from 'lucide-react';
import { cn } from '@/lib/utils';

export interface Crumb {
  text: string;
  to?: string;
}

interface PageHeaderProps {
  title: string;
  description?: React.ReactNode;
  crumbs?: Crumb[];
  actions?: React.ReactNode;
  className?: string;
}

export function PageHeader({ title, description, crumbs, actions, className }: PageHeaderProps) {
  return (
    <header className={cn('mb-7 flex flex-col gap-3.5', className)}>
      {crumbs && crumbs.length > 0 ? (
        <nav className="flex items-center gap-1.5 text-[11.5px] text-muted-foreground/80">
          {crumbs.map((crumb, index) => (
            <React.Fragment key={`${crumb.text}-${index}`}>
              {index > 0 ? <ChevronRight className="h-3 w-3 opacity-35" /> : null}
              {crumb.to ? (
                <Link
                  to={crumb.to}
                  className="rounded transition-colors hover:text-foreground"
                >
                  {crumb.text}
                </Link>
              ) : (
                <span className={index === crumbs.length - 1 ? 'font-medium text-foreground/70' : ''}>
                  {crumb.text}
                </span>
              )}
            </React.Fragment>
          ))}
        </nav>
      ) : null}

      <div className="flex flex-wrap items-end justify-between gap-4">
        <div className="flex flex-col gap-1.5">
          <h1 className="font-display text-[27px] font-semibold leading-[1.15] tracking-[-0.034em] text-foreground">
            {title}
          </h1>
          {description ? (
            <p className="max-w-[68ch] text-[13px] leading-relaxed text-muted-foreground">
              {description}
            </p>
          ) : null}
        </div>
        {actions ? <div className="flex items-center gap-2">{actions}</div> : null}
      </div>
    </header>
  );
}

/** 页面内的内容卡片区块：顶部细描边 + 悬浮渐变边 */
export function SectionCard({
  title,
  description,
  actions,
  children,
  className,
  bodyClassName
}: {
  title?: React.ReactNode;
  description?: React.ReactNode;
  actions?: React.ReactNode;
  children: React.ReactNode;
  className?: string;
  bodyClassName?: string;
}) {
  return (
    <section
      className={cn(
        'ring-gradient overflow-hidden rounded-2xl border border-border/70 bg-card',
        'shadow-[0_1px_1.5px_hsl(240_30%_12%/0.035),0_3px_10px_-3px_hsl(240_30%_12%/0.055),inset_0_1px_0_0_hsl(0_0%_100%/0.04)]',
        className
      )}
    >
      {title || actions ? (
        <div className="flex items-center justify-between gap-3 border-b border-hairline px-5 py-3.5">
          <div className="flex flex-col gap-0.5">
            {title ? (
              <h2 className="font-display text-[14px] font-semibold tracking-[-0.015em] text-foreground">
                {title}
              </h2>
            ) : null}
            {description ? (
              <p className="text-[12px] leading-relaxed text-muted-foreground">{description}</p>
            ) : null}
          </div>
          {actions}
        </div>
      ) : null}
      <div className={cn('px-5 py-4', bodyClassName)}>{children}</div>
    </section>
  );
}
