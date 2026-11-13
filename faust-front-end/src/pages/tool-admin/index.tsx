import * as React from 'react';
import {
  Bot,
  Database,
  Eye,
  Globe,
  MoreHorizontal,
  Pencil,
  Plus,
  Rocket,
  Search,
  StopCircle,
  Trash2,
  Wrench
} from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { MultiSelect } from '@/components/ui/multi-select';
import { Select } from '@/components/ui/select';
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
import { StatusBadge, TagList, TypeBadge } from '@/components/status-badge';
import { CardAction, EntityCard, cardActionClass } from '@/components/entity-card';
import { CardGrid, LazyCardGrid } from '@/components/card-grid';
import {
  deleteTool,
  fetchToolList,
  publishTool,
  takeOffTool,
  type ToolRow
} from '@/services/agent-center';
import { useInfiniteList } from '@/lib/hooks/use-infinite-list';
import { STATUS_OPTIONS, TOOL_TYPE_FILTERS } from '@/lib/constants';
import { formatDateTime, normalizeTags } from '@/lib/format';
import { toast } from 'sonner';
import { ToolDetailView } from './detail-view';
import { ToolFormView } from './form-view';

type View = { name: 'list' } | { name: 'detail'; id: number } | { name: 'edit'; id?: number };

function isNativeTool(record: ToolRow) {
  return String(record.type || '').toUpperCase() === 'NATIVE';
}

function toolIcon(type?: string) {
  switch (String(type || '').toUpperCase()) {
    case 'DB':
      return <Database />;
    case 'SUB_AGENT':
      return <Bot />;
    case 'NATIVE':
      return <Wrench />;
    default:
      return <Globe />;
  }
}

