import * as React from 'react';
import { FileCode2 } from 'lucide-react';
import { Markdown } from '@/components/markdown';
import { splitFrontmatter, type FrontmatterEntry } from '@/lib/frontmatter';
import { cn } from '@/lib/utils';

/**
 * 文件头展示：把 skill.md 顶部那段 YAML frontmatter 渲染成紧凑的键值表。
 *
 * 之前直接丢给 markdown-it，`breaks: true` 会把每一行都当软换行输出，
 * 于是整段 YAML 变成一坨没有层级的纯文本。这里按字段拆开：键走等宽小字，
 * 值按单行 / 多行 / 数组分别排版，视觉上对齐详情页既有的字段块（1px 描边 + 紧凑行高）。
 */
export function FrontmatterCard({ entries, className }: { entries: FrontmatterEntry[]; className?: string }) {
  return (
    <section className={cn('overflow-hidden rounded-lg border border-border/70', className)} aria-label="文件头信息">
      <div className="flex items-center gap-1.5 border-b border-border/70 bg-muted/25 px-3 py-1.5 text-[11px] font-medium tracking-[0.01em] text-muted-foreground">
        <FileCode2 className="h-3.5 w-3.5" />
        文件头
      </div>
      <table className="w-full table-auto border-collapse text-left">
        <tbody>
          {entries.map((entry, index) => (
            <tr
              key={`${entry.key}-${index}`}
              className="border-b border-border/60 align-top last:border-b-0"
            >
              <th
                scope="row"
                className="w-px whitespace-nowrap bg-muted/15 px-3 py-1.5 font-mono text-[11.5px] font-normal text-muted-foreground"
              >
                {entry.key}
              </th>
              <td className="px-3 py-1.5 text-[12.5px] leading-relaxed text-foreground/90">
                {entry.list === undefined ? (
                  entry.value ? (
                    <span className="whitespace-pre-wrap break-words">{entry.value}</span>
                  ) : (
                    <span className="text-muted-foreground">—</span>
                  )
                ) : entry.list.length ? (
                  <div className="flex flex-wrap gap-1">
                    {entry.list.map((item, itemIndex) => (
                      <span
                        key={`${item}-${itemIndex}`}
                        className="rounded-md border border-border/70 bg-muted/30 px-1.5 py-0.5 text-[11.5px] text-foreground/85"
                      >
                        {item}
                      </span>
                    ))}
                  </div>
                ) : (
                  <span className="text-muted-foreground">—</span>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  );
}

/**
 * SKILL 文件内容渲染：文件头单独成卡，正文走 Markdown。
 * 没有文件头时行为与直接使用 `<Markdown>` 完全一致。
 */
export function SkillMarkdown({ content, className }: { content?: string | null; className?: string }) {
  const { entries, body } = React.useMemo(() => splitFrontmatter(content), [content]);
  const hasBody = Boolean(body.trim());
  if (!entries && !hasBody) return null;

  return (
    <div className={cn('flex flex-col gap-3', className)}>
      {entries ? <FrontmatterCard entries={entries} /> : null}
      {hasBody ? <Markdown content={body} /> : null}
    </div>
  );
}
