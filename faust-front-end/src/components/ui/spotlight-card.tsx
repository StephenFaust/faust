import * as React from 'react';
import { cn } from '@/lib/utils';

type SpotlightCardProps = React.HTMLAttributes<HTMLDivElement>;

/**
 * 光标跟随聚光卡片容器。
 *
 * 通过直接写 CSS 变量（--mx / --my）驱动 `radial-gradient`，
 * 全程不触发 React 重渲染，鼠标移动成本可以忽略。
 */
export const SpotlightCard = React.forwardRef<HTMLDivElement, SpotlightCardProps>(
  ({ className, onMouseMove, ...props }, ref) => {
    const localRef = React.useRef<HTMLDivElement | null>(null);

    const setRefs = (node: HTMLDivElement | null) => {
      localRef.current = node;
      if (typeof ref === 'function') ref(node);
      else if (ref) (ref as React.MutableRefObject<HTMLDivElement | null>).current = node;
    };

    const handleMove = (event: React.MouseEvent<HTMLDivElement>) => {
      const node = localRef.current;
      if (node) {
        const rect = node.getBoundingClientRect();
        node.style.setProperty('--mx', `${event.clientX - rect.left}px`);
        node.style.setProperty('--my', `${event.clientY - rect.top}px`);
      }
      onMouseMove?.(event);
    };

    return (
      <div
        ref={setRefs}
        onMouseMove={handleMove}
        className={cn('spotlight', className)}
        {...props}
      />
    );
  }
);
SpotlightCard.displayName = 'SpotlightCard';
