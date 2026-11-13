import * as React from 'react';
import { ArrowLeft, Info, Layers, Sparkles, Wrench } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { PageHeader, SectionCard } from '@/components/layout/page-header';
import { Descriptions } from '@/components/descriptions';
import { Skeleton } from '@/components/ui/controls';
import { EmptyState } from '@/components/empty-state';
import { StatusBadge, TagList } from '@/components/status-badge';
import { Markdown } from '@/components/markdown';
import { Disclosure, ExpandHint } from '@/components/disclosure';
import { SkillDetailDialog } from '@/components/skill-detail-dialog';
import { fetchAgentDetail, type AgentDetail } from '@/services/agent-center';
import { useAsyncData } from '@/lib/hooks/use-paged-list';
import { formatDateTime, normalizeTags } from '@/lib/format';

export function AgentDetailView({ code, onBack }: { code: string; onBack: () => void }) {
  const { data, loading, error, reload } = useAsyncData<AgentDetail>(() => fetchAgentDetail(code), [code]);

  /**
   * 技能预览弹窗：skillId 与开关分开存，
   * 关闭时只收起弹窗、保留最后一次的 skillId，避免关闭动画期间内容先被清空。
   */
  const [previewSkillId, setPreviewSkillId] = React.useState<number | null>(null);
  const [previewOpen, setPreviewOpen] = React.useState(false);

  const skills = data?.skillList ?? [];
  const tools = data?.toolList ?? [];

  return (
    <div className="animate-fade-in">
      <PageHeader
        title="Agent 详情"
        crumbs={[
          { text: 'Faust', to: '/' },
          { text: '智能体', to: '/' },
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
            {Array.from({ length: 8 }).map((_, index) => (
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
                { label: 'Code', value: data.agentCode || '—' },
                { label: '版本', value: `v${data.version || '—'}` },
                { label: '状态', value: <StatusBadge status={data.status} /> },
                { label: '分类', value: data.classification || '—' },
                { label: '使用次数', value: data.useNum ?? 0 },
                { label: '可作为子智能体', value: data.canAsSubAgent ? '是' : '否' },
                { label: '创建时间', value: formatDateTime(data.createTime) },
                { label: '描述', value: data.description || '—', span: 2 },
                {
                  label: '标签',
                  value: normalizeTags(data.tags).length ? <TagList tags={normalizeTags(data.tags)} max={10} /> : '—',
                  span: 2
                }
              ]}
            />
          </SectionCard>

          <SectionCard
            title={
              <>
                <Sparkles className="h-4 w-4 text-primary" />
                模型配置
              </>
            }
          >
            <Descriptions
              columns={3}
              items={[
                { label: '模型', value: data.modelConfig?.modelName || '—' },
                { label: 'Base URL', value: data.modelConfig?.baseUrl || '—' },
                { label: 'API Key', value: data.modelConfig ? '••••••••' : '—' },
                { label: 'Max Token', value: data.modelConfig?.maxTokens ?? '—' },
                { label: '温度', value: data.modelConfig?.temperature ?? '—' },
                { label: 'Top P', value: data.modelConfig?.topP ?? '—' },
                { label: '超时(秒)', value: data.modelConfig?.timeout ?? '—' }
              ]}
            />

            <div className="mt-4">
              <Disclosure
                title={<span className="font-medium text-foreground">系统提示词</span>}
                meta={<ExpandHint />}
              >
                {data.systemPrompt ? (
                  <div className="agent-md-view">
                    <Markdown content={data.systemPrompt} />
                  </div>
                ) : (
                  <p className="text-[12.5px] text-muted-foreground">未配置系统提示词</p>
                )}
              </Disclosure>
            </div>
          </SectionCard>

          <SectionCard
            title={
              <>
                <Layers className="h-4 w-4 text-primary" />
                关联技能（{skills.length}）
              </>
            }
            description="技能在「技能」页独立维护，Agent 只关联已发布的技能；点击「查看技能」可直接预览该技能的完整内容。"
          >
            {skills.length === 0 ? (
              <EmptyState title="未关联技能" />
            ) : (
              <div className="flex flex-col gap-2">
                {skills.map((skill) => (
                  <div
                    key={skill.skillId}
                    className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-border px-3 py-2.5"
                  >
                    <div className="flex min-w-0 flex-1 flex-col gap-0.5">
                      <div className="flex min-w-0 items-center gap-2">
                        <span className="truncate text-[13px] font-medium text-foreground">{skill.name}</span>
                        <span className="shrink-0 font-mono text-[11.5px] text-muted-foreground">
                          {skill.skillCode}
                        </span>
                      </div>
                      {skill.description ? (
                        <p className="truncate text-[12px] text-muted-foreground" title={skill.description}>
                          {skill.description}
                        </p>
                      ) : null}
                    </div>
                    <button
                      type="button"
                      onClick={() => {
                        setPreviewSkillId(skill.skillId);
                        setPreviewOpen(true);
                      }}
                      className="shrink-0 cursor-pointer text-[12px] text-primary transition-colors hover:text-primary/80"
                    >
                      查看技能
                    </button>
                  </div>
                ))}
              </div>
            )}
          </SectionCard>

          <SectionCard
            title={
              <>
                <Wrench className="h-4 w-4 text-primary" />
                关联工具（{tools.length}）
              </>
            }
          >
            {tools.length === 0 ? (
              <EmptyState title="未关联工具" />
            ) : (
              <div className="flex flex-wrap gap-1.5">
                {tools.map((tool) => (
                  <Badge key={tool.toolId} tone="outline" className="font-normal">
                    {tool.name || tool.toolCode || tool.toolId}
                  </Badge>
                ))}
              </div>
            )}
          </SectionCard>
        </div>
      ) : (
        <EmptyState title="未找到 Agent" description="该 Agent 可能已被删除。" />
      )}

      <SkillDetailDialog
        skillId={previewSkillId}
        open={previewOpen}
        onOpenChange={setPreviewOpen}
      />
    </div>
  );
}
