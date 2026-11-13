import * as React from 'react';
import { useNavigate } from 'react-router-dom';
import {
  ArrowUpRight,
  Bot,
  Eye,
  MessageSquare,
  MoreHorizontal,
  Pencil,
  Plus,
  Rocket,
  Search,
  Settings2,
  StopCircle,
  Trash2,
  X
} from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
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
import { StatusBadge, TagList } from '@/components/status-badge';
import { CardAction, EntityCard, cardActionClass } from '@/components/entity-card';
import { CardGrid, LazyCardGrid } from '@/components/card-grid';
import {
  deleteAgent,
  fetchAgentList,
  fetchDict,
  publishAgent,
  takeOffAgent,
  type AgentRow
} from '@/services/agent-center';
import { useInfiniteList } from '@/lib/hooks/use-infinite-list';
import { AGENT_STATUS_OPTIONS, DICT_TYPE, STATUS_MAP } from '@/lib/constants';
import { formatDateTime, normalizeTags } from '@/lib/format';
import { cn } from '@/lib/utils';
import { toast } from 'sonner';
import { AgentDetailView } from './detail-view';
import { AgentFormView } from './form-view';
import { GlobalEnvDialog } from './global-env-dialog';

type View = { name: 'list' } | { name: 'detail'; code: string } | { name: 'edit'; code?: string };

