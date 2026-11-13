import * as React from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Building2,
  LogIn,
  MoreHorizontal,
  Pencil,
  Plus,
  Trash2,
  UserRound,
  Users,
  UserX
} from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger
} from '@/components/ui/dropdown-menu';
import { useConfirm } from '@/components/ui/confirm';
import { PageHeader } from '@/components/layout/page-header';
import { EmptyState } from '@/components/empty-state';
import { CardAction, EntityCard, cardActionClass } from '@/components/entity-card';
import { CardGrid, CardGridSkeleton } from '@/components/card-grid';
import { useSpace } from '@/components/space-provider';
import { dissolveSpace, fetchSpaceDetail, leaveSpace, type SpaceInfo } from '@/services/space';
import { formatDateTime } from '@/lib/format';
import { toast } from 'sonner';
import { SpaceFormDialog } from './space-form-dialog';
import { MemberDialog } from './member-dialog';

function resourceSummary(space: SpaceInfo) {
  return `Agent ${space.agentCount ?? 0} · 工具 ${space.toolCount ?? 0} · 技能 ${space.skillCount ?? 0} · 数据源 ${space.dataSourceCount ?? 0}`;
}

export default function SpacesPage() {
  const { spaces, current, loading, error, switchSpace, reload } = useSpace();
  const confirm = useConfirm();
  const navigate = useNavigate();

  const [form, setForm] = React.useState<{ open: boolean; mode: 'create' | 'rename'; target?: SpaceInfo | null }>({
    open: false,
    mode: 'create'
  });
  const [memberTarget, setMemberTarget] = React.useState<SpaceInfo | null>(null);

  const personal = spaces.filter((space) => space.personal);
  const owned = spaces.filter((space) => !space.personal && space.owner);
  const joined = spaces.filter((space) => !space.personal && !space.owner);

  const enter = (space: SpaceInfo) => {
    if (current?.spaceId === space.spaceId) return;
    switchSpace(space.spaceId);
    toast.success(`已切换到「${space.name}」`);
    // 进入空间后统一落到「智能体」页
    navigate('/');
  };

  const handleDissolve = async (space: SpaceInfo) => {
    let detail = space;
    try {
      detail = await fetchSpaceDetail(space.spaceId);
    } catch {
      // 详情拿不到时退回列表里的计数
    }
    const total =
      (detail.agentCount ?? 0) + (detail.toolCount ?? 0) + (detail.skillCount ?? 0) + (detail.dataSourceCount ?? 0);
    const ok = await confirm({
      title: `解散「${space.name}」`,
      description:
        total > 0
          ? `该空间还有 ${resourceSummary(detail)}，需先转移或删除这些资源才能解散。`
          : '解散后空间与成员关系将一并删除，且不可恢复。',
      confirmText: '解散',
      destructive: true
    });
    if (!ok) return;
    try {
      await dissolveSpace(space.spaceId);
      toast.success('空间已解散');
      await reload();
    } catch (e) {
      toast.error(`解散失败：${e instanceof Error ? e.message : '未知错误'}`);
    }
  };

  const handleLeave = async (space: SpaceInfo) => {
    const ok = await confirm({
      title: `退出「${space.name}」`,
      description: '退出后将无法再查看该空间内的 Agent / 工具 / 技能 / 数据源，可让创建者重新邀请你。',
      confirmText: '退出',
      destructive: true
    });
    if (!ok) return;
    try {
      await leaveSpace(space.spaceId);
      toast.success('已退出空间');
      await reload();
    } catch (e) {
      toast.error(`退出失败：${e instanceof Error ? e.message : '未知错误'}`);
    }
  };

  const renderCard = (space: SpaceInfo) => {
    const isCurrent = current?.spaceId === space.spaceId;
    return (
      <EntityCard
        key={space.spaceId}
        title={space.name}
        seed={`space-${space.spaceId}`}
        icon={space.personal ? <UserRound /> : <Building2 />}
        code={space.personal ? '仅自己可见' : `所有者 ${space.ownerName || space.ownerUcId || '—'}`}
        description={
          space.description ||
          (space.personal
            ? '你的独立工作空间，其他人和团队空间都无法访问。'
            : '团队协作空间，成员可共同维护其中的资源。')
        }
        onOpen={() => enter(space)}
        badges={
          <>
            <Badge tone={space.personal ? 'muted' : 'info'}>
              {space.personal ? '个人空间' : '团队空间'}
            </Badge>
            <Badge tone={space.owner ? 'purple' : 'outline'}>{space.owner ? '创建者' : '成员'}</Badge>
            <Badge tone="secondary">
              <Users className="h-3 w-3" />
              {space.memberCount ?? 0} 人
            </Badge>
            {isCurrent ? <Badge tone="success">当前空间</Badge> : null}
          </>
        }
        meta={
          <>
            <span className="truncate">{resourceSummary(space)}</span>
            {space.createTime ? (
              <span className="hidden shrink-0 opacity-70 2xl:inline">
                {formatDateTime(space.createTime)}
              </span>
            ) : null}
          </>
        }
        actions={
          <>
            <CardAction
              label={isCurrent ? '当前空间' : '进入该空间'}
              icon={<LogIn />}
              disabled={isCurrent}
              onClick={() => enter(space)}
            />
            {space.personal ? null : (
              <>
                <CardAction label="成员管理" icon={<Users />} onClick={() => setMemberTarget(space)} />
                <DropdownMenu>
                  <DropdownMenuTrigger asChild>
                    <button type="button" className={cardActionClass} title="更多操作" aria-label="更多操作">
                      <MoreHorizontal />
                    </button>
                  </DropdownMenuTrigger>
                  <DropdownMenuContent align="end">
                    {space.owner ? (
                      <>
                        <DropdownMenuItem onSelect={() => setForm({ open: true, mode: 'rename', target: space })}>
                          <Pencil className="h-3.5 w-3.5" />
                          重命名
                        </DropdownMenuItem>
                        <DropdownMenuItem onSelect={() => setMemberTarget(space)}>
                          <Users className="h-3.5 w-3.5" />
                          成员管理
                        </DropdownMenuItem>
                        <DropdownMenuSeparator />
                        <DropdownMenuItem destructive onSelect={() => void handleDissolve(space)}>
                          <Trash2 className="h-3.5 w-3.5" />
                          解散空间
                        </DropdownMenuItem>
                      </>
                    ) : (
                      <DropdownMenuItem destructive onSelect={() => void handleLeave(space)}>
                        <UserX className="h-3.5 w-3.5" />
                        退出空间
                      </DropdownMenuItem>
                    )}
                  </DropdownMenuContent>
                </DropdownMenu>
              </>
            )}
          </>
        }
      />
    );
  };

  return (
    <div className="animate-fade-in">
      <PageHeader
        title="空间"
        description="个人空间默认生成、仅自己可见；团队空间可添加成员，空间内的 Agent / 工具 / 技能 / 数据源共享可见。"
        crumbs={[{ text: 'Faust', to: '/' }, { text: '空间' }]}
        actions={
          <Button onClick={() => setForm({ open: true, mode: 'create' })}>
            <Plus className="h-4 w-4" />
            新建空间
          </Button>
        }
      />

      {loading && spaces.length === 0 ? (
        <CardGridSkeleton count={3} />
      ) : error && spaces.length === 0 ? (
        <EmptyState
          title="加载工作空间失败"
          description={error}
          action={
            <Button variant="outline" size="sm" onClick={() => void reload()}>
              重试
            </Button>
          }
        />
      ) : (
        <div className="flex flex-col gap-8">
          <section>
            <h2 className="mb-3 flex items-center gap-2 text-[13px] font-medium text-foreground/80">
              <UserRound className="h-3.5 w-3.5 text-muted-foreground" />
              我的个人空间
            </h2>
            <CardGrid>{personal.map(renderCard)}</CardGrid>
          </section>

          <section>
            <div className="mb-3 flex items-center justify-between">
              <h2 className="flex items-center gap-2 text-[13px] font-medium text-foreground/80">
                <Building2 className="h-3.5 w-3.5 text-muted-foreground" />
                我创建的团队空间
                <span className="text-[11.5px] font-normal text-muted-foreground">{owned.length}</span>
              </h2>
            </div>
            {owned.length ? (
              <CardGrid>{owned.map(renderCard)}</CardGrid>
            ) : (
              <EmptyState
                title="还没有团队空间"
                description="创建一个团队空间，邀请同事一起维护 Agent、工具和数据源。"
                icon={<Building2 className="h-5 w-5" />}
                action={
                  <Button size="sm" onClick={() => setForm({ open: true, mode: 'create' })}>
                    <Plus className="h-4 w-4" />
                    新建空间
                  </Button>
                }
              />
            )}
          </section>

          <section>
            <h2 className="mb-3 flex items-center gap-2 text-[13px] font-medium text-foreground/80">
              <Users className="h-3.5 w-3.5 text-muted-foreground" />
              我加入的团队空间
              <span className="text-[11.5px] font-normal text-muted-foreground">{joined.length}</span>
            </h2>
            {joined.length ? (
              <CardGrid>{joined.map(renderCard)}</CardGrid>
            ) : (
              <EmptyState
                title="还没有加入任何团队空间"
                description="让你的同事在「空间」页把你的用户 ID 添加为成员即可。"
                icon={<Users className="h-5 w-5" />}
              />
            )}
          </section>
        </div>
      )}

      <SpaceFormDialog
        open={form.open}
        mode={form.mode}
        target={form.target}
        onClose={() => setForm((prev) => ({ ...prev, open: false }))}
        onSaved={() => void reload()}
      />

      <MemberDialog
        open={!!memberTarget}
        space={memberTarget}
        onClose={() => setMemberTarget(null)}
        onChanged={() => void reload()}
      />
    </div>
  );
}
