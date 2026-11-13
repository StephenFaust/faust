import * as React from 'react';
import { Loader2, Trash2, UserPlus } from 'lucide-react';
import {
  Dialog,
  DialogBody,
  DialogContent,
  DialogHeader,
  DialogTitle
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { useConfirm } from '@/components/ui/confirm';
import { toast } from 'sonner';
import {
  addSpaceMember,
  fetchSpaceMembers,
  removeSpaceMember,
  type SpaceInfo,
  type SpaceMember
} from '@/services/space';

/**
 * 空间成员管理：成员列表 + 添加（成员均可）+ 移除（仅创建者）。
 */
export function MemberDialog({
  open,
  space,
  onClose,
  onChanged
}: {
  open: boolean;
  space: SpaceInfo | null;
  onClose: () => void;
  onChanged: () => void;
}) {
  const confirm = useConfirm();
  const [members, setMembers] = React.useState<SpaceMember[]>([]);
  const [loading, setLoading] = React.useState(false);
  const [ucId, setUcId] = React.useState('');
  const [ucName, setUcName] = React.useState('');
  const [submitting, setSubmitting] = React.useState(false);

  const spaceId = space?.spaceId;
  const isOwner = !!space?.owner;

  const load = React.useCallback(async () => {
    if (!spaceId) return;
    setLoading(true);
    try {
      setMembers(await fetchSpaceMembers(spaceId));
    } catch (error) {
      toast.error(`加载成员失败：${error instanceof Error ? error.message : '未知错误'}`);
    } finally {
      setLoading(false);
    }
  }, [spaceId]);

  React.useEffect(() => {
    if (!open) return;
    setUcId('');
    setUcName('');
    void load();
  }, [open, load]);

  const handleAdd = async () => {
    if (!spaceId) return;
    const id = Number(ucId);
    if (!Number.isFinite(id) || id <= 0) {
      toast.error('请输入有效的用户 ID');
      return;
    }
    setSubmitting(true);
    try {
      await addSpaceMember({ spaceId, ucId: id, ucName: ucName.trim() || undefined });
      toast.success('已添加成员');
      setUcId('');
      setUcName('');
      await load();
      onChanged();
    } catch (error) {
      toast.error(`添加失败：${error instanceof Error ? error.message : '未知错误'}`);
    } finally {
      setSubmitting(false);
    }
  };

  const handleRemove = async (member: SpaceMember) => {
    if (!spaceId) return;
    const ok = await confirm({
      title: '移除成员',
      description: `移除后 ${member.ucName || member.ucId} 将无法再访问该空间，空间内资源不受影响。`,
      confirmText: '移除',
      destructive: true
    });
    if (!ok) return;
    try {
      await removeSpaceMember({ spaceId, ucId: member.ucId });
      toast.success('已移除成员');
      await load();
      onChanged();
    } catch (error) {
      toast.error(`移除失败：${error instanceof Error ? error.message : '未知错误'}`);
    }
  };

  return (
    <Dialog open={open} onOpenChange={(next) => !next && onClose()}>
      <DialogContent className="max-w-lg">
        <DialogHeader>
          <DialogTitle>成员管理 · {space?.name ?? ''}</DialogTitle>
        </DialogHeader>

        <DialogBody className="flex flex-col gap-4">
          <div className="rounded-xl border border-border/70">
            <div className="flex items-center justify-between border-b border-hairline px-3 py-2 text-[11.5px] text-muted-foreground">
              <span>共 {members.length} 人</span>
              {isOwner ? <span>创建者可移除成员</span> : <span>成员可添加人员</span>}
            </div>
            <div className="max-h-[260px] overflow-y-auto">
              {loading ? (
                <div className="flex items-center justify-center gap-2 py-10 text-[12px] text-muted-foreground">
                  <Loader2 className="h-3.5 w-3.5 animate-spin" />
                  加载中…
                </div>
              ) : members.length === 0 ? (
                <div className="py-10 text-center text-[12px] text-muted-foreground">暂无成员</div>
              ) : (
                members.map((member) => {
                  return (
                    <div
                      key={member.id}
                      className="flex items-center gap-3 border-b border-hairline px-3 py-2.5 last:border-b-0"
                    >
                      <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-muted text-[11px] font-medium text-foreground/70">
                        {(member.ucName || String(member.ucId)).slice(0, 1).toUpperCase()}
                      </span>
                      <span className="min-w-0 flex-1">
                        <span className="flex items-center gap-1.5">
                          <span className="truncate text-[12.5px] font-medium text-foreground">
                            {member.ucName || `用户 ${member.ucId}`}
                          </span>
                          {member.owner ? <Badge tone="purple">创建者</Badge> : null}
                        </span>
                        <span className="mt-0.5 block font-mono text-[10.5px] text-muted-foreground">
                          UCID {member.ucId}
                        </span>
                      </span>
                      {isOwner && !member.owner ? (
                        <button
                          type="button"
                          title="移除成员"
                          aria-label="移除成员"
                          onClick={() => void handleRemove(member)}
                          className="inline-flex h-7 w-7 items-center justify-center rounded-md text-muted-foreground/80 transition-colors hover:bg-rose-50 hover:text-rose-600"
                        >
                          <Trash2 className="h-3.5 w-3.5" />
                        </button>
                      ) : null}
                    </div>
                  );
                })
              )}
            </div>
          </div>

          <div className="rounded-xl border border-border/70 bg-muted/20 p-3">
            <p className="mb-2 flex items-center gap-1.5 text-[12px] font-medium text-foreground/80">
              <UserPlus className="h-3.5 w-3.5" />
              添加成员
            </p>
            <div className="flex flex-wrap items-end gap-2">
              <div className="w-[130px]">
                <Input
                  value={ucId}
                  inputMode="numeric"
                  placeholder="用户 ID"
                  onChange={(event) => setUcId(event.target.value.replace(/[^\d]/g, ''))}
                />
              </div>
              <div className="min-w-[140px] flex-1">
                <Input
                  value={ucName}
                  maxLength={128}
                  placeholder="昵称（用于展示）"
                  onChange={(event) => setUcName(event.target.value)}
                />
              </div>
              <Button onClick={() => void handleAdd()} disabled={submitting}>
                {submitting ? '添加中…' : '添加'}
              </Button>
            </div>
            <p className="mt-2 text-[11px] leading-relaxed text-muted-foreground">
              添加后该用户可查看并编辑空间内的全部 Agent / 工具 / 技能 / 数据源。
            </p>
          </div>
        </DialogBody>
      </DialogContent>
    </Dialog>
  );
}
