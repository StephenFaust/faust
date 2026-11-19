import * as React from 'react';
import { ArrowLeft, BookOpen, ChevronLeft, ChevronRight, Info, Layers, Loader2, Save, Sparkles } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { Field } from '@/components/ui/field';
import { Select } from '@/components/ui/select';
import { MultiSelect } from '@/components/ui/multi-select';
import { Switch } from '@/components/ui/controls';
import { Steps } from '@/components/ui/steps';
import { PageHeader } from '@/components/layout/page-header';
import { Skeleton } from '@/components/ui/controls';
import { useConfirm } from '@/components/ui/confirm';
import {
  createAgent,
  fetchAgentDetail,
  fetchDict,
  fetchSkillPublishedList,
  fetchToolPublishedList,
  updateAgent,
  type AgentDetail,
  type SkillRow,
  type ToolRow
} from '@/services/agent-center';
import { useAsyncData } from '@/lib/hooks/use-paged-list';
import { DICT_TYPE } from '@/lib/constants';
import { validators } from '@/lib/format';
import { toast } from 'sonner';

const STEPS = ['基本信息', '模型配置', '技能关联', '工具关联'];

interface ModelState {
  baseUrl: string;
  apiKey: string;
  modelName: string;
  maxTokens: string;
  temperature: string;
  topP: string;
  timeout: string;
}

interface FormState {
  name: string;
  code: string;
  version: string;
  classification: string;
  tags: string[];
  description: string;
  prompt: string;
  canAsSubAgent: boolean;
  model: ModelState;
  /** 已发布技能的 skillId（字符串便于 MultiSelect 使用） */
  skills: string[];
  tools: string[];
}

const BLANK_MODEL: ModelState = {
  baseUrl: '',
  apiKey: '',
  modelName: '',
  maxTokens: '',
  temperature: '',
  topP: '',
  timeout: ''
};

const BLANK: FormState = {
  name: '',
  code: '',
  version: '',
  classification: '',
  tags: [],
  description: '',
  prompt: '',
  canAsSubAgent: false,
  model: BLANK_MODEL,
  skills: [],
  tools: []
};

type ErrorMap = Record<string, string>;

function numErr(value: string, options: { min?: number; max?: number; int?: boolean } = {}): string {
  if (value === null || value === undefined || String(value).trim() === '') return '请填写';
  const num = Number(value);
  if (!Number.isFinite(num)) return '必须是数字';
  if (options.int && !Number.isInteger(num)) return '必须是整数';
  if (options.min !== undefined && num < options.min) return `不能小于 ${options.min}`;
  if (options.max !== undefined && num > options.max) return `不能大于 ${options.max}`;
  return '';
}

