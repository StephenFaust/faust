import * as React from 'react';
import { ArrowLeft, Info, Loader2, Save } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { Field, FieldGroup } from '@/components/ui/field';
import { Select } from '@/components/ui/select';
import { MultiSelect } from '@/components/ui/multi-select';
import { PageHeader } from '@/components/layout/page-header';
import { Skeleton } from '@/components/ui/controls';
import {
  createTool,
  fetchDataSourceList,
  fetchDict,
  fetchSubAgentList,
  fetchToolDetail,
  updateTool,
  type AgentCard,
  type DataSourceRow,
  type ToolDetail
} from '@/services/agent-center';
import { useAsyncData } from '@/lib/hooks/use-paged-list';
import { DICT_TYPE, TOOL_TYPE, TOOL_TYPES } from '@/lib/constants';
import { validators } from '@/lib/format';
import { toast } from 'sonner';

interface FormState {
  name: string;
  code: string;
  version: string;
  type: string;
  tags: string[];
  description: string;
  schemaJson: string;
  dataSourceConfigId?: number;
  subAgentId?: number;
}

const BLANK: FormState = {
  name: '',
  code: '',
  version: '',
  type: '',
  tags: [],
  description: '',
  schemaJson: '',
  dataSourceConfigId: undefined,
  subAgentId: undefined
};

function hasValue(value: unknown): value is number | string {
  return value !== null && value !== undefined && value !== '';
}

function checkSchemaJson(raw: string): string {
  const text = String(raw ?? '').trim();
  if (!text) return '请填写 Tool Schema';
  let parsed: unknown;
  try {
    parsed = JSON.parse(text);
  } catch (error) {
    return `Tool Schema 不是合法 JSON：${error instanceof Error ? error.message : ''}`;
  }
  if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return 'Tool Schema 顶层必须是 JSON 对象';
  return '';
}

