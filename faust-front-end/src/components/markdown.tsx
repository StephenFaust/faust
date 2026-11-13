import * as React from 'react';
import { mdParseBlocks, mdRender } from '@/lib/markdown';
import { cn } from '@/lib/utils';

interface MarkdownProps {
  content?: string | null;
  /** 流式渲染：按块增量渲染，避免长文本每帧整篇重排 */
  streaming?: boolean;
  className?: string;
}

export function Markdown({ content, streaming, className }: MarkdownProps) {
  const html = React.useMemo(() => {
    if (!content) return [];
    return streaming ? mdParseBlocks(content) : [mdRender(content)];
  }, [content, streaming]);

  if (html.length === 0) return null;

  return (
    <div className={cn('markdown-body', className)}>
      {html.map((fragment, index) => (
        <div key={index} dangerouslySetInnerHTML={{ __html: fragment }} />
      ))}
    </div>
  );
}
