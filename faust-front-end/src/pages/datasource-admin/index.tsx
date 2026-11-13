import * as React from 'react';
import { Database, Eye, MoreHorizontal, Pencil, Plus, Search, Trash2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
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
import { CurrentSpaceHint } from '@/components/space-provider';
import { EmptyState } from '@/components/empty-state';
import { CardAction, EntityCard, cardActionClass } from '@/components/entity-card';
import { CardGrid, LazyCardGrid } from '@/components/card-grid';
import { deleteDataSource, fetchDataSourceList, type DataSourceRow } from '@/services/agent-center';
import { useInfiniteList } from '@/lib/hooks/use-infinite-list';
import { DB_TYPES } from '@/lib/constants';
import { formatDateTime } from '@/lib/format';
import { toast } from 'sonner';
import { DataSourceDetailView } from './detail-view';
import { DataSourceFormView } from './form-view';

type View = { name: 'list' } | { name: 'detail'; id: number } | { name: 'edit'; id?: number };

function dbTypeLabel(type?: string) {
  return DB_TYPES.find((item) => item.value === type)?.label || type || '—';
}

export default function DataSourceAdminPage() {
  const [view, setView] = React.useState<View>({ name: 'list' });
  const [keywordInput, setKeywordInput] = React.useState('');
  const [keyword, setKeyword] = React.useState('');
  const confirm = useConfirm();

  const query = React.useMemo(() => ({ keyword: keyword || undefined }), [keyword]);
  const list = useInfiniteList<DataSourceRow>(fetchDataSourceList, query, { pageSize: 12 });

  const handleDelete = async (record: DataSourceRow) => {
    const ok = await confirm({
      title: '删除数据源',
      description: `确定要删除「${record.dbName}」吗？删除后关联该数据源的工具将无法执行。`,
      confirmText: '删除',
      destructive: true
    });
    if (!ok) return;
    try {
      await deleteDataSource(record.id);
      toast.success('已删除');
      list.reload();
    } catch (error) {
      toast.error(`删除失败：${error instanceof Error ? error.message : '未知错误'}`);
    }
  };

  if (view.name === 'detail') {
    return <DataSourceDetailView id={view.id} onBack={() => setView({ name: 'list' })} />;
  }

  if (view.name === 'edit') {
    return (
      <DataSourceFormView
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
        title="数据源"
        description={
          <>
            维护 Agent 可访问的数据库连接，工具通过数据源执行 SQL。
            <CurrentSpaceHint />
          </>
        }
        crumbs={[{ text: 'Faust' }, { text: '数据源' }]}
        actions={
          <Button onClick={() => setView({ name: 'edit' })}>
            <Plus className="h-4 w-4" />
            新增数据源
          </Button>
        }
      />

      <div className="mb-5 flex flex-wrap items-center gap-2.5 rounded-2xl border border-border/70 bg-card p-3 shadow-panel">
        <div className="relative w-[300px]">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-muted-foreground/70" />
          <Input
            value={keywordInput}
            onChange={(event) => setKeywordInput(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === 'Enter') setKeyword(keywordInput.trim());
            }}
            placeholder="搜索数据库类型、地址、用户名"
            className="pl-9"
          />
        </div>
        <Button onClick={() => setKeyword(keywordInput.trim())}>查询</Button>
        <Button
          variant="ghost"
          onClick={() => {
            setKeywordInput('');
            setKeyword('');
          }}
        >
          重置
        </Button>
        <span className="ml-auto pr-1 text-[12px] text-muted-foreground">
          共 
          <span className="tabular font-medium text-foreground/75">
            {list.loading ? '—' : list.total}
          </span> 
          个数据源
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
            title="暂无数据源"
            description="注册数据库连接后，DB 类型的工具即可在其上执行 SQL。"
            icon={<Database className="h-5 w-5" />}
            action={
              <Button size="sm" onClick={() => setView({ name: 'edit' })}>
                <Plus className="h-4 w-4" />
                新增数据源
              </Button>
            }
          />
        }
      >
        <CardGrid>
          {list.list.map((record) => (
            <EntityCard
              key={record.id}
              title={record.dbName}
              seed={String(record.id || record.dbName)}
              icon={<Database />}
              code={record.user}
              description={record.url}
              descriptionMono
              onOpen={() => setView({ name: 'detail', id: record.id })}
              badges={<Badge tone="info">{dbTypeLabel(record.dbType)}</Badge>}
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
                    onClick={() => setView({ name: 'detail', id: record.id })}
                  />
                  <DropdownMenu>
                    <DropdownMenuTrigger asChild>
                      <button type="button" className={cardActionClass} title="更多操作" aria-label="更多操作">
                        <MoreHorizontal />
                      </button>
                    </DropdownMenuTrigger>
                    <DropdownMenuContent align="end">
                      <DropdownMenuItem onSelect={() => setView({ name: 'edit', id: record.id })}>
                        <Pencil className="h-3.5 w-3.5" />
                        编辑
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
          ))}
        </CardGrid>
      </LazyCardGrid>
    </div>
  );
}
