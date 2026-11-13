import * as React from 'react';
import {
  BookOpen,
  Eye,
  FileText,
  MoreHorizontal,
  Pencil,
  Plus,
  Rocket,
  Search,
  StopCircle,
  Trash2
} from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { MultiSelect } from '@/components/ui/multi-select';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger
} from '@/components/ui/dropdown-menu';
import { useConfirm } from '@/components/ui/confirm';
import { PageHeader } from '@/components/layout/page-header';
import { CurrentSpaceHint } from '@/components/space-provider';
import { EmptyState } from '@/components/empty-state';
import { StatusBadge } from '@/components/status-badge';
import { CardAction, EntityCard, cardActionClass } from '@/components/entity-card';
import { CardGrid, LazyCardGrid } from '@/components/card-grid';
import {
  deleteSkill,
  fetchSkillList,
  publishSkill,
  takeOffSkill,
  type SkillRow
} from '@/services/agent-center';
import { useInfiniteList } from '@/lib/hooks/use-infinite-list';
import { SKILL_STATUS, SKILL_STATUS_OPTIONS } from '@/lib/constants';
import { formatDateTime } from '@/lib/format';
import { toast } from 'sonner';
import { SkillDetailView } from './detail-view';
import { SkillFormView } from './form-view';

type View = { name: 'list' } | { name: 'detail'; id: number } | { name: 'edit'; id?: number };

