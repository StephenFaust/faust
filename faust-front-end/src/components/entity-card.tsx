import * as React from 'react';
import { SpotlightCard } from '@/components/ui/spotlight-card';
import { gradientAvatarStyle } from '@/lib/avatar';
import { cn } from '@/lib/utils';

export interface EntityCardProps {
  /** 标题，长文本单行省略 */
  title: string;
  /** 头像色相种子（默认取 title） */
  seed?: string;
  icon: React.ReactNode;
  /** 次级标识，如 agentCode / skillCode / 数据库类型 */
  code?: string;
  version?: string;
  /** 描述 / 连接地址等主信息，固定两行高度保证卡片等高 */
  description?: React.ReactNode;
  descriptionMono?: boolean;
  /** 状态、类型、标签等徽标行 */
  badges?: React.ReactNode;
  /** 底部左侧元信息 */
  meta?: React.ReactNode;
  /** 底部右侧操作区 */
  actions?: React.ReactNode;
  onOpen?: () => void;
}

/**
 * 卡片：与「智能体」页保持同一套视觉语言 ——
 * 渐变头像 + 两行描述 + 徽标行 + 细分割线底部操作区。
 * 整卡可点击进入详情，操作区内部点击不冒泡。
 */
export function EntityCard({
  title,
  seed,
  icon,
  code,
  version,
  description,
  descriptionMono,
  badges,
  meta,
  actions,
  onOpen
}: EntityCardProps) {
  const stop = (event: React.SyntheticEvent) => event.stopPropagation();

  return (
    <SpotlightCard
      onClick={onOpen}
      className={cn(
        'ring-gradient group relative flex flex-col overflow-hidden rounded-2xl border border-border/70 bg-card p-4',
        'shadow-panel transition-[transform,box-shadow,border-color] duration-300 ease-expo',
        onOpen && 'cursor-pointer hover:-translate-y-1 hover:border-primary/25 hover:shadow-panel-hover'
      )}
    >
      {/* 顶部一条极细的品牌光线，hover 时点亮 */}
      <span
        aria-hidden
        className="pointer-events-none absolute inset-x-0 top-0 h-px bg-gradient-to-r from-transparent via-primary/50 to-transparent opacity-0 transition-opacity duration-500 group-hover:opacity-100"
      />

      <div className="flex items-start gap-3">
        <span
          className="relative flex h-9 w-9 shrink-0 items-center justify-center rounded-[10px] text-white shadow-[0_4px_14px_-4px_hsl(244_80%_50%/0.55),inset_0_1px_0_0_hsl(0_0%_100%/0.28)] [&_svg]:h-4 [&_svg]:w-4"
          style={gradientAvatarStyle(seed || title)}
        >
          {icon}
        </span>

        <div className="min-w-0 flex-1">
          <h3
            className="truncate font-display text-[14px] font-semibold leading-tight tracking-[-0.015em] text-foreground"
            title={title}
          >
            {title}
          </h3>
          <div className="mt-1 flex items-center gap-1.5 text-[11px] text-muted-foreground">
            {version ? (
              <span className="shrink-0 rounded bg-muted/70 px-1.5 py-px font-mono text-[10.5px] text-foreground/60">
                v{version}
              </span>
            ) : null}
            {version && code ? <span className="opacity-30">·</span> : null}
            {code ? (
              <span className="truncate font-mono text-[10.5px]" title={code}>
                {code}
              </span>
            ) : null}
          </div>
        </div>
      </div>

      <div
        className={cn(
          'mt-3 line-clamp-2 min-h-[36px] break-words text-[12.5px] leading-[1.45] text-muted-foreground',
          descriptionMono && 'font-mono text-[11.5px] leading-[1.55]'
        )}
        title={typeof description === 'string' ? description : undefined}
      >
        {description || '暂无描述'}
      </div>

      <div className="mt-2.5 flex min-h-[22px] flex-wrap items-center gap-1.5">{badges}</div>

      {/* mt-auto 铺开剩余空间后，再给分割线留出上方的呼吸，线因此更靠近底部 */}
      <div className="mt-auto pt-3.5">
        <div className="flex items-center justify-between gap-2 border-t border-hairline pt-2.5">
          <div className="flex min-w-0 items-center gap-2.5 overflow-hidden text-[11px] text-muted-foreground">
            {meta}
          </div>
          {actions ? (
            <div
              className="flex shrink-0 items-center gap-0.5"
              onClick={stop}
              onKeyDown={stop}
              role="presentation"
            >
              {actions}
            </div>
          ) : null}
        </div>
      </div>
    </SpotlightCard>
  );
}

/** 卡片内小号图标按钮的公共样式，下拉菜单触发器也复用它 */
export const cardActionClass = cn(
  'inline-flex h-7 w-7 items-center justify-center rounded-md text-muted-foreground/80 transition-colors duration-200',
  'hover:bg-muted hover:text-foreground',
  'disabled:pointer-events-none disabled:opacity-35',
  '[&_svg]:h-3.5 [&_svg]:w-3.5'
);

/** 卡片内的小号图标操作按钮 */
export function CardAction({
  label,
  icon,
  onClick,
  disabled,
  destructive
}: {
  label: string;
  icon: React.ReactNode;
  onClick: () => void;
  disabled?: boolean;
  destructive?: boolean;
}) {
  return (
    <button
      type="button"
      title={label}
      aria-label={label}
      disabled={disabled}
      onClick={onClick}
      className={cn(cardActionClass, destructive && 'hover:bg-rose-50 hover:text-rose-600')}
    >
      {icon}
    </button>
  );
}
