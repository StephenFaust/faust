import * as React from 'react';
import { Inbox } from 'lucide-react';
import { cn } from '@/lib/utils';

export interface DataTableColumn<T> {
  key?: string;
  dataIndex?: string;
  title: React.ReactNode;
  width?: number | string;
  align?: 'left' | 'center' | 'right';
  className?: string;
  render?: (value: unknown, record: T, index: number) => React.ReactNode;
}

export interface DataTableProps<T> {
  columns: DataTableColumn<T>[];
  dataSource: T[];
  rowKey?: string | ((record: T) => string | number);
  loading?: boolean;
  emptyText?: string;
  emptyHint?: string;
  className?: string;
  onRowClick?: (record: T) => void;
}

function resolveRowKey<T>(record: T, rowKey: DataTableProps<T>['rowKey'], index: number): string | number {
  if (typeof rowKey === 'function') return rowKey(record);
  if (typeof rowKey === 'string') {
    const value = (record as Record<string, unknown>)[rowKey];
    if (typeof value === 'string' || typeof value === 'number') return value;
  }
  return index;
}

export function DataTable<T>({
  columns,
  dataSource,
  rowKey = 'id',
  loading,
  emptyText = '暂无数据',
  emptyHint,
  className,
  onRowClick
}: DataTableProps<T>) {
  return (
    <div className={cn('relative w-full overflow-x-auto', className)}>
      <table className="w-full border-collapse text-[13px]">
        <thead>
          <tr className="border-b border-border">
            {columns.map((column, index) => (
              <th
                key={column.key ?? column.dataIndex ?? index}
                style={{ width: column.width }}
                className={cn(
                  'sticky top-0 z-[1] whitespace-nowrap bg-card/95 px-4 py-2.5 text-left',
                  'text-[11.5px] font-semibold tracking-[0.02em] text-muted-foreground backdrop-blur',
                  'first:pl-5 last:pr-5',
                  column.align === 'center' && 'text-center',
                  column.align === 'right' && 'text-right',
                  column.className
                )}
              >
                {column.title}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {loading ? (
            Array.from({ length: 5 }).map((_, rowIndex) => (
              <tr key={`skeleton-${rowIndex}`} className="border-b border-hairline last:border-0">
                {columns.map((column, cellIndex) => (
                  <td
                    key={`skeleton-${rowIndex}-${cellIndex}`}
                    className={cn('px-4 py-3.5 first:pl-5 last:pr-5', column.className)}
                  >
                    <span
                      className="skeleton-sheen block h-3 rounded-full"
                      style={{ width: cellIndex === 0 ? '52%' : cellIndex === columns.length - 1 ? '38%' : '68%' }}
                    />
                  </td>
                ))}
              </tr>
            ))
          ) : dataSource.length === 0 ? (
            <tr>
              <td colSpan={columns.length} className="px-4 py-16">
                <div className="flex flex-col items-center justify-center gap-2.5 text-center">
                  <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-muted/70 text-muted-foreground/60 ring-1 ring-inset ring-border/70">
                    <Inbox className="h-[17px] w-[17px]" />
                  </span>
                  <p className="text-[13px] font-medium text-foreground/70">{emptyText}</p>
                  {emptyHint ? (
                    <p className="max-w-xs text-[12px] leading-relaxed text-muted-foreground">
                      {emptyHint}
                    </p>
                  ) : null}
                </div>
              </td>
            </tr>
          ) : (
            dataSource.map((record, rowIndex) => (
              <tr
                key={resolveRowKey(record, rowKey, rowIndex)}
                onClick={onRowClick ? () => onRowClick(record) : undefined}
                className={cn(
                  'group border-b border-hairline transition-colors duration-150 last:border-0',
                  'hover:bg-primary/[0.028]',
                  onRowClick && 'cursor-pointer'
                )}
              >
                {columns.map((column, index) => {
                  const raw = column.dataIndex
                    ? (record as Record<string, unknown>)[column.dataIndex]
                    : undefined;
                  const content = column.render
                    ? column.render(raw, record, rowIndex)
                    : ((raw as React.ReactNode) ?? '—');
                  return (
                    <td
                      key={column.key ?? column.dataIndex ?? index}
                      className={cn(
                        'px-4 py-3.5 align-middle text-foreground/85 first:pl-5 last:pr-5',
                        column.align === 'center' && 'text-center',
                        column.align === 'right' && 'text-right',
                        column.className
                      )}
                    >
                      {content}
                    </td>
                  );
                })}
              </tr>
            ))
          )}
        </tbody>
      </table>
    </div>
  );
}