export default function ToolAdminPage() {
  const [view, setView] = React.useState<View>({ name: 'list' });
  const [keywordInput, setKeywordInput] = React.useState('');
  const [keyword, setKeyword] = React.useState('');
  const [statuses, setStatuses] = React.useState<string[]>([]);
  const [type, setType] = React.useState<string | undefined>();
  const confirm = useConfirm();

  const query = React.useMemo(
    () => ({
      keyword: keyword || undefined,
      status: statuses.length ? statuses : undefined,
      type: type || undefined
    }),
    [keyword, statuses, type]
  );
  const list = useInfiniteList<ToolRow>(fetchToolList, query, { pageSize: 12 });

  const runAction = async (action: () => Promise<unknown>, successText: string, failPrefix: string) => {
    try {
      await action();
      toast.success(successText);
      list.reload();
    } catch (error) {
      toast.error(`${failPrefix}：${error instanceof Error ? error.message : '未知错误'}`);
    }
  };

  const handlePublish = async (record: ToolRow) => {
    const ok = await confirm({
      title: '发布工具',
      description: `发布后「${record.name}」即可被 Agent 关联使用，确定继续吗？`,
      confirmText: '发布'
    });
    if (ok) await runAction(() => publishTool(record.toolId), '已发布', '发布失败');
  };

  const handleTakeOff = async (record: ToolRow) => {
    const ok = await confirm({
      title: '下架工具',
      description: `下架后「${record.name}」将不能被新关联，已关联的 Agent 也会失去该工具。`,
      confirmText: '下架'
    });
    if (ok) await runAction(() => takeOffTool(record.toolId), '已下架', '下架失败');
  };

  const handleDelete = async (record: ToolRow) => {
    const ok = await confirm({
      title: '删除工具',
      description: `确定要删除「${record.name}」吗？此操作不可恢复。`,
      confirmText: '删除',
      destructive: true
    });
    if (ok) await runAction(() => deleteTool(record.toolId), '已删除', '删除失败');
  };

  if (view.name === 'detail') {
    return <ToolDetailView id={view.id} onBack={() => setView({ name: 'list' })} />;
  }

  if (view.name === 'edit') {
    return (
      <ToolFormView
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
        title="工具"
        description={
          <>
            HTTP / DB / 子智能体工具的统一注册与发布，Agent 通过关联工具获得执行能力。
            <CurrentSpaceHint />
          </>
        }
        crumbs={[{ text: 'Faust' }, { text: '工具' }]}
        actions={
          <Button onClick={() => setView({ name: 'edit' })}>
            <Plus className="h-4 w-4" />
            新增 Tool
          </Button>
        }
      />

      <div className="mb-5 flex flex-wrap items-center gap-2.5 rounded-2xl border border-border/70 bg-card p-3 shadow-panel">
        <div className="relative w-[240px]">
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
          options={STATUS_OPTIONS}
          placeholder="全部状态"
          searchPlaceholder="搜索状态"
          className="w-[190px]"
        />
        <Select
          value={type}
          onChange={(value) => setType(value ? String(value) : undefined)}
          options={TOOL_TYPE_FILTERS}
          placeholder="全部类型"
          allowClear
          className="w-[140px]"
        />
        <Button onClick={() => setKeyword(keywordInput.trim())}>查询</Button>
        <Button
          variant="ghost"
          onClick={() => {
            setKeywordInput('');
            setKeyword('');
            setStatuses([]);
            setType(undefined);
          }}
        >
          重置
        </Button>
        <span className="ml-auto pr-1 text-[12px] text-muted-foreground">
          共 
          <span className="tabular font-medium text-foreground/75">
            {list.loading ? '—' : list.total}
          </span> 
          个工具
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
            title="暂无工具"
            description="注册 HTTP / DB / 子智能体工具后，Agent 才能调用它们执行动作。"
            icon={<Wrench className="h-5 w-5" />}
            action={
              <Button size="sm" onClick={() => setView({ name: 'edit' })}>
                <Plus className="h-4 w-4" />
                新增 Tool
              </Button>
            }
          />
        }
      >
        <CardGrid>
          {list.list.map((record) => {
            const native = isNativeTool(record);
            const published = record.status === 'PUBLISHED';
            return (
              <EntityCard
                key={record.toolId}
                title={record.name}
                seed={String(record.toolId || record.toolCode || record.name)}
                icon={toolIcon(record.type)}
                code={record.toolCode}
                version={record.version}
                description={record.description}
                onOpen={() => setView({ name: 'detail', id: record.toolId })}
                badges={
                  <>
                    <TypeBadge type={record.type} />
                    <StatusBadge status={record.status} />
                    {record.subAgentInfoResp?.name ? (
                      <Badge tone="outline" className="max-w-[150px] font-normal">
                        <Bot className="h-3 w-3" />
                        <span className="min-w-0 truncate" title={record.subAgentInfoResp.name}>
                          {record.subAgentInfoResp.name}
                        </span>
                      </Badge>
                    ) : null}
                    <TagList tags={normalizeTags(record.tags)} max={2} />
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
                  // 内置 NATIVE 工具不可编辑 / 发布 / 删除，只保留「详情」
                  <>
                    <CardAction
                      label="详情"
                      icon={<Eye />}
                      onClick={() => setView({ name: 'detail', id: record.toolId })}
                    />
                    {native ? null : (
                      <>
                        <DropdownMenu>
                          <DropdownMenuTrigger asChild>
                            <button
                              type="button"
                              className={cardActionClass}
                              title="更多操作"
                              aria-label="更多操作"
                            >
                              <MoreHorizontal />
                            </button>
                          </DropdownMenuTrigger>
                          <DropdownMenuContent align="end">
                            <DropdownMenuItem
                              disabled={published}
                              title={published ? '已发布的版本不可编辑，请先下架' : undefined}
                              onSelect={() => setView({ name: 'edit', id: record.toolId })}
                            >
                              <Pencil className="h-3.5 w-3.5" />
                              编辑
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
                            <DropdownMenuItem destructive onSelect={() => handleDelete(record)}>
                              <Trash2 className="h-3.5 w-3.5" />
                              删除
                            </DropdownMenuItem>
                          </DropdownMenuContent>
                        </DropdownMenu>
                      </>
                    )}
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