export function ToolFormView({
  id,
  onBack,
  onSaved
}: {
  id?: number;
  onBack: () => void;
  onSaved: () => void;
}) {
  const isEdit = typeof id === 'number';
  const [form, setForm] = React.useState<FormState>(BLANK);
  const [errors, setErrors] = React.useState<Partial<Record<keyof FormState | 'schema', string>>>({});
  const [saving, setSaving] = React.useState(false);
  const [populated, setPopulated] = React.useState(!isEdit);

  const detail = useAsyncData<ToolDetail>(isEdit ? () => fetchToolDetail(id as number) : null, [id]);
  const dict = useAsyncData<string[]>(() => fetchDict(DICT_TYPE.TOOL_TAGS).then((list) => list.map((item) => item.dictLabel)), []);
  const dataSources = useAsyncData<DataSourceRow[]>(() =>
    fetchDataSourceList({ current: 1, size: 50 }).then((page) => page.records ?? [])
  );
  const subAgents = useAsyncData<AgentCard[]>(() =>
    fetchSubAgentList({ current: 1, size: 50 }).then((page) => page.records ?? [])
  );

  React.useEffect(() => {
    const data = detail.data;
    if (!isEdit || !data || populated) return;
    setForm({
      name: data.name || '',
      code: data.toolCode || '',
      version: data.version || '',
      type: data.type || '',
      tags: Array.isArray(data.tags) ? data.tags : [],
      description: data.description || '',
      schemaJson: data.schemaJson || '',
      dataSourceConfigId: data.dataSourceConfig?.configId,
      subAgentId: data.subAgentInfoResp?.agentId
    });
    setPopulated(true);
  }, [detail.data, isEdit, populated]);

  // 编辑态类型以详情为准（后端不支持改类型）
  const effectiveType = (isEdit ? detail.data?.type : form.type) || '';
  const isDbType = effectiveType === TOOL_TYPE.DB;
  const isSubAgentType = effectiveType === TOOL_TYPE.SUB_AGENT;

  const setField = <K extends keyof FormState>(key: K, value: FormState[K]) => {
    setForm((prev) => ({ ...prev, [key]: value }));
    setErrors((prev) => {
      const next = { ...prev };
      delete next[key as keyof FormState];
      if (key === 'schemaJson') delete next.schema;
      return next;
    });
  };

  const validate = () => {
    const next: Partial<Record<keyof FormState | 'schema', string>> = {};
    if (!form.name.trim()) next.name = '请填写工具名称';
    if (!form.code.trim()) next.code = '请填写工具 Code';
    else if (!validators.code(form.code.trim())) next.code = 'Code 只能由字母、下划线和短横线组成，且不能以 _ 或 - 开头';
    if (!form.version.trim()) next.version = '请填写版本号';
    else if (!validators.version(form.version.trim())) next.version = '版本号格式应为 x.y.z';
    if (!form.tags.length) next.tags = '请选择标签';
    if (!form.description.trim()) next.description = '请填写描述';
    if (!isEdit && !form.type) next.type = '请选择工具类型';
    if (!isDbType && !isSubAgentType) {
      const schemaError = checkSchemaJson(form.schemaJson);
      if (schemaError) next.schema = schemaError;
    }
    if (isDbType && !hasValue(form.dataSourceConfigId)) next.dataSourceConfigId = '请选择关联数据源';
    if (isSubAgentType && !hasValue(form.subAgentId)) next.subAgentId = '请选择子 Agent';
    setErrors(next);
    if (Object.keys(next).length) {
      toast.error('请完善必填项后再保存');
      return false;
    }
    return true;
  };

  const submit = async () => {
    if (!populated) {
      toast.error('工具详情尚未加载完成，请稍后重试');
      return;
    }
    if (!validate()) return;
    setSaving(true);
    try {
      const base = {
        toolCode: form.code.trim(),
        name: form.name.trim(),
        version: form.version.trim(),
        description: form.description.trim(),
        tags: form.tags,
        ...(isSubAgentType ? {} : { schemaJson: form.schemaJson })
      };
      if (isEdit) {
        const body: Record<string, unknown> = { ...base, toolId: id };
        if (isDbType) body.dataSourceConfigId = form.dataSourceConfigId;
        if (isSubAgentType) body.subAgentId = form.subAgentId;
        await updateTool(body);
        toast.success('工具已更新');
      } else {
        const body: Record<string, unknown> = { ...base, type: form.type };
        if (isDbType) body.dataSourceConfigId = form.dataSourceConfigId;
        if (isSubAgentType) body.subAgentId = form.subAgentId;
        await createTool(body);
        toast.success('工具已创建');
      }
      onSaved();
    } catch (error) {
      toast.error(`${isEdit ? '更新' : '创建'}失败：${error instanceof Error ? error.message : '未知错误'}`);
    } finally {
      setSaving(false);
    }
  };

  const tagOptions = React.useMemo(
    () => (dict.data ?? []).map((label) => ({ label, value: label })),
    [dict.data]
  );

  const dsOptions = React.useMemo(
    () => (dataSources.data ?? []).map((item) => ({ label: `${item.dbName}（${item.dbType}）`, value: String(item.id) })),
    [dataSources.data]
  );

  const agentOptions = React.useMemo(
    () => (subAgents.data ?? []).map((item) => ({ label: `${item.name}（${item.agentCode}）`, value: String(item.agentId) })),
    [subAgents.data]
  );

  return (
    <div className="animate-fade-in">
      <PageHeader
        title={isEdit ? '编辑工具' : '新增工具'}
        crumbs={[
          { text: 'Faust', to: '/' },
          { text: '工具', to: '/tools' },
          { text: isEdit ? '编辑' : '新增' }
        ]}
        actions={
          <Button variant="outline" onClick={onBack}>
            <ArrowLeft className="h-4 w-4" />
            返回列表
          </Button>
        }
      />

      {!populated ? (
        <div className="rounded-2xl border border-border/70 bg-card p-5 shadow-panel">
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            {Array.from({ length: 6 }).map((_, index) => (
              <Skeleton key={index} className="h-16" />
            ))}
          </div>
        </div>
      ) : (
        <>
          <FieldGroup
            title={
              <>
                <Info className="h-4 w-4 text-primary" />
                基础信息
              </>
            }
          >
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <Field label="名称" required error={errors.name}>
                <Input
                  value={form.name}
                  onChange={(event) => setField('name', event.target.value)}
                  placeholder="工具名称"
                />
              </Field>
              <Field label="Code" required error={errors.code} hint={isEdit ? 'Code 创建后不可修改' : '字母开头，仅字母、下划线、短横线'}>
                <Input
                  value={form.code}
                  disabled={isEdit}
                  onChange={(event) => setField('code', event.target.value)}
                  placeholder="tool_code"
                  className="font-mono"
                />
              </Field>
              <Field label="类型" required error={errors.type}>
                {isEdit ? (
                  <Input value={effectiveType} disabled className="font-mono" />
                ) : (
                  <Select
                    value={form.type || undefined}
                    onChange={(value) =>
                      setForm((prev) => ({
                        ...prev,
                        type: value ? String(value) : '',
                        dataSourceConfigId: undefined,
                        subAgentId: undefined
                      }))
                    }
                    options={TOOL_TYPES}
                    placeholder="请选择工具类型"
                  />
                )}
              </Field>
              <Field label="版本号" required error={errors.version}>
                <Input
                  value={form.version}
                  onChange={(event) => setField('version', event.target.value)}
                  placeholder="1.0.0"
                  className="font-mono"
                />
              </Field>
              <Field label="标签" required error={errors.tags} className="sm:col-span-2">
                <MultiSelect
                  value={form.tags}
                  onChange={(value) => setField('tags', value)}
                  options={tagOptions}
                  placeholder="请选择标签"
                />
              </Field>
              <Field label="描述" required error={errors.description} className="sm:col-span-2">
                <Textarea
                  rows={3}
                  value={form.description}
                  onChange={(event) => setField('description', event.target.value)}
                  placeholder="一句话描述这个工具的能力与适用场景"
                />
              </Field>

              {isDbType ? (
                <Field label="关联数据源" required error={errors.dataSourceConfigId} className="sm:col-span-2">
                  <Select
                    value={form.dataSourceConfigId}
                    onChange={(value) => setField('dataSourceConfigId', value ? Number(value) : undefined)}
                    options={dsOptions}
                    placeholder={dataSources.loading ? '加载中…' : '请选择关联数据源'}
                    allowClear
                  />
                </Field>
              ) : null}

              {isSubAgentType ? (
                <Field label="子 Agent" required error={errors.subAgentId} className="sm:col-span-2">
                  <Select
                    value={form.subAgentId}
                    onChange={(value) => setField('subAgentId', value ? Number(value) : undefined)}
                    options={agentOptions}
                    placeholder={subAgents.loading ? '加载中…' : '请选择子 Agent'}
                    allowClear
                  />
                </Field>
              ) : null}

              {!isDbType && !isSubAgentType ? (
                <Field label="Tool Schema" required error={errors.schema} className="sm:col-span-2">
                  <Textarea
                    rows={10}
                    value={form.schemaJson}
                    onChange={(event) => setField('schemaJson', event.target.value)}
                    placeholder={'{\n  "name": "my_tool",\n  "description": "...",\n  "parameters": {}\n}'}
                    className="font-mono text-[12.5px] leading-relaxed"
                  />
                </Field>
              ) : null}
            </div>
          </FieldGroup>

          <div className="mt-4 flex items-center justify-end gap-2">
            <Button variant="outline" onClick={onBack}>
              取消
            </Button>
            <Button onClick={submit} disabled={saving}>
              {saving ? <Loader2 className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />}
              {saving ? '保存中…' : '保存'}
            </Button>
          </div>
        </>
      )}
    </div>
  );
}
