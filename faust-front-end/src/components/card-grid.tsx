import * as React from 'react';
import { Loader2, Sparkles } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { EmptyState } from '@/components/empty-state';
import { cn } from '@/lib/utils';

/** 卡片网格容器：与「智能体」页同一套栅格与入场节奏 */
export function CardGrid({ children, className }: { children: React.ReactNode; className?: string }) {
  return (
    <div className={cn('stagger grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-3', className)}>
      {children}
    </div>
  );
}

/** 首屏骨架：结构对齐 EntityCard，避免加载完成时布局跳动 */
export function CardGridSkeleton({ count = 6 }: { count?: number }) {
  return (
    <CardGrid>
      {Array.from({ length: count }).map((_, index) => (
        <div
          key={index}
          className="rounded-2xl border border-border/70 bg-card p-4 shadow-panel"
        >
          <div className="flex items-center gap-3">
            <span className="skeleton-sheen h-9 w-9 rounded-[10px]" />
            <div className="flex flex-1 flex-col gap-2">
              <span className="skeleton-sheen h-3.5 w-1/2 rounded-full" />
              <span className="skeleton-sheen h-2.5 w-1/4 rounded-full" />
            </div>
          </div>
          <span className="skeleton-sheen mt-4 block h-2.5 w-full rounded-full" />
          <span className="skeleton-sheen mt-2 block h-2.5 w-4/5 rounded-full" />
          <div className="mt-5 flex gap-2">
            <span className="skeleton-sheen h-5 w-14 rounded-full" />
            <span className="skeleton-sheen h-5 w-14 rounded-full" />
          </div>
          <div className="mt-4 pt-3.5">
            <div className="flex items-center justify-between border-t border-hairline pt-2.5">
              <span className="skeleton-sheen h-2.5 w-16 rounded-full" />
              <span className="skeleton-sheen h-5 w-20 rounded-full" />
            </div>
          </div>
        </div>
      ))}
    </CardGrid>
  );
}

export interface LazyCardGridProps {
  /** 已渲染的卡片数量（用于区分首屏加载 / 追加加载） */
  count: number;
  total: number;
  loading: boolean;
  loadingMore: boolean;
  error: string | null;
  hasMore: boolean;
  onLoadMore: () => void;
  onRetry: () => void;
  /** 空态节点（由页面提供文案与图标） */
  empty: React.ReactNode;
  skeletonCount?: number;
  children: React.ReactNode;
}

/**
 * 卡片流 + 懒加载。
 *
 * 底部放一个哨兵节点，用 IntersectionObserver 提前 320px 触发下一页；
 * 同时保留「加载更多」按钮，作为不支持 observer / 键盘用户的兜底入口。
 */
export function LazyCardGrid({
  count,
  total,
  loading,
  loadingMore,
  error,
  hasMore,
  onLoadMore,
  onRetry,
  empty,
  skeletonCount = 6,
  children
}: LazyCardGridProps) {
  const sentinelRef = React.useRef<HTMLDivElement | null>(null);
  const loadMoreRef = React.useRef(onLoadMore);
  loadMoreRef.current = onLoadMore;

  React.useEffect(() => {
    const node = sentinelRef.current;
    if (!node || !hasMore || error) return undefined;
    if (typeof IntersectionObserver === 'undefined') return undefined;

    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((entry) => entry.isIntersecting)) loadMoreRef.current();
      },
      { rootMargin: '320px 0px' }
    );
    observer.observe(node);
    return () => observer.disconnect();
  }, [hasMore, error, loading, loadingMore]);

  if (loading && count === 0) return <CardGridSkeleton count={skeletonCount} />;

  if (error && count === 0) {
    return (
      <EmptyState
        title="加载失败"
        description={error}
        action={
          <Button variant="outline" size="sm" onClick={onRetry}>
            重试
          </Button>
        }
      />
    );
  }

  if (count === 0) return <>{empty}</>;

  return (
    <>
      {children}

      <div ref={sentinelRef} aria-hidden className="h-px w-full" />

      <div className="mt-7 flex flex-col items-center gap-2">
        {error ? (
          <>
            <span className="text-[12px] text-rose-600">{error}</span>
            <Button variant="outline" size="sm" onClick={onRetry}>
              重试
            </Button>
          </>
        ) : hasMore ? (
          <Button variant="outline" onClick={onLoadMore} disabled={loadingMore}>
            {loadingMore ? (
              <>
                <Loader2 className="h-3.5 w-3.5 animate-spin" />
                加载中…
              </>
            ) : (
              `加载更多（还有 ${total - count} 个）`
            )}
          </Button>
        ) : (
          <span className="flex items-center gap-1.5 text-[12px] text-muted-foreground">
            <Sparkles className="h-3.5 w-3.5 text-primary/50" />
            已展示全部 {total} 个
          </span>
        )}
      </div>
    </>
  );
}