export function AgentFormView({
  code,
  onBack,
  onSaved
}: {
  code?: string;
  onBack: () => void;
  onSaved: () => void;
}) {
  const isEdit = Boolean(code);
  const confirm = useConfirm();

  const [form, setForm] = React.useState<FormState>(BLANK);
  const [step, setStep] = React.useState(1);
  const [errors, setErrors] = React.useState<ErrorMap>({});
  const [skillEnabled, setSkillEnabled] = React.useState(false);
  const [toolEnabled, setToolEnabled] = React.useState(false);
  const [apiKeyTouched, setApiKeyTouched] = React.useState(false);
  const [saving, setSaving] = React.useState(false);
  const [populated, setPopulated] = React.useState(!isEdit);

  const detail = useAsyncData<AgentDetail>(isEdit ? () => fetchAgentDetail(code as string) : null, [code]);
  const classifications = useAsyncData<string[]>(
    () => fetchDict(DICT_TYPE.CLASSIFICATION).then((list) => list.map((item) => item.dictLabel)),
    []
  );
  const agentTags = useAsyncData<string[]>(
    () => fetchDict(DICT_TYPE.AGENT_TAGS).then((list) => list.map((item) => item.dictLabel)),
    []
  );
  // 「可作为子智能体」= true 时后端会排除「子智能体」类工具，避免循环套娃
  const publishedTools = useAsyncData<ToolRow[]>(
    () =>
      fetchToolPublishedList({
        current: 1,
        size: 50,
        ...(form.canAsSubAgent ? { excludeAgentTool: true } : {})
      }).then((page) => page.records ?? []),
    [form.canAsSubAgent]
  );
  // 技能已在「技能管理」中独立维护，Agent 只负责关联已发布的技能
  const publishedSkills = useAsyncData<SkillRow[]>(() =>
    fetchSkillPublishedList({ current: 1, size: 200 }).then((page) => page.records ?? [])
  );

  React.useEffect(() => {
    const data = detail.data;
    if (!isEdit || !data || populated) return;
    setForm({
      name: data.name || '',
      code: data.agentCode || '',
      version: data.version || '',
      classification: data.classification || '',
      tags: Array.isArray(data.tags) ? data.tags : [],
      description: data.description || '',
      prompt: data.systemPrompt || '',
      canAsSubAgent: Boolean(data.canAsSubAgent),
      model: {
        baseUrl: data.modelConfig?.baseUrl || '',
        apiKey: '',
        modelName: data.modelConfig?.modelName || '',
        maxTokens: data.modelConfig?.maxTokens != null ? String(data.modelConfig.maxTokens) : '',
        temperature: data.modelConfig?.temperature != null ? String(data.modelConfig.temperature) : '',
        topP: data.modelConfig?.topP != null ? String(data.modelConfig.topP) : '',
        timeout: data.modelConfig?.timeout != null ? String(data.modelConfig.timeout) : ''
      },
      skills: (data.skillList ?? []).map((skill) => String(skill.skillId)),
      tools: (data.toolList ?? []).map((tool) => String(tool.toolId))
    });
    setSkillEnabled((data.skillList ?? []).length > 0);
    setToolEnabled((data.toolList ?? []).length > 0);
    setApiKeyTouched(false);
    setPopulated(true);
  }, [detail.data, isEdit, populated]);

  const apiKeyNeeded = !isEdit || apiKeyTouched;

  const patch = (next: Partial<FormState>) => {
    setForm((prev) => ({ ...prev, ...next }));
    setErrors((prev) => {
      const copy = { ...prev };
      Object.keys(next).forEach((key) => delete copy[key]);
      return copy;
    });
  };

  const patchModel = (next: Partial<ModelState>) => {
    setForm((prev) => ({ ...prev, model: { ...prev.model, ...next } }));
    setErrors((prev) => {
      const copy = { ...prev };
      Object.keys(next).forEach((key) => delete copy[key]);
      return copy;
    });
  };

  const toggleCanAsSubAgent = async (checked: boolean) => {
    if (!checked) {
      patch({ canAsSubAgent: false });
      return;
    }
    if (form.tools.length > 0) {
      const ok = await confirm({
        title: '开启「可作为子智能体」？',
        description: '开启后不能再关联「子智能体」类工具，当前已选工具会被清空，需重新绑定。',
        confirmText: '确认开启'
      });
      if (!ok) return;
    }
    setForm((prev) => ({ ...prev, canAsSubAgent: true, tools: [] }));
    setToolEnabled(false);
  };

  // ---------------- 校验 ----------------
  const collectErrors = (targetStep: number, out: ErrorMap) => {
    if (targetStep === 1) {
      if (!form.name.trim()) out.name = '请填写名称';
      if (!form.code.trim()) out.code = '请填写 Code';
      else if (!validators.code(form.code.trim())) out.code = 'Code 只能由字母、下划线和短横线组成，且不能以 _ 或 - 开头';
      if (!form.version.trim()) out.version = '请填写版本号';
      else if (!validators.version(form.version.trim())) out.version = '版本号格式应为 x.y.z';
      if (!form.classification) out.classification = '请选择分类';
      if (!form.tags.length) out.tags = '请选择标签';
      if (!form.description.trim()) out.description = '请填写描述';
      if (!form.prompt.trim()) out.prompt = '请填写系统提示词';
    }

    if (targetStep === 2) {
      const model = form.model;
      if (!model.baseUrl.trim()) out.baseUrl = '请填写 Base URL';
      if (apiKeyNeeded && !model.apiKey.trim()) out.apiKey = '请填写 API Key';
      if (!model.modelName.trim()) out.modelName = '请填写模型名称';
      const maxTokens = numErr(model.maxTokens, { int: true, min: 1 });
      if (maxTokens) out.maxTokens = `Max Token ${maxTokens}`;
      const temperature = numErr(model.temperature, { min: 0, max: 2 });
      if (temperature) out.temperature = `温度 ${temperature}`;
      const topP = numErr(model.topP, { min: 0, max: 1 });
      if (topP) out.topP = `Top P ${topP}`;
      const timeout = numErr(model.timeout, { int: true, min: 1 });
      if (timeout) out.timeout = `超时 ${timeout}`;
    }

    if (targetStep === 3 && skillEnabled && !form.skills.length) {
      out.skills = '请至少关联一个技能';
    }

    if (targetStep === 4 && toolEnabled && !form.tools.length) {
      out.tools = '请至少关联一个工具';
    }
  };

  const validateStep = (targetStep: number) => {
    const next: ErrorMap = {};
    collectErrors(targetStep, next);
    setErrors(next);
    return Object.keys(next).length === 0;
  };

  const nextStep = () => {
    if (!validateStep(step)) {
      toast.error('请完善当前步骤的必填项');
      return;
    }
    setStep((prev) => Math.min(4, prev + 1));
  };

  const submit = async () => {
    if (!populated) {
      toast.error('Agent 详情尚未加载完成，请稍后重试');
      return;
    }
    const all: ErrorMap = {};
    for (let s = 1; s <= 4; s += 1) collectErrors(s, all);
    setErrors(all);
    if (Object.keys(all).length) {
      const firstBad = [1, 2, 3, 4].find((s) => {
        const stepErrors: ErrorMap = {};
        collectErrors(s, stepErrors);
        return Object.keys(stepErrors).length > 0;
      });
      if (firstBad) setStep(firstBad);
      toast.error('请完善必填项后再保存');
      return;
    }

    setSaving(true);
    try {
      const model = form.model;
      const modelConfig: Record<string, unknown> = {
        baseUrl: model.baseUrl || null,
        modelName: model.modelName || null,
        maxTokens: model.maxTokens === '' ? null : Number.parseInt(model.maxTokens, 10),
        temperature: model.temperature === '' ? null : Number.parseFloat(model.temperature),
        topP: model.topP === '' ? null : Number.parseFloat(model.topP),
        timeout: model.timeout === '' ? null : Number.parseInt(model.timeout, 10)
      };
      if (apiKeyNeeded) modelConfig.apiKey = model.apiKey || null;

      const body: Record<string, unknown> = {
        agentCode: form.code.trim(),
        name: form.name.trim(),
        version: form.version.trim(),
        description: form.description.trim(),
        classification: form.classification || null,
        tags: form.tags,
        canAsSubAgent: form.canAsSubAgent,
        promptReq: form.prompt ? { content: form.prompt } : null,
        skillIdList: skillEnabled && form.skills.length ? form.skills.map((skillId) => Number(skillId)) : null,
        toolIdList: toolEnabled && form.tools.length ? form.tools.map((id) => Number(id)) : null
      };

      if (isEdit) {
        body.agentId = detail.data?.agentId;
        body.modelCreateConfig = modelConfig;
        await updateAgent(body);
        toast.success('Agent 已更新');
      } else {
        body.modelCreateConfigReq = modelConfig;
        await createAgent(body);
        toast.success('Agent 已创建');
      }
      onSaved();
    } catch (error) {
      toast.error(`${isEdit ? '更新' : '创建'}失败：${error instanceof Error ? error.message : '未知错误'}`);
    } finally {
      setSaving(false);
    }
  };

  const classificationOptions = (classifications.data ?? []).map((label) => ({ label, value: label }));
  const tagOptions = (agentTags.data ?? []).map((label) => ({ label, value: label }));
  const toolOptions = (publishedTools.data ?? []).map((tool) => ({
    label: `${tool.name}（${tool.toolCode}）`,
    value: String(tool.toolId)
  }));
  const skillOptions = (publishedSkills.data ?? []).map((skill) => ({
    label: `${skill.name}（${skill.skillCode}）`,
    value: String(skill.skillId)
  }));

  if (!populated) {
    return (
      <div className="animate-fade-in">
        <PageHeader title="编辑 Agent" crumbs={[{ text: 'Faust', to: '/' }, { text: '智能体', to: '/' }]} />
        <div className="rounded-2xl border border-border/70 bg-card p-5 shadow-panel">
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            {Array.from({ length: 6 }).map((_, index) => (
              <Skeleton key={index} className="h-16" />
            ))}
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="animate-fade-in">
      <PageHeader
        title={isEdit ? '编辑 Agent' : '新增 Agent'}
        description="按四步完成配置：基本信息 → 模型 → SKILL → 工具。"
        crumbs={[
          { text: 'Faust', to: '/' },
          { text: '智能体', to: '/' },
          { text: isEdit ? '编辑' : '新增' }
        ]}
        actions={
          <Button variant="outline" onClick={onBack}>
            <ArrowLeft className="h-4 w-4" />
            返回列表
          </Button>
        }
      />

      <div className="mb-4 rounded-2xl border border-border/70 bg-card px-5 py-4 shadow-panel">
        <Steps steps={STEPS} current={step} onChange={(next) => setStep(next)} />
      </div>

      <div className="flex flex-col gap-4">
        {step === 1 ? (
          <section className="rounded-2xl border border-border/70 bg-card shadow-panel">
            <header className="flex items-center gap-2 border-b border-border/70 px-5 py-3">
              <Info className="h-4 w-4 text-primary" />
              <h2 className="text-[13.5px] font-semibold text-foreground">基本信息</h2>
            </header>
            <div className="grid grid-cols-1 gap-4 px-5 py-4 sm:grid-cols-2">
              <Field label="名称" required error={errors.name}>
                <Input value={form.name} onChange={(event) => patch({ name: event.target.value })} placeholder="Agent 名称" />
              </Field>
              <Field label="Code" required error={errors.code} hint={isEdit ? 'Code 创建后不可修改' : '字母开头，仅字母、下划线、短横线'}>
                <Input
                  value={form.code}
                  disabled={isEdit}
                  onChange={(event) => patch({ code: event.target.value })}
                  placeholder="agent_code"
                  className="font-mono"
                />
              </Field>
              <Field
                label="版本号"
                required
                error={errors.version}
                hint={isEdit ? '编辑时可不修改' : '格式 x.y.z'}
              >
                <Input
                  value={form.version}
                  onChange={(event) => patch({ version: event.target.value })}
                  placeholder="1.0.0"
                  className="font-mono"
                />
              </Field>
              <Field label="分类" required error={errors.classification}>
                <Select
                  value={form.classification || undefined}
                  onChange={(value) => patch({ classification: value ? String(value) : '' })}
                  options={classificationOptions}
                  placeholder="请选择分类"
                />
              </Field>
              <Field label="标签" required error={errors.tags} className="sm:col-span-2">
                <MultiSelect
                  value={form.tags}
                  onChange={(value) => patch({ tags: value })}
                  options={tagOptions}
                  placeholder="请选择标签"
                />
              </Field>
              <div className="flex items-center justify-between rounded-lg border border-border bg-muted/25 px-4 py-3 sm:col-span-2">
                <div className="flex flex-col gap-0.5">
                  <span className="text-[13px] font-medium text-foreground">可作为子智能体</span>
                  <span className="text-[11.5px] text-muted-foreground">
                    开启后可被其他 Agent 作为 SUB_AGENT 工具关联，同时不能再关联子智能体类工具
                  </span>
                </div>
                <Switch checked={form.canAsSubAgent} onCheckedChange={toggleCanAsSubAgent} />
              </div>
              <Field label="描述" required error={errors.description} className="sm:col-span-2">
                <Textarea
                  rows={3}
                  value={form.description}
                  onChange={(event) => patch({ description: event.target.value })}
                  placeholder="一句话描述这个 Agent 的用途"
                />
              </Field>
              <Field label="系统提示词" required error={errors.prompt} className="sm:col-span-2">
                <Textarea
                  rows={7}
                  value={form.prompt}
                  onChange={(event) => patch({ prompt: event.target.value })}
                  placeholder="你是一个…"
                  className="font-mono text-[12.5px] leading-relaxed"
                />
              </Field>
            </div>
          </section>
        ) : null}

        {step === 2 ? (
          <section className="rounded-2xl border border-border/70 bg-card shadow-panel">
            <header className="flex items-center gap-2 border-b border-border/70 px-5 py-3">
              <Sparkles className="h-4 w-4 text-primary" />
              <h2 className="text-[13.5px] font-semibold text-foreground">模型配置</h2>
            </header>
            <div className="grid grid-cols-1 gap-4 px-5 py-4 sm:grid-cols-2">
              <Field label="Base URL" required error={errors.baseUrl} className="sm:col-span-2">
                <Input
                  value={form.model.baseUrl}
                  onChange={(event) => patchModel({ baseUrl: event.target.value })}
                  placeholder="https://api.openai.com"
                  className="font-mono text-[12.5px]"
                />
              </Field>
              <Field
                label="API Key"
                required={apiKeyNeeded}
                error={errors.apiKey}
                hint={apiKeyNeeded ? undefined : '留空则不修改原有 API Key'}
              >
                <Input
                  type="password"
                  value={form.model.apiKey}
                  onChange={(event) => {
                    setApiKeyTouched(event.target.value !== '');
                    patchModel({ apiKey: event.target.value });
                  }}
                  placeholder={apiKeyNeeded ? 'sk-…' : '••••••••'}
                  autoComplete="new-password"
                />
              </Field>
              <Field label="模型名称" required error={errors.modelName}>
                <Input
                  value={form.model.modelName}
                  onChange={(event) => patchModel({ modelName: event.target.value })}
                  placeholder="gpt-4o-mini"
                  className="font-mono text-[12.5px]"
                />
              </Field>
              <Field label="Max Token" required error={errors.maxTokens}>
                <Input
                  value={form.model.maxTokens}
                  onChange={(event) => patchModel({ maxTokens: event.target.value })}
                  placeholder="4096"
                  inputMode="numeric"
                />
              </Field>
              <Field label="温度" required error={errors.temperature}>
                <Input
                  value={form.model.temperature}
                  onChange={(event) => patchModel({ temperature: event.target.value })}
                  placeholder="0.7"
                  inputMode="decimal"
                />
              </Field>
              <Field label="Top P" required error={errors.topP}>
                <Input
                  value={form.model.topP}
                  onChange={(event) => patchModel({ topP: event.target.value })}
                  placeholder="1.0"
                  inputMode="decimal"
                />
              </Field>
              <Field label="超时（秒）" required error={errors.timeout}>
                <Input
                  value={form.model.timeout}
                  onChange={(event) => patchModel({ timeout: event.target.value })}
                  placeholder="60"
                  inputMode="numeric"
                />
              </Field>
            </div>
          </section>
        ) : null}

        {step === 3 ? (
          <section className="rounded-2xl border border-border/70 bg-card shadow-panel">
            <header className="flex items-center justify-between gap-2 border-b border-border/70 px-5 py-3">
              <div className="flex items-center gap-2">
                <Layers className="h-4 w-4 text-primary" />
                <h2 className="text-[13.5px] font-semibold text-foreground">关联技能</h2>
              </div>
              <label className="flex items-center gap-2 text-[12.5px] text-muted-foreground">
                启用技能
                <Switch checked={skillEnabled} onCheckedChange={setSkillEnabled} />
              </label>
            </header>
            <div className="px-5 py-4">
              {skillEnabled ? (
                <Field
                  label="已发布技能"
                  required
                  error={errors.skills}
                  hint="技能内容在「技能」页维护，这里只做关联；未发布的技能不会被关联"
                >
                  <MultiSelect
                    value={form.skills}
                    onChange={(value) => patch({ skills: value })}
                    options={skillOptions}
                    placeholder={publishedSkills.loading ? '加载中…' : '选择已发布技能'}
                    searchPlaceholder="搜索技能名称 / Code"
                    emptyText={publishedSkills.loading ? '加载中…' : '暂无已发布技能'}
                  />
                </Field>
              ) : (
                <p className="rounded-lg bg-muted/40 px-3 py-6 text-center text-[12.5px] text-muted-foreground">
                  未关联技能。需要领域知识时，先到「技能」页创建并发布技能，再回到这里关联。
                </p>
              )}
            </div>
          </section>
        ) : null}

        {step === 4 ? (
          <section className="rounded-2xl border border-border/70 bg-card shadow-panel">
            <header className="flex items-center justify-between gap-2 border-b border-border/70 px-5 py-3">
              <div className="flex items-center gap-2">
                <BookOpen className="h-4 w-4 text-primary" />
                <h2 className="text-[13.5px] font-semibold text-foreground">关联工具</h2>
              </div>
              <label className="flex items-center gap-2 text-[12.5px] text-muted-foreground">
                启用工具
                <Switch checked={toolEnabled} onCheckedChange={setToolEnabled} />
              </label>
            </header>
            <div className="px-5 py-4">
              {toolEnabled ? (
                <Field label="已发布工具" required error={errors.tools} hint="仅可关联已发布的工具">
                  <MultiSelect
                    value={form.tools}
                    onChange={(value) => patch({ tools: value })}
                    options={toolOptions}
                    placeholder={publishedTools.loading ? '加载中…' : '选择已发布工具'}
                    searchPlaceholder="搜索工具名称 / Code"
                    emptyText={publishedTools.loading ? '加载中…' : '暂无已发布工具'}
                  />
                </Field>
              ) : (
                <p className="rounded-lg bg-muted/40 px-3 py-6 text-center text-[12.5px] text-muted-foreground">
                  未关联工具。Agent 只能基于模型知识回答，无法执行外部动作。
                </p>
              )}
            </div>
          </section>
        ) : null}
      </div>

      <div className="mt-4 flex items-center justify-between gap-2">
        <Button variant="ghost" onClick={onBack}>
          取消
        </Button>
        <div className="flex items-center gap-2">
          <Button variant="outline" disabled={step === 1} onClick={() => setStep((prev) => Math.max(1, prev - 1))}>
            <ChevronLeft className="h-4 w-4" />
            上一步
          </Button>
          {step < 4 ? (
            <Button onClick={nextStep}>
              下一步
              <ChevronRight className="h-4 w-4" />
            </Button>
          ) : (
            <Button onClick={submit} disabled={saving}>
              {saving ? <Loader2 className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />}
              {saving ? '保存中…' : '保存'}
            </Button>
          )}
        </div>
      </div>
    </div>
  );
}
