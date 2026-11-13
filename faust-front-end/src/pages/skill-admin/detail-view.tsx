import * as React from 'react';
import { ArrowLeft, Info, Layers } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { PageHeader, SectionCard } from '@/components/layout/page-header';
import { Descriptions } from '@/components/descriptions';
import { StatusBadge } from '@/components/status-badge';
import { Skeleton } from '@/components/ui/controls';
import { EmptyState } from '@/components/empty-state';
import { Markdown } from '@/components/markdown';
import { Disclosure, ExpandHint } from '@/components/disclosure';
import { fetchSkillDetail, type SkillDetail } from '@/services/agent-center';
import { useAsyncData } from '@/lib/hooks/use-paged-list';
import { SKILL_ITEM_TYPE, SKILL_TYPE_LABEL } from '@/lib/constants';
import { formatDateTime } from '@/lib/format';

const SKILL_TONE: Record<string, 'info' | 'purple' | 'warning'> = {
  CONTENT: 'info',
  SCRIPT: 'purple',
  REFERENCE: 'warning'
};

export function SkillDetailView({ id, onBack }: { id: number; onBack: () => void }) {
  const { data, loading, error, reload } = useAsyncData<SkillDetail>(() => fetchSkillDetail(id), [id]);
  const items = data?.skillItemList ?? [];

  return (
    <div className="animate-fade-in">
      <PageHeader
        title="技能详情"
        crumbs={[
          { text: 'Faust', to: '/' },
          { text: '技能', to: '/skills' },
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
        <SectionCard title="基本信息">
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            {Array.from({ length: 6 }).map((_, index) => (
              <Skeleton key={index} className="h-10" />
            ))}
          </div>
        </SectionCard>
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
                { label: 'Code', value: data.skillCode || '—' },
                { label: '状态', value: <StatusBadge status={data.status} /> },
                { label: '文件数', value: items.length },
                { label: '创建时间', value: formatDateTime(data.createTime) },
                { label: '更新时间', value: formatDateTime(data.updateTime) },
                { label: '关键词', value: data.keyWords || '—', span: 2 },
                { label: '描述', value: data.description || '—', span: 2 }
              ]}
            />
          </SectionCard>

          <SectionCard
            title={
              <>
                <Layers className="h-4 w-4 text-primary" />
                技能文件（{items.length}）
              </>
            }
            description="CONTENT 作为主文档，SCRIPT / REFERENCE 供 read_skill 按文件名读取。"
          >
            {items.length === 0 ? (
              <EmptyState title="暂无文件" description="编辑该技能以补充主文档与参考资料。" />
            ) : (
              <div className="flex flex-col gap-2.5">
                {items.map((item, index) => (
                  <Disclosure
                    key={`${item.fileName}-${index}`}
                    title={
                      <>
                        <Badge tone={SKILL_TONE[item.itemType] ?? 'muted'}>
                          {SKILL_TYPE_LABEL[item.itemType] || item.itemType}
                        </Badge>
                        <span className="truncate font-mono text-[12px] text-foreground/85">
                          {item.fileName || '—'}
                        </span>
                        <span className="shrink-0 font-mono text-[11px] text-muted-foreground/70">
                          #{item.sortOrder ?? index}
                        </span>
                      </>
                    }
                    meta={<ExpandHint />}
                  >
                    {item.itemType === SKILL_ITEM_TYPE.SCRIPT ? (
                      <pre className="max-h-[420px] overflow-auto rounded-lg bg-slate-950 p-3 text-[12px] leading-relaxed text-slate-200">
                        <code>{item.content || ''}</code>
                      </pre>
                    ) : (
                      <div className="agent-md-view">
                        <Markdown content={item.content || ''} />
                      </div>
                    )}
                  </Disclosure>
                ))}
              </div>
            )}
          </SectionCard>
        </div>
      ) : (
        <EmptyState title="未找到技能" description="该技能可能已被删除。" />
      )}
    </div>
  );
}