export default function SkillAdminPage() {
  const [view, setView] = React.useState<View>({ name: 'list' });
  const [keywordInput, setKeywordInput] = React.useState('');
  const [keyword, setKeyword] = React.useState('');
  const [statuses, setStatuses] = React.useState<string[]>([]);
  const confirm = useConfirm();

  const query = React.useMemo(
    () => ({
      keyword: keyword || undefined,
      status: statuses.length ? statuses : undefined
    }),
    [keyword, statuses]
  );
  const list = useInfiniteList<SkillRow>(fetchSkillList, query, { pageSize: 12 });

  const runAction = async (action: () => Promise<unknown>, successText: string, failPrefix: string) => {
    try {
      await action();
      toast.success(successText);
      list.reload();
    } catch (error) {
      toast.error(`${failPrefix}：${error instanceof Error ? error.message : '未知错误'}`);
    }
  };

  const handlePublish = async (record: SkillRow) => {
    const ok = await confirm({
      title: '发布技能',
      description: `发布后「${record.name}」即可被 Agent 关联使用，确定继续吗？`,
      confirmText: '发布'
    });
    if (ok) await runAction(() => publishSkill(record.skillId), '已发布', '发布失败');
  };

  const handleTakeOff = async (record: SkillRow) => {
    const ok = await confirm({
      title: '下架技能',
      description: `下架后「${record.name}」将不能再被新关联。若仍有 Agent 关联，后端会拒绝本次操作。`,
      confirmText: '下架'
    });
    if (ok) await runAction(() => takeOffSkill(record.skillId), '已下架', '下架失败');
  };

  const handleDelete = async (record: SkillRow) => {
    const ok = await confirm({
      title: '删除技能',
      description: `确定要删除「${record.name}」及其全部文件吗？此操作不可恢复。`,
      confirmText: '删除',
      destructive: true
    });
    if (ok) await runAction(() => deleteSkill(record.skillId), '已删除', '删除失败');
  };

  if (view.name === 'detail') {
    return <SkillDetailView id={view.id} onBack={() => setView({ name: 'list' })} />;
  }

  if (view.name === 'edit') {
    return (
      <SkillFormView
        id={view.id}
        onBack={() => setView({ name: 'list' })}
        onSaved={() => {
          setView({ name: 'list' });
          list.reset();
        }}
      />
    );
  }

  return (
    <div className="animate-fade-in">
      <PageHeader
        title="技能"
        description={
          <>
            技能独立维护、发布后供多个 Agent 复用；技能下的文件（文档 / 脚本 / 引用）
            <CurrentSpaceHint />
          </>
        }
        crumbs={[{ text: 'Faust' }, { text: '技能' }]}
        actions={
          <Button onClick={() => setView({ name: 'edit' })}>
            <Plus className="h-4 w-4" />
            新增技能
          </Button>
        }
      />

      <div className="mb-5 flex flex-wrap items-center gap-2.5 rounded-2xl border border-border/70 bg-card p-3 shadow-panel">
        <div className="relative w-[260px]">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-muted-foreground/70" />
          <Input
            value={keywordInput}
            onChange={(event) => setKeywordInput(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === 'Enter') setKeyword(keywordInput.trim());
            }}
            placeholder="搜索名称、编码"
            className="pl-9"
          />
        </div>
        <MultiSelect
          value={statuses}
          onChange={setStatuses}
          options={SKILL_STATUS_OPTIONS}
          placeholder="全部状态"
          searchPlaceholder="搜索状态"
          className="w-[200px]"
        />
        <Button onClick={() => setKeyword(keywordInput.trim())}>查询</Button>
        <Button
          variant="ghost"
          onClick={() => {
            setKeywordInput('');
            setKeyword('');
            setStatuses([]);
          }}
        >
          重置
        </Button>
        <span className="ml-auto pr-1 text-[12px] text-muted-foreground">
          共 
          <span className="tabular font-medium text-foreground/75">
            {list.loading ? '—' : list.total}
          </span> 
          个技能
        </span>
      </div>

      <LazyCardGrid
        count={list.list.length}
        total={list.total}
        loading={list.loading}
        loadingMore={list.loadingMore}
        error={list.error}
        hasMore={list.hasMore}
        onLoadMore={list.loadMore}
        onRetry={list.total > 0 ? list.reload : list.reset}
        empty={
          <EmptyState
            title="暂无技能"
            description="技能由多个文件组成，发布后可被多个 Agent 复用。"
            icon={<BookOpen className="h-5 w-5" />}
            action={
              <Button size="sm" onClick={() => setView({ name: 'edit' })}>
                <Plus className="h-4 w-4" />
                新增技能
              </Button>
            }
          />
        }
      >
        <CardGrid>
          {list.list.map((record) => {
            const published = record.status === SKILL_STATUS.PUBLISHED;
            return (
              <EntityCard
                key={record.skillId}
                title={record.name}
                seed={String(record.skillId || record.skillCode || record.name)}
                icon={<BookOpen />}
                code={record.skillCode}
                description={record.description}
                onOpen={() => setView({ name: 'detail', id: record.skillId })}
                badges={
                  <>
                    <StatusBadge status={record.status} />
                    <Badge tone="muted">
                      <FileText className="h-3 w-3" />
                      {record.itemCount ?? 0} 文件
                    </Badge>
                    {record.keyWords ? (
                      <Badge tone="outline" className="max-w-[150px] font-normal">
                        <span className="min-w-0 truncate" title={record.keyWords}>
                          {record.keyWords}
                        </span>
                      </Badge>
                    ) : null}
                  </>
                }
                meta={
                  record.createTime ? (
                    <span className="truncate tabular opacity-70" title={formatDateTime(record.createTime)}>
                      {formatDateTime(record.createTime)}
                    </span>
                  ) : null
                }
                actions={
                  <>
                    <CardAction
                      label="详情"
                      icon={<Eye />}
                      onClick={() => setView({ name: 'detail', id: record.skillId })}
                    />
                    <DropdownMenu>
                      <DropdownMenuTrigger asChild>
                        <button type="button" className={cardActionClass} title="更多操作" aria-label="更多操作">
                          <MoreHorizontal />
                        </button>
                      </DropdownMenuTrigger>
                      <DropdownMenuContent align="end">
                        {/* 已发布技能不允许编辑，需先下架 —— 后端同样会拦截 */}
                        <DropdownMenuItem
                          disabled={published}
                          onSelect={() => setView({ name: 'edit', id: record.skillId })}
                        >
                          <Pencil className="h-3.5 w-3.5" />
                          编辑
                          {published ? (
                            <span className="ml-auto pl-3 text-[11px] text-muted-foreground/70">需先下架</span>
                          ) : null}
                        </DropdownMenuItem>
                        <DropdownMenuItem disabled={published} onSelect={() => handlePublish(record)}>
                          <Rocket className="h-3.5 w-3.5" />
                          发布
                        </DropdownMenuItem>
                        <DropdownMenuItem disabled={!published} onSelect={() => handleTakeOff(record)}>
                          <StopCircle className="h-3.5 w-3.5" />
                          下架
                        </DropdownMenuItem>
                        <DropdownMenuSeparator />
                        <DropdownMenuItem
                          destructive
                          disabled={published}
                          onSelect={() => handleDelete(record)}
                        >
                          <Trash2 className="h-3.5 w-3.5" />
                          删除
                        </DropdownMenuItem>
                      </DropdownMenuContent>
                    </DropdownMenu>
                  </>
                }
              />
            );
          })}
        </CardGrid>
      </LazyCardGrid>
    </div>
  );
}
