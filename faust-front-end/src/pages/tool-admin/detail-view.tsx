import * as React from 'react';
import { ArrowLeft, Bot, Database, Info, Wrench } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { PageHeader, SectionCard } from '@/components/layout/page-header';
import { Descriptions } from '@/components/descriptions';
import { StatusBadge, TagList, TypeBadge } from '@/components/status-badge';
import { Skeleton } from '@/components/ui/controls';
import { EmptyState } from '@/components/empty-state';
import { fetchToolDetail, type ToolDetail } from '@/services/agent-center';
import { useAsyncData } from '@/lib/hooks/use-paged-list';
import { TOOL_TYPE } from '@/lib/constants';
import { formatDateTime, normalizeTags, stringifyJson } from '@/lib/format';

export function ToolDetailView({ id, onBack }: { id: number; onBack: () => void }) {
  const { data, loading, error, reload } = useAsyncData<ToolDetail>(() => fetchToolDetail(id), [id]);

  const type = String(data?.type || '').toUpperCase();
  const hasOwnSection = type === TOOL_TYPE.DB || type === TOOL_TYPE.SUB_AGENT || type === TOOL_TYPE.NATIVE;
  const nativeTools = data?.nativeToolsListResp ?? [];
  const subAgent = data?.subAgentInfoResp;

  return (
    <div className="animate-fade-in">
      <PageHeader
        title="工具详情"
        crumbs={[
          { text: 'Faust', to: '/' },
          { text: '工具', to: '/tools' },
          { text: '详情' }
        ]}
        actions={
          <Button variant="outline" onClick={onBack}>
            <ArrowLeft className="h-4 w-4" />
            返回列表
          </Button>
        }
      />

      {loading ? (
        <div className="flex flex-col gap-4">
          <SectionCard title="基本信息">
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
              {Array.from({ length: 6 }).map((_, index) => (
                <Skeleton key={index} className="h-10" />
              ))}
            </div>
          </SectionCard>
        </div>
      ) : error ? (
        <EmptyState
          title="加载失败"
          description={error}
          action={
            <Button variant="outline" size="sm" onClick={reload}>
              重试
            </Button>
          }
        />
      ) : data ? (
        <div className="flex flex-col gap-4">
          <SectionCard
            title={
              <>
                <Info className="h-4 w-4 text-primary" />
                基本信息
              </>
            }
          >
            <Descriptions
              columns={2}
              items={[
                { label: '名称', value: data.name || '—' },
                { label: 'Code', value: data.toolCode || '—' },
                { label: '版本', value: `v${data.version || '—'}` },
                {
                  label: '类型',
                  value: <TypeBadge type={data.type} />
                },
                { label: '状态', value: <StatusBadge status={data.status} /> },
                { label: '创建时间', value: formatDateTime(data.createTime) },
                { label: '描述', value: data.description || '—', span: 2 },
                {
                  label: '标签',
                  value:
                    normalizeTags(data.tags).length > 0 ? (
                      <TagList tags={normalizeTags(data.tags)} max={10} />
                    ) : (
                      '—'
                    ),
                  span: 2
                }
              ]}
            />
          </SectionCard>

          {type === TOOL_TYPE.DB ? (
            <SectionCard
              title={
                <>
                  <Database className="h-4 w-4 text-primary" />
                  关联数据源
                </>
              }
            >
              <Descriptions
                columns={2}
                items={[
                  { label: '数据库名称', value: data.dataSourceConfig?.dbName || '—' },
                  { label: '数据库类型', value: data.dataSourceConfig?.dbType || '—' },
                  { label: '连接地址', value: data.dataSourceConfig?.url || '—', span: 2 }
                ]}
              />
            </SectionCard>
          ) : null}

          {type === TOOL_TYPE.SUB_AGENT ? (
            <SectionCard
              title={
                <>
                  <Bot className="h-4 w-4 text-primary" />
                  子智能体
                </>
              }
            >
              <Descriptions
                columns={2}
                items={[
                  { label: '名称', value: subAgent?.name || '—' },
                  { label: 'Code', value: subAgent?.agentCode || '—' },
                  { label: '版本', value: subAgent?.version ? `v${subAgent.version}` : '—' },
                  { label: '描述', value: subAgent?.desc || '—', span: 2 }
                ]}
              />
            </SectionCard>
          ) : null}

          {type === TOOL_TYPE.NATIVE ? (
            <SectionCard
              title={
                <>
                  <Wrench className="h-4 w-4 text-primary" />
                  工具集（{nativeTools.length}）
                </>
              }
              description="原生工具由 Agent 运行时提供，仅可查看，不支持编辑 / 发布 / 下架 / 删除。"
            >
              {nativeTools.length === 0 ? (
                <EmptyState title="暂无工具" />
              ) : (
                <div className="flex flex-col gap-2.5">
                  {nativeTools.map((tool, index) => (
                    <div key={tool.toolName || index} className="rounded-lg border border-border bg-muted/20 p-3">
                      <p className="font-mono text-[12.5px] font-medium text-foreground">{tool.toolName || '—'}</p>
                      <p className="mt-1 whitespace-pre-wrap break-words text-[12px] leading-relaxed text-muted-foreground">
                        {tool.toolDesc || '—'}
                      </p>
                    </div>
                  ))}
                </div>
              )}
            </SectionCard>
          ) : null}

          {!hasOwnSection ? (
            <SectionCard
              title={
                <>
                  <Wrench className="h-4 w-4 text-primary" />
                  Tool Schema
                </>
              }
            >
              <pre className="max-h-[520px] overflow-auto rounded-lg bg-slate-950 p-4 text-[12.5px] leading-relaxed text-slate-200">
                <code>{stringifyJson(data.schemaJson) || '—'}</code>
              </pre>
            </SectionCard>
          ) : null}
        </div>
      ) : (
        <EmptyState title="未找到工具" description="该工具可能已被删除。" />
      )}
    </div>
  );
}