export default function AgentsPage() {
  const navigate = useNavigate();
  const confirm = useConfirm();

  const [view, setView] = React.useState<View>({ name: 'list' });
  const [keywordInput, setKeywordInput] = React.useState('');
  const [keyword, setKeyword] = React.useState('');
  const [classification, setClassification] = React.useState<string | undefined>();
  const [statuses, setStatuses] = React.useState<string[]>([]);
  const [tags, setTags] = React.useState<string[]>([]);
  const [categories, setCategories] = React.useState<string[]>([]);
  const [tagOptions, setTagOptions] = React.useState<string[]>([]);
  // 环境变量是「Agent × 用户」维度的配置，入口挂在卡片菜单上，需记录目标 Agent
  const [envTarget, setEnvTarget] = React.useState<string | null>(null);

  const query = React.useMemo(
    () => ({
      keyword: keyword || undefined,
      classification: classification || undefined,
      status: statuses.length ? statuses : undefined,
      tags: tags.length ? tags : undefined
    }),
    [keyword, classification, statuses, tags]
  );
  const list = useInfiniteList<AgentRow>(fetchAgentList, query, { pageSize: 12 });

  React.useEffect(() => {
    fetchDict(DICT_TYPE.CLASSIFICATION)
      .then((items) => setCategories([...new Set(items.map((item) => item.dictLabel).filter(Boolean))]))
      .catch(() => undefined);
    fetchDict(DICT_TYPE.AGENT_TAGS)
      .then((items) => setTagOptions([...new Set(items.map((item) => item.dictLabel).filter(Boolean))]))
      .catch(() => undefined);
  }, []);

  const runAction = async (action: () => Promise<unknown>, successText: string, failPrefix: string) => {
    try {
      await action();
      toast.success(successText);
      list.reload();
    } catch (error) {
      toast.error(`${failPrefix}：${error instanceof Error ? error.message : '未知错误'}`);
    }
  };

  const handlePublish = async (record: AgentRow) => {
    const ok = await confirm({
      title: '发布 Agent',
      description: `发布后「${record.name}」即可在当前空间内发起对话，空间成员都可见。`,
      confirmText: '发布'
    });
    if (ok) await runAction(() => publishAgent(record.agentId), '已发布', '发布失败');
  };

  const handleTakeOff = async (record: AgentRow) => {
    const ok = await confirm({
      title: '下架 Agent',
      description: `下架后「${record.name}」将无法发起新对话，已有会话不受影响。`,
      confirmText: '下架'
    });
    if (ok) await runAction(() => takeOffAgent(record.agentId), '已下架', '下架失败');
  };

  const handleDelete = async (record: AgentRow) => {
    const ok = await confirm({
      title: '删除 Agent',
      description: `确定要删除「${record.name}」吗？关联的模型配置、SKILL、工具绑定会一并删除，且不可恢复。`,
      confirmText: '删除',
      destructive: true
    });
    if (ok) await runAction(() => deleteAgent(record.agentId), '已删除', '删除失败');
  };

  const toggleTag = (tag: string) => {
    setTags((prev) => (prev.includes(tag) ? prev.filter((item) => item !== tag) : [...prev, tag]));
  };

  const resetFilters = () => {
    setKeywordInput('');
    setKeyword('');
    setClassification(undefined);
    setStatuses([]);
    setTags([]);
  };

  const filtersActive = Boolean(keyword || classification || statuses.length || tags.length);

  if (view.name === 'detail') {
    return <AgentDetailView code={view.code} onBack={() => setView({ name: 'list' })} />;
  }

  if (view.name === 'edit') {
    return (
      <AgentFormView
        code={view.code}
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
        title="智能体"
        description={
          <>
            浏览全部智能体：已发布的可以直接进入对话，未发布的在这里继续配置、发布。
            <CurrentSpaceHint />
          </>
        }
        crumbs={[{ text: 'Faust' }, { text: '智能体' }]}
        actions={
          <Button onClick={() => setView({ name: 'edit' })}>
            <Plus className="h-4 w-4" />
            新增 Agent
          </Button>
        }
      />

      {/* 筛选区 */}
      <div className="mb-5 overflow-hidden rounded-2xl border border-border/70 bg-card/80 shadow-panel backdrop-blur-sm">
        <div className="flex flex-wrap items-center gap-2.5 px-4 py-3">
          <div className="relative w-[280px]">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-muted-foreground/60" />
            <Input
              value={keywordInput}
              onChange={(event) => setKeywordInput(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === 'Enter') setKeyword(keywordInput.trim());
              }}
              placeholder="搜索 Agent 名称 / 编码"
              className="pl-9"
            />
          </div>
          <Select
            value={classification}
            onChange={(value) => setClassification(value ? String(value) : undefined)}
            placeholder="全部分类"
            allowClear
            className="w-[160px]"
            options={categories.map((item) => ({ label: item, value: item }))}
          />
          <MultiSelect
            value={statuses}
            onChange={setStatuses}
            options={AGENT_STATUS_OPTIONS}
            placeholder="全部状态"
            searchPlaceholder="搜索状态"
            className="w-[190px]"
          />
          <Button onClick={() => setKeyword(keywordInput.trim())}>查询</Button>
          {filtersActive ? (
            <Button variant="ghost" className="gap-1.5" onClick={resetFilters}>
              <X className="h-3.5 w-3.5" />
              清除筛选
            </Button>
          ) : null}
          <span className="ml-auto pr-1 text-[12px] text-muted-foreground">
            共{' '}
            <span className="tabular font-medium text-foreground/75">
              {list.loading ? '—' : list.total}
            </span>{' '}
            个 Agent
          </span>
        </div>

        {tagOptions.length > 0 ? (
          <div className="flex flex-wrap items-center gap-1.5 border-t border-hairline px-4 py-2.5">
            <span className="mr-1 text-[11px] font-medium tracking-[0.04em] text-muted-foreground/70">
              标签
            </span>
            {tagOptions.map((tag) => {
              const active = tags.includes(tag);
              return (
                <button
                  key={tag}
                  type="button"
                  onClick={() => toggleTag(tag)}
                  className={cn(
                    'rounded-full px-2.5 py-[3px] text-[11.5px] font-medium transition-all duration-200 ease-expo ring-1 ring-inset',
                    active
                      ? 'bg-primary/10 text-primary ring-primary/25'
                      : 'text-muted-foreground ring-border/70 hover:bg-muted/60 hover:text-foreground hover:ring-border'
                  )}
                >
                  {tag}
                </button>
              );
            })}
          </div>
        ) : null}
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
            title={filtersActive ? '没有匹配的智能体' : '还没有智能体'}
            description={
              filtersActive
                ? '换个关键词或清掉筛选条件再试试。'
                : '创建一个 Agent，配置模型、技能与工具后就能发布对话。'
            }
            icon={<Bot className="h-5 w-5" />}
            action={
              filtersActive ? (
                <Button variant="outline" size="sm" onClick={resetFilters}>
                  清除筛选
                </Button>
              ) : (
                <Button size="sm" onClick={() => setView({ name: 'edit' })}>
                  <Plus className="h-4 w-4" />
                  新增 Agent
                </Button>
              )
            }
          />
        }
      >
        <CardGrid>
          {list.list.map((record) => {
            const published = record.status === 'PUBLISHED';
            const statusLabel = STATUS_MAP[String(record.status || '').toUpperCase()]?.text || '未发布';
            return (
              <EntityCard
                key={record.agentId}
                title={record.name}
                seed={String(record.agentId || record.agentCode || record.name)}
                icon={<Bot />}
                code={record.agentCode}
                version={record.version}
                description={record.description}
                onOpen={() => setView({ name: 'detail', code: record.agentCode })}
                badges={
                  <>
                    <StatusBadge status={record.status} />
                    {record.classification ? <Badge tone="info">{record.classification}</Badge> : null}
                    {record.canAsSubAgent ? <Badge tone="purple">子智能体</Badge> : null}
                    <TagList tags={normalizeTags(record.tags)} max={2} />
                  </>
                }
                meta={
                  <>
                    <span className="inline-flex shrink-0 items-center gap-1">
                      <MessageSquare className="h-3.5 w-3.5 opacity-60" />
                      <span className="tabular">{record.useNum || 0}</span>
                    </span>
                    {record.createTime ? (
                      <span
                        className="hidden truncate tabular opacity-70 2xl:inline"
                        title={formatDateTime(record.createTime)}
                      >
                        {formatDateTime(record.createTime)}
                      </span>
                    ) : null}
                  </>
                }
                actions={
                  <>
                    <Button
                      size="sm"
                      className="mr-0.5 h-7 gap-1 px-2.5 text-[12px]"
                      disabled={!published}
                      title={published ? '进入对话' : `${statusLabel}的 Agent 不能发起对话`}
                      onClick={() => navigate(`/chat/${record.agentCode}`)}
                    >
                      对话
                      <ArrowUpRight className="h-3 w-3 transition-transform duration-300 group-hover:-translate-y-0.5 group-hover:translate-x-0.5" />
                    </Button>
                    <CardAction
                      label="详情"
                      icon={<Eye />}
                      onClick={() => setView({ name: 'detail', code: record.agentCode })}
                    />
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
                          onSelect={() => setView({ name: 'edit', code: record.agentCode })}
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
                        <DropdownMenuItem onSelect={() => setEnvTarget(record.agentCode)}>
                          <Settings2 className="h-3.5 w-3.5" />
                          环境变量
                        </DropdownMenuItem>
                        <DropdownMenuSeparator />
                        <DropdownMenuItem destructive onSelect={() => handleDelete(record)}>
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
      <GlobalEnvDialog
        open={Boolean(envTarget)}
        agentCode={envTarget ?? ''}
        onClose={() => setEnvTarget(null)}
      />
    </div>
  );
}
