import * as React from 'react';
import { Loader2, Plus } from 'lucide-react';
import {
  Dialog,
  DialogBody,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { EnvVarRows } from '@/components/env-var-rows';
import { createEnvRow, envListToMap, envMapToList, validateEnvRows, type EnvRow } from '@/lib/env';
import { fetchGlobalEnvVar, saveGlobalEnvVar } from '@/services/agent-center';
import { toast } from 'sonner';

/**
 * 全局环境变量配置（Agent + 用户维度）。
 *
 * 语义：这里配的是「该 Agent 新建会话时的默认值」，新建会话时复制进会话记录；
 * 会话内仍可单独调整，互不影响；已有会话不会被回溯修改。
 */
export function GlobalEnvDialog({
  open,
  agentCode,
  onClose
}: {
  open: boolean;
  agentCode: string;
  onClose: () => void;
}) {
  const [rows, setRows] = React.useState<EnvRow[]>([]);
  const [errors, setErrors] = React.useState<Record<string, string>>({});
  const [loading, setLoading] = React.useState(false);
  const [saving, setSaving] = React.useState(false);

  React.useEffect(() => {
    if (!open || !agentCode) return;
    let cancelled = false;
    setErrors({});
    setLoading(true);
    fetchGlobalEnvVar(agentCode)
      .then((map) => {
        if (cancelled) return;
        const list = envMapToList(map);
        setRows(list.length ? list : [createEnvRow()]);
      })
      .catch((error) => {
        if (cancelled) return;
        setRows([createEnvRow()]);
        toast.error(`加载环境变量失败：${error instanceof Error ? error.message : '未知错误'}`);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [open, agentCode]);

  const submit = async () => {
    const { errors: nextErrors, list } = validateEnvRows(rows);
    if (Object.keys(nextErrors).length) {
      setErrors(nextErrors);
      return;
    }
    setSaving(true);
    try {
      await saveGlobalEnvVar(agentCode, envListToMap(list));
      toast.success('环境变量已保存');
      onClose();
    } catch (error) {
      toast.error(`保存失败：${error instanceof Error ? error.message : '未知错误'}`);
    } finally {
      setSaving(false);
    }
  };

  // 编辑后清空上一次的校验提示
  const handleRows = (next: EnvRow[]) => {
    setRows(next);
    setErrors((prev) => (Object.keys(prev).length ? {} : prev));
  };

  return (
    <Dialog open={open} onOpenChange={(next) => !next && onClose()}>
      <DialogContent className="max-w-lg">
        <DialogHeader>
          <DialogTitle>环境变量</DialogTitle>
          <DialogDescription>
            仅对该 Agent 生效：新建会话时自动带入，会话内仍可单独修改；只影响之后新建的会话，不会修改历史会话。
          </DialogDescription>
        </DialogHeader>
        <DialogBody className="flex flex-col gap-2.5">
          {loading ? (
            <div className="flex h-24 items-center justify-center text-muted-foreground">
              <Loader2 className="h-4 w-4 animate-spin" />
            </div>
          ) : (
            <>
              <EnvVarRows rows={rows} onChange={handleRows} errors={errors} />
              <Button
                variant="ghost"
                size="xs"
                className="self-start"
                onClick={() => setRows((prev) => [...prev, createEnvRow()])}
              >
                <Plus className="h-3.5 w-3.5" />
                添加变量
              </Button>
              <p className="text-[11px] leading-relaxed text-muted-foreground">
                key 需以字母开头，可含字母、数字、下划线、短横线；value 可留空。清空全部变量即取消该 Agent 的默认配置。
              </p>
            </>
          )}
        </DialogBody>
        <DialogFooter>
          <Button variant="ghost" onClick={onClose} disabled={saving}>
            取消
          </Button>
          <Button onClick={() => void submit()} disabled={saving || loading}>
            {saving ? '保存中…' : '保存'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
