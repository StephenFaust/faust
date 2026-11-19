import * as React from 'react';
import { ArrowDown, ArrowLeft, ArrowUp, FolderOpen, Info, Loader2, Lock, Plus, Save, Trash2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { Field, FieldGroup } from '@/components/ui/field';
import { Select } from '@/components/ui/select';
import { useConfirm } from '@/components/ui/confirm';
import { PageHeader } from '@/components/layout/page-header';
import { Skeleton } from '@/components/ui/controls';
import { EmptyState } from '@/components/empty-state';
import { createSkill, fetchSkillDetail, updateSkill, type SkillDetail } from '@/services/agent-center';
import { useAsyncData } from '@/lib/hooks/use-paged-list';
import { SKILL_ITEM_TYPES, SKILL_ITEM_TYPE, SKILL_STATUS } from '@/lib/constants';
import { SKILL_FILE_NAME_CONTENT, SKILL_FILE_NAME_MAX_LEN, skillFileNameError, validators } from '@/lib/format';
import { cn } from '@/lib/utils';
import {
  buildSkillDrafts,
  describeImportResult,
  readFilesFromDrop,
  readFilesFromInput,
  type RawSkillFile
} from '@/lib/skill-import';
import { toast } from 'sonner';

interface ItemState {
  itemType: string;
  fileName: string;
  content: string;
}

interface FormState {
  name: string;
  skillCode: string;
  description: string;
  keyWords: string;
  items: ItemState[];
}

const BLANK: FormState = {
  name: '',
  skillCode: '',
  description: '',
  keyWords: '',
  items: []
};

function newItem(hasContent: boolean): ItemState {
  return hasContent
    ? { itemType: SKILL_ITEM_TYPE.REFERENCE, fileName: '', content: '' }
    : { itemType: SKILL_ITEM_TYPE.CONTENT, fileName: SKILL_FILE_NAME_CONTENT, content: '' };
}

export function SkillFormView({ id, onBack, onSaved }: { id?: number; onBack: () => void; onSaved: () => void }) {
  const isEdit = typeof id === 'number';
  const [form, setForm] = React.useState<FormState>(BLANK);
  const [errors, setErrors] = React.useState<Record<string, string>>({});
  const [saving, setSaving] = React.useState(false);
  const [populated, setPopulated] = React.useState(!isEdit);

  const detail = useAsyncData<SkillDetail>(isEdit ? () => fetchSkillDetail(id as number) : null, [id]);

  const confirm = useConfirm();
  const [importing, setImporting] = React.useState(false);
  const [dragging, setDragging] = React.useState(false);
  const dragDepth = React.useRef(0);
  const folderInputRef = React.useRef<HTMLInputElement>(null);

  React.useEffect(() => {
    const input = folderInputRef.current;
    if (!input) return;
    // React 的 input 属性里没有 webkitdirectory，只能落到原生属性上
    input.setAttribute('webkitdirectory', '');
    input.setAttribute('directory', '');
  }, []);

  React.useEffect(() => {
    const data = detail.data;
    if (!isEdit || !data || populated) return;
    setForm({
      name: data.name || '',
      skillCode: data.skillCode || '',
      description: data.description || '',
      keyWords: data.keyWords || '',
      items: (data.skillItemList ?? []).map((item) => ({
        itemType: item.itemType,
        fileName: item.itemType === SKILL_ITEM_TYPE.CONTENT ? SKILL_FILE_NAME_CONTENT : item.fileName || '',
        content: item.content || ''
      }))
    });
    setPopulated(true);
  }, [detail.data, isEdit, populated]);

  const setField = <K extends keyof FormState>(key: K, value: FormState[K]) => {
    setForm((prev) => ({ ...prev, [key]: value }));
    setErrors((prev) => {
      const next = { ...prev };
      delete next[key];
      return next;
    });
  };

  const clearItemErrors = (index: number) => {
    setErrors((prev) => {
      const next: Record<string, string> = {};
      Object.entries(prev).forEach(([key, value]) => {
        if (!key.startsWith(`item_${index}_`)) next[key] = value;
      });
      return next;
    });
  };

  const patchItem = (index: number, patch: Partial<ItemState>) => {
    setForm((prev) => ({
      ...prev,
      items: prev.items.map((item, i) => (i === index ? { ...item, ...patch } : item))
    }));
    clearItemErrors(index);
  };

  const addItem = () => {
    const hasContent = form.items.some((item) => item.itemType === SKILL_ITEM_TYPE.CONTENT);
    setForm((prev) => ({ ...prev, items: [...prev.items, newItem(hasContent)] }));
  };

  const removeItem = (index: number) => {
    setForm((prev) => ({ ...prev, items: prev.items.filter((_, i) => i !== index) }));
    setErrors({});
  };

  /** 上移 / 下移：文件在列表中的次序即提交时的 sortOrder */
  const moveItem = (index: number, offset: -1 | 1) => {
    const target = index + offset;
    if (target < 0 || target >= form.items.length) return;
    setForm((prev) => {
      const items = [...prev.items];
      const [current] = items.splice(index, 1);
      items.splice(target, 0, current);
      return { ...prev, items };
    });
    setErrors({});
  };

  const changeItemType = (index: number, itemType: string) => {
    if (itemType === SKILL_ITEM_TYPE.CONTENT) {
      const taken = form.items.some((item, i) => i !== index && item.itemType === SKILL_ITEM_TYPE.CONTENT);
      if (taken) {
        toast.error('每个技能只能有一个 CONTENT 文件');
        return;
      }
      patchItem(index, { itemType, fileName: SKILL_FILE_NAME_CONTENT });
      return;
    }
    const current = form.items[index];
    patchItem(index, {
      itemType,
      fileName: current?.itemType === SKILL_ITEM_TYPE.CONTENT ? '' : current?.fileName ?? ''
    });
  };

  /**
   * 导入 skill 文件夹：读取目录内所有文本文件（含文件名与内容），
   * 主文档（skill.md / content.md，兜底第一个 Markdown）自动归为 CONTENT 并排在首位。
   */
  const applyImport = async (rawFiles: RawSkillFile[]) => {
    if (!rawFiles.length) {
      toast.error('没有读取到文件');
      return;
    }
    setImporting(true);
    try {
      const result = await buildSkillDrafts(rawFiles);
      if (!result.files.length) {
        toast.error('没有可导入的文件（已跳过图片等非文本文件与超过 512KB 的文件）');
        return;
      }
      if (form.items.length) {
        // 一个文件夹就是一份完整技能，导入按「替换」处理，避免新旧文件混在一起
        const confirmed = await confirm({
          title: '替换当前技能文件？',
          description: `导入会用文件夹里的 ${result.files.length} 个文件替换当前 ${form.items.length} 个文件，已手动编辑的内容将丢失。`,
          confirmText: '替换',
          destructive: true
        });
        if (!confirmed) return;
      }
      setForm((prev) => ({
        ...prev,
        items: result.files.map((file) => ({
          itemType: file.itemType,
          fileName: file.fileName,
          content: file.content
        }))
      }));
      setErrors({});
      toast.success(describeImportResult(result));
    } catch (error) {
      toast.error(`导入失败：${error instanceof Error ? error.message : '未知错误'}`);
    } finally {
      setImporting(false);
    }
  };

  const onFolderPicked = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const files = readFilesFromInput(event.target.files);
    // 清空 value，保证同一个文件夹可以连续导入两次
    event.target.value = '';
    await applyImport(files);
  };

  const onDrop = async (event: React.DragEvent<HTMLDivElement>) => {
    event.preventDefault();
    dragDepth.current = 0;
    setDragging(false);
    const files = await readFilesFromDrop(event.dataTransfer);
    await applyImport(files);
  };

  const onDragEnter = (event: React.DragEvent<HTMLDivElement>) => {
    event.preventDefault();
    dragDepth.current += 1;
    setDragging(true);
  };

  const onDragLeave = (event: React.DragEvent<HTMLDivElement>) => {
    event.preventDefault();
    dragDepth.current -= 1;
    if (dragDepth.current <= 0) {
      dragDepth.current = 0;
      setDragging(false);
    }
  };

  const validate = () => {
    const next: Record<string, string> = {};
    if (!form.name.trim()) next.name = '请填写技能名称';
    if (!form.skillCode.trim()) next.skillCode = '请填写技能 Code';
    else if (!validators.skillCode(form.skillCode.trim())) {
      next.skillCode = 'Code 需以字母开头，只能包含字母、数字、下划线和短横线';
    }
    if (!form.items.length) next.items = '请至少添加一个文件';
    const contentCount = form.items.filter((item) => item.itemType === SKILL_ITEM_TYPE.CONTENT).length;
    if (form.items.length && contentCount === 0) next.itemsContent = '必须包含一个 CONTENT 文件（作为主文档）';
    else if (contentCount > 1) next.itemsContent = 'CONTENT 文件最多只能有一个';

    const nameSet = new Set<string>();
    form.items.forEach((item, index) => {
      const fileNameError = skillFileNameError(item.fileName);
      if (fileNameError) next[`item_${index}_fileName`] = fileNameError;
      if (!item.content.trim()) next[`item_${index}_content`] = '请填写文件内容';
      const fileName = String(item.fileName ?? '').trim();
      if (!fileName) return;
      if (!nameSet.has(fileName)) {
        nameSet.add(fileName);
        return;
      }
      if (item.itemType !== SKILL_ITEM_TYPE.CONTENT) next[`item_${index}_fileName`] = `文件名不能重复：${fileName}`;
    });

    setErrors(next);
    if (Object.keys(next).length) {
      toast.error('请完善必填项后再保存');
      return false;
    }
    return true;
  };

  const submit = async () => {
    if (!populated) {
      toast.error('技能详情尚未加载完成，请稍后重试');
      return;
    }
    if (isEdit && detail.data?.status === SKILL_STATUS.PUBLISHED) {
      toast.error('已发布的技能不可编辑，请先下架');
      return;
    }
    if (!validate()) return;
    setSaving(true);
    try {
      const body: Record<string, unknown> = {
        skillCode: form.skillCode.trim(),
        name: form.name.trim(),
        description: form.description.trim() || null,
        keyWords: form.keyWords.trim() || null,
        // sortOrder 只存在于文件上，按当前列表次序回写
        skillItemList: form.items.map((item, index) => ({
          itemType: item.itemType,
          fileName: item.itemType === SKILL_ITEM_TYPE.CONTENT ? SKILL_FILE_NAME_CONTENT : item.fileName.trim(),
          content: item.content.trim(),
          sortOrder: index
        }))
      };
      if (isEdit) {
        await updateSkill({ ...body, skillId: id });
        toast.success('技能已更新');
      } else {
        await createSkill(body);
        toast.success('技能已创建（草稿状态，发布后才会被 Agent 关联）');
      }
      onSaved();
    } catch (error) {
      toast.error(`${isEdit ? '更新' : '创建'}失败：${error instanceof Error ? error.message : '未知错误'}`);
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="animate-fade-in">
      <PageHeader
        title={isEdit ? '编辑技能' : '新增技能'}
        crumbs={[
          { text: 'Faust', to: '/' },
          { text: '技能', to: '/skills' },
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
            {Array.from({ length: 4 }).map((_, index) => (
              <Skeleton key={index} className="h-16" />
            ))}
          </div>
        </div>
      ) : isEdit && detail.data?.status === SKILL_STATUS.PUBLISHED ? (
        // 已发布技能不可编辑：后端同样会拦截，这里直接不给表单
        <div className="rounded-2xl border border-border/70 bg-card p-5 shadow-panel">
          <EmptyState
            icon={<Lock className="h-5 w-5" />}
            title="已发布的技能不可编辑"
            description={`「${detail.data.name || form.name || '该技能'}」当前为已发布状态，请先在技能列表中下架后再编辑。`}
            action={
              <Button variant="outline" size="sm" onClick={onBack}>
                返回列表
              </Button>
            }
          />
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
                  placeholder="技能名称，例如 日志排查"
                />
              </Field>
              <Field
                label="Code"
                required
                error={errors.skillCode}
                hint={isEdit ? 'Code 修改后全局唯一性会重新校验' : '字母开头，仅字母 / 数字 / 下划线 / 短横线，全局唯一'}
              >
                <Input
                  value={form.skillCode}
                  onChange={(event) => setField('skillCode', event.target.value)}
                  placeholder="log_troubleshoot"
                  className="font-mono"
                />
              </Field>
              <Field label="关键词" error={errors.keyWords} hint="逗号分隔，便于检索与运维排查">
                <Input
                  value={form.keyWords}
                  onChange={(event) => setField('keyWords', event.target.value)}
                  placeholder="日志,报错,排查"
                />
              </Field>
              <Field label="描述" error={errors.description}>
                <Input
                  value={form.description}
                  onChange={(event) => setField('description', event.target.value)}
                  placeholder="一句话说明这个技能解决什么问题"
                />
              </Field>
            </div>
          </FieldGroup>

          <div className="mt-4">
            <FieldGroup
              title={
                <>
                  <Plus className="h-4 w-4 text-primary" />
                  技能文件
                </>
              }
              extra={<span className="text-[11.5px] text-muted-foreground">按顺序注入，可通过上下移动调整</span>}
            >
              {/* 文件夹选择：webkitdirectory 让浏览器整目录读取，路径走 webkitRelativePath */}
              <input ref={folderInputRef} type="file" multiple className="hidden" onChange={onFolderPicked} />

              {errors.items ? <p className="text-[11.5px] text-rose-600">{errors.items}</p> : null}
              {errors.itemsContent ? <p className="text-[11.5px] text-rose-600">{errors.itemsContent}</p> : null}

              <div
                onDragEnter={onDragEnter}
                onDragOver={(event) => {
                  event.preventDefault();
                  event.dataTransfer.dropEffect = 'copy';
                }}
                onDragLeave={onDragLeave}
                onDrop={onDrop}
                className={cn(
                  'rounded-xl transition-all',
                  dragging && 'bg-primary/[0.05] ring-2 ring-primary/30 ring-offset-2 ring-offset-card'
                )}
              >
                {form.items.length === 0 ? (
                  <div className="rounded-lg border border-dashed border-border/80 bg-muted/25 px-4 py-7 text-center">
                    <p className="text-[12.5px] font-medium text-foreground/80">
                      {dragging ? '松开鼠标即可导入' : '还没有文件'}
                    </p>
                    <p className="mt-1 text-[11.5px] leading-relaxed text-muted-foreground">
                      可点击「导入文件夹」选择本地 skill 目录，或把文件夹直接拖到这里；建议至少保留一个 CONTENT
                      文件作为技能主文档。
                    </p>
                  </div>
                ) : (
                  <div className="flex flex-col gap-2.5">
                  {form.items.map((item, index) => {
                    const isContent = item.itemType === SKILL_ITEM_TYPE.CONTENT;
                    const contentTaken = form.items.some(
                      (current, i) => i !== index && current.itemType === SKILL_ITEM_TYPE.CONTENT
                    );
                    return (
                      <div key={index} className="rounded-lg border border-border/80 bg-muted/10 p-3">
                        <div className="flex flex-wrap items-start gap-2">
                          <span className="flex h-8 w-6 shrink-0 items-center justify-center font-mono text-[11.5px] text-muted-foreground/70">
                            #{index + 1}
                          </span>
                          <Select
                            value={item.itemType}
                            onChange={(value) => changeItemType(index, String(value ?? ''))}
                            options={SKILL_ITEM_TYPES.map((option) => ({
                              ...option,
                              disabled: option.value === SKILL_ITEM_TYPE.CONTENT && contentTaken && !isContent
                            }))}
                            className="w-[150px]"
                            size="sm"
                          />
                          <div className="min-w-[200px] flex-1">
                            <Input
                              value={item.fileName}
                              disabled={isContent}
                              maxLength={SKILL_FILE_NAME_MAX_LEN}
                              title={isContent ? 'CONTENT 类型文件名固定为 skill.md' : undefined}
                              onChange={(event) => patchItem(index, { fileName: event.target.value })}
                              placeholder={isContent ? 'skill.md' : '文件名，如 文档.md、脚本.py'}
                              className="h-8 font-mono text-[12px]"
                            />
                            {errors[`item_${index}_fileName`] ? (
                              <p className="mt-1 text-[11px] text-rose-600">{errors[`item_${index}_fileName`]}</p>
                            ) : null}
                          </div>
                          <div className="flex items-center gap-0.5">
                            <Button
                              variant="ghost"
                              size="xs"
                              className="px-1.5"
                              title="上移"
                              aria-label="上移"
                              disabled={index === 0}
                              onClick={() => moveItem(index, -1)}
                            >
                              <ArrowUp className="h-3.5 w-3.5" />
                            </Button>
                            <Button
                              variant="ghost"
                              size="xs"
                              className="px-1.5"
                              title="下移"
                              aria-label="下移"
                              disabled={index === form.items.length - 1}
                              onClick={() => moveItem(index, 1)}
                            >
                              <ArrowDown className="h-3.5 w-3.5" />
                            </Button>
                            <Button
                              variant="ghost"
                              size="xs"
                              className="px-1.5 text-rose-600 hover:bg-rose-50 hover:text-rose-700"
                              title="删除文件"
                              aria-label="删除文件"
                              onClick={() => removeItem(index)}
                            >
                              <Trash2 className="h-3.5 w-3.5" />
                            </Button>
                          </div>
                        </div>

                        <Textarea
                          rows={item.itemType === SKILL_ITEM_TYPE.CONTENT ? 8 : 4}
                          value={item.content}
                          onChange={(event) => patchItem(index, { content: event.target.value })}
                          placeholder={
                            item.itemType === SKILL_ITEM_TYPE.SCRIPT
                              ? '脚本内容'
                              : item.itemType === SKILL_ITEM_TYPE.REFERENCE
                                ? 'URL 或参考资料内容'
                                : 'Markdown 内容'
                          }
                          className="mt-2 font-mono text-[12px]"
                        />
                        {errors[`item_${index}_content`] ? (
                          <p className="mt-1 text-[11px] text-rose-600">{errors[`item_${index}_content`]}</p>
                        ) : null}
                      </div>
                    );
                  })}
                </div>
                )}
              </div>

              <div className="flex flex-wrap items-center gap-2 self-start">
                <Button variant="secondary" size="sm" onClick={addItem}>
                  <Plus className="h-3.5 w-3.5" />
                  添加文件
                </Button>
                <Button
                  variant="outline"
                  size="sm"
                  disabled={importing}
                  onClick={() => folderInputRef.current?.click()}
                >
                  {importing ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <FolderOpen className="h-3.5 w-3.5" />}
                  {importing ? '导入中…' : '导入文件夹'}
                </Button>
                <span className="text-[11.5px] text-muted-foreground">
                  skill.md / content.md 识别为主文档，子目录文件命名为 目录__文件名
                </span>
              </div>
            </FieldGroup>
          </div>

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
