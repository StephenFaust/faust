import * as React from 'react';
import { Trash2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { cn } from '@/lib/utils';
import { ENV_KEY_MAX_LEN, ENV_VALUE_MAX_LEN, createEnvRow, type EnvRow } from '@/lib/env';

/**
 * 环境变量行编辑器（key = value），会话内面板与全局配置弹窗共用。
 *
 * 只负责渲染与增删行，校验与提交交由调用方（见 `lib/env.ts#validateEnvRows`）。
 */
export function EnvVarRows({
  rows,
  onChange,
  errors
}: {
  rows: EnvRow[];
  onChange: (rows: EnvRow[]) => void;
  errors: Record<string, string>;
}) {
  const update = (id: string, patch: Partial<EnvRow>) => {
    onChange(rows.map((row) => (row.id === id ? { ...row, ...patch } : row)));
  };

  const remove = (id: string) => {
    onChange(rows.length === 1 ? [createEnvRow()] : rows.filter((row) => row.id !== id));
  };

  return (
    <div className="flex max-h-[240px] flex-col gap-2 overflow-y-auto">
      {rows.map((row) => (
        <div key={row.id} className="flex flex-col gap-1">
          <div className="flex items-center gap-1.5">
            <Input
              value={row.key}
              maxLength={ENV_KEY_MAX_LEN}
              placeholder="KEY"
              autoComplete="off"
              onChange={(event) => update(row.id, { key: event.target.value })}
              className={cn('h-8 flex-1 font-mono text-[12px]', errors[row.id] && 'border-rose-400')}
            />
            <span className="text-muted-foreground/50">=</span>
            <Input
              value={row.value}
              maxLength={ENV_VALUE_MAX_LEN}
              placeholder="value"
              autoComplete="off"
              onChange={(event) => update(row.id, { value: event.target.value })}
              className={cn('h-8 flex-1 font-mono text-[12px]', errors[row.id] && 'border-rose-400')}
            />
            <Button
              variant="ghost"
              size="icon"
              className="h-8 w-8 shrink-0 text-muted-foreground"
              onClick={() => remove(row.id)}
            >
              <Trash2 className="h-3.5 w-3.5" />
            </Button>
          </div>
          {errors[row.id] ? <p className="text-[11px] text-rose-600">{errors[row.id]}</p> : null}
        </div>
      ))}
    </div>
  );
}
