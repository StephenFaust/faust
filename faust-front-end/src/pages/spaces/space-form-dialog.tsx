import * as React from 'react';
import {
  Dialog,
  DialogBody,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { toast } from 'sonner';
import { createSpace, renameSpace, type SpaceInfo } from '@/services/space';

export function SpaceFormDialog({
  open,
  mode,
  target,
  onClose,
  onSaved
}: {
  open: boolean;
  mode: 'create' | 'rename';
  target?: SpaceInfo | null;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [name, setName] = React.useState('');
  const [description, setDescription] = React.useState('');
  const [submitting, setSubmitting] = React.useState(false);

  React.useEffect(() => {
    if (!open) return;
    setName(mode === 'rename' ? (target?.name ?? '') : '');
    setDescription('');
  }, [open, mode, target]);

  const submit = async () => {
    const trimmed = name.trim();
    if (!trimmed) {
      toast.error('空间名称不能为空');
      return;
    }
    setSubmitting(true);
    try {
      if (mode === 'create') {
        await createSpace({ name: trimmed, description: description.trim() || undefined });
        toast.success('空间已创建');
      } else if (target) {
        await renameSpace({ spaceId: target.spaceId, name: trimmed });
        toast.success('空间已重命名');
      }
      onSaved();
      onClose();
    } catch (error) {
      toast.error(`${mode === 'create' ? '创建' : '重命名'}失败：${error instanceof Error ? error.message : '未知错误'}`);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={(next) => !next && onClose()}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>{mode === 'create' ? '新建团队空间' : '重命名空间'}</DialogTitle>
        </DialogHeader>
        <DialogBody className="flex flex-col gap-4">
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="space-name">空间名称</Label>
            <Input
              id="space-name"
              value={name}
              maxLength={128}
              autoFocus
              placeholder="如：美租商品组"
              onChange={(event) => setName(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === 'Enter') void submit();
              }}
            />
          </div>
          {mode === 'create' ? (
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="space-desc">描述（可选）</Label>
              <Input
                id="space-desc"
                value={description}
                maxLength={512}
                placeholder="这个空间用来做什么"
                onChange={(event) => setDescription(event.target.value)}
              />
            </div>
          ) : null}
        </DialogBody>
        <DialogFooter>
          <Button variant="ghost" onClick={onClose} disabled={submitting}>
            取消
          </Button>
          <Button onClick={() => void submit()} disabled={submitting}>
            {submitting ? '提交中…' : '确定'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
