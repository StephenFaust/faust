import * as React from 'react';
import { Layers } from 'lucide-react';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle
} from '@/components/ui/dialog';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/controls';
import { EmptyState } from '@/components/empty-state';
import { Descriptions } from '@/components/descriptions';
import { StatusBadge } from '@/components/status-badge';
import { Markdown } from '@/components/markdown';
import { Disclosure, ExpandHint } from '@/components/disclosure';
import { fetchSkillDetail, type SkillDetail, type SkillItem } from '@/services/agent-center';
import { useAsyncData } from '@/lib/hooks/use-paged-list';
import { SKILL_ITEM_TYPE, SKILL_TYPE_LABEL } from '@/lib/constants';
import { formatDateTime } from '@/lib/format';

const SKILL_TONE: Record<string, 'info' | 'purple' | 'warning'> = {
  CONTENT: 'info',
  SCRIPT: 'purple',
  REFERENCE: 'warning'
};

/**
 * 技能文件。
 *
 * 默认收起：一个技能可能挂十几条参考文档，全展开会把弹窗拉得很长；
 * 先看文件名再决定展开哪一条，比一屏到底更好找。
 * SCRIPT 走裸 pre，其余走 Markdown。
 */
function SkillItemBlock({ item, index }: { item: SkillItem; index: number }) {
  return (
    <Disclosure
      title={
        <>
          <Badge tone={SKILL_TONE[item.itemType] ?? 'muted'}>
            {SKILL_TYPE_LABEL[item.itemType] || item.itemType}
          </Badge>
          <span className="min-w-0 truncate font-mono text-[12px] text-foreground/85">
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
        <pre className="max-h-[560px] overflow-auto rounded-lg bg-slate-950 p-3 text-[12px] leading-relaxed text-slate-200">
          <code>{item.content || ''}</code>
        </pre>
      ) : (
        <div className="agent-md-view max-h-[640px] overflow-auto">
          <Markdown content={item.content || ''} />
        </div>
      )}
    </Disclosure>
  );
}

/** 弹窗主体：只在打开时挂载，因此详情接口按需请求，关掉即卸载 */
function SkillDetailBody({ skillId }: { skillId: number }) {
  const { data, loading, error, reload } = useAsyncData<SkillDetail>(
    () => fetchSkillDetail(skillId),
    [skillId]
  );
  const items = data?.skillItemList ?? [];

  return (
    <React.Fragment>
      {/* 头部固定、只让内容区滚动：右上角关闭按钮才不会被长文档滚走 */}
      <DialogHeader className="shrink-0 border-b border-border/70 px-8 pr-16">
        <DialogTitle className="flex flex-wrap items-center gap-2">
          <span className="min-w-0 truncate">{data?.name || '技能详情'}</span>
          {data?.skillCode ? (
            <span className="rounded-md bg-muted px-1.5 py-0.5 font-mono text-[11px] font-normal text-muted-foreground">
              {data.skillCode}
            </span>
          ) : null}
        </DialogTitle>
        <DialogDescription>
          {loading ? '加载中…' : error ? '加载失败' : data?.description || '该技能暂无描述'}
        </DialogDescription>
      </DialogHeader>

      <div className="min-h-0 flex-1 overflow-y-auto px-8 py-5">
        {loading ? (
          <div className="flex flex-col gap-3">
            <Skeleton className="h-14" />
            <Skeleton className="h-14" />
            <Skeleton className="h-40" />
          </div>
        ) : error || !data ? (
          <EmptyState
            title="加载失败"
            description={error || '该技能可能已被删除'}
            action={
              <Button variant="outline" size="sm" onClick={reload}>
                重试
              </Button>
            }
          />
        ) : (
          <div className="flex flex-col gap-4">
            <Descriptions
              columns={2}
              items={[
                { label: '状态', value: <StatusBadge status={data.status} /> },
                { label: '文件数', value: items.length },
                { label: '创建时间', value: formatDateTime(data.createTime) },
                { label: '更新时间', value: formatDateTime(data.updateTime) },
                { label: '关键词', value: data.keyWords || '—', span: 2 }
              ]}
            />

            <div className="flex items-center gap-2">
              <Layers className="h-4 w-4 text-primary" />
              <h3 className="text-[13.5px] font-semibold text-foreground">技能文件（{items.length}）</h3>
            </div>

            {items.length === 0 ? (
              <EmptyState title="暂无文件" description="该技能还没有配置主文档或参考资料。" className="py-10" />
            ) : (
              <div className="flex flex-col gap-2.5">
                {items.map((item, index) => (
                  <SkillItemBlock key={`${item.fileName}-${index}`} item={item} index={index} />
                ))}
              </div>
            )}
          </div>
        )}
      </div>
    </React.Fragment>
  );
}

/**
 * 技能详情弹窗。
 *
 * 走 `/api/skill/detail/{skillId}`，用于在 Agent 详情页直接预览已关联技能的全部内容，
 * 不必跳到技能管理页再找一遍。
 */
export function SkillDetailDialog({
  skillId,
  open,
  onOpenChange
}: {
  skillId: number | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="flex h-[92vh] max-h-[92vh] w-[96vw] max-w-[1440px] flex-col overflow-hidden">
        {skillId === null ? null : <SkillDetailBody skillId={skillId} />}
      </DialogContent>
    </Dialog>
  );
}
