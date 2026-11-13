import * as React from 'react';
import { CircleAlert, FileText, Loader2, Paperclip, Plus, Settings2, X } from 'lucide-react';
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover';
import { Button } from '@/components/ui/button';
import { EnvVarRows } from '@/components/env-var-rows';
import { uploadFile } from '@/services/agent-center';
import { ALLOWED_EXT, ALLOWED_MIME, MAX_FILES, MAX_FILE_SIZE } from '@/lib/constants';
import { createEnvRow, validateEnvRows, type EnvRow } from '@/lib/env';
import { cn } from '@/lib/utils';

export interface UploadedFile {
  id: string;
  fileName: string;
  fileType: string;
  url: string;
  uploading: boolean;
}

function inferFileType(fileName: string) {
  const name = (fileName || '').toLowerCase();
  if (/\.jpe?g$/.test(name)) return 'JPEG';
  if (/\.png$/.test(name)) return 'PNG';
  if (/\.pdf$/.test(name)) return 'PDF';
  if (/\.(xls|xlsx)$/.test(name)) return 'EXCEL';
  return 'PDF';
}

// ---------------- 环境变量 ----------------
function EnvVarPanel({
  value,
  onChange,
  disabled
}: {
  value: EnvRow[];
  onChange: (rows: EnvRow[]) => void;
  disabled?: boolean;
}) {
  const [open, setOpen] = React.useState(false);
  const [draft, setDraft] = React.useState<EnvRow[]>([]);
  const [errors, setErrors] = React.useState<Record<string, string>>({});

  const openPanel = (next: boolean) => {
    if (next) {
      setDraft(value.length ? value.map((row) => createEnvRow(row.key, row.value)) : [createEnvRow()]);
      setErrors({});
    }
    setOpen(next);
  };

  const submit = () => {
    const { errors: nextErrors, list } = validateEnvRows(draft);
    if (Object.keys(nextErrors).length) {
      setErrors(nextErrors);
      return;
    }
    onChange(list);
    setOpen(false);
  };

  // 编辑后清空上一次的校验提示，避免红框一直挂着
  const handleRows = (next: EnvRow[]) => {
    setDraft(next);
    setErrors((prev) => (Object.keys(prev).length ? {} : prev));
  };

  return (
    <Popover open={open} onOpenChange={openPanel}>
      <PopoverTrigger asChild>
        <button
          type="button"
          disabled={disabled}
          title={value.length ? `环境变量（已配置 ${value.length} 个）` : '环境变量配置'}
          className={cn(
            'relative flex h-8 w-8 items-center justify-center rounded-lg text-muted-foreground transition-colors',
            'hover:bg-muted hover:text-foreground disabled:opacity-50'
          )}
        >
          <Settings2 className="h-4 w-4" />
          {value.length > 0 ? (
            <span className="absolute -right-0.5 -top-0.5 flex h-4 min-w-4 items-center justify-center rounded-full bg-primary px-1 text-[9.5px] font-semibold text-primary-foreground">
              {value.length}
            </span>
          ) : null}
        </button>
      </PopoverTrigger>

      <PopoverContent side="top" align="start" className="w-[420px]">
        <div className="flex flex-col gap-2.5">
          <p className="text-[11.5px] leading-relaxed text-muted-foreground">
            随消息一起下发给智能体；key 需以字母开头，value 可留空。
          </p>

          <EnvVarRows rows={draft} onChange={handleRows} errors={errors} />

          <div className="flex items-center justify-between border-t border-border/70 pt-2.5">
            <Button variant="ghost" size="xs" onClick={() => setDraft((rows) => [...rows, createEnvRow()])}>
              <Plus className="h-3.5 w-3.5" />
              添加变量
            </Button>
            <div className="flex items-center gap-2">
              <Button variant="outline" size="sm" onClick={() => setOpen(false)}>
                取消
              </Button>
              <Button size="sm" onClick={submit}>
                确定
              </Button>
            </div>
          </div>
        </div>
      </PopoverContent>
    </Popover>
  );
}

// ---------------- 输入区工具条 ----------------
export function ChatInputTools({
  files,
  onFilesChange,
  envVars,
  onEnvVarsChange,
  disabled
}: {
  files: UploadedFile[];
  onFilesChange: (files: UploadedFile[]) => void;
  envVars: EnvRow[];
  onEnvVarsChange: (rows: EnvRow[]) => void;
  disabled?: boolean;
}) {
  const inputRef = React.useRef<HTMLInputElement>(null);
  const [error, setError] = React.useState<string | null>(null);
  const filesRef = React.useRef(files);
  filesRef.current = files;

  const showError = (message: string) => {
    setError(message);
    window.setTimeout(() => setError(null), 3200);
  };

  const handlePick = (event: React.ChangeEvent<HTMLInputElement>) => {
    const picked = Array.from(event.target.files ?? []);
    event.target.value = '';
    if (!picked.length) return;

    const accepted: File[] = [];
    for (const file of picked) {
      const ext = (file.name.split('.').pop() || '').toLowerCase();
      if (!ALLOWED_EXT.includes(ext)) {
        showError(`不支持的文件类型：${file.name}`);
        continue;
      }
      if (file.type && !ALLOWED_MIME.includes(file.type)) {
        showError(`不支持的文件类型：${file.name}`);
        continue;
      }
      if (file.size > MAX_FILE_SIZE) {
        showError(`文件超过 200MB 上限：${file.name}`);
        continue;
      }
      if (filesRef.current.length + accepted.length >= MAX_FILES) {
        showError(`最多只能上传 ${MAX_FILES} 个文件`);
        break;
      }
      accepted.push(file);
    }
    if (!accepted.length) return;

    accepted.forEach((file) => {
      const id = `f-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`;
      const fileType = inferFileType(file.name);
      onFilesChange([...filesRef.current, { id, fileName: file.name, fileType, url: '', uploading: true }]);
      uploadFile(file)
        .then((response) => {
          onFilesChange(
            filesRef.current.map((item) =>
              item.id === id
                ? { ...item, url: response.url, fileName: response.fileName || file.name, uploading: false }
                : item
            )
          );
        })
        .catch((err) => {
          onFilesChange(filesRef.current.filter((item) => item.id !== id));
          showError(err instanceof Error ? err.message : '文件上传失败');
        });
    });
  };

  return (
    <div className="flex flex-col gap-2">
      {files.length > 0 ? (
        <div className="flex flex-wrap gap-1.5">
          {files.map((file) => (
            <span
              key={file.id}
              className={cn(
                'inline-flex max-w-[240px] items-center gap-1.5 rounded-lg border border-border bg-muted/50 px-2 py-1',
                file.uploading && 'opacity-70'
              )}
              title={file.fileName}
            >
              {file.uploading ? (
                <Loader2 className="h-3 w-3 animate-spin text-muted-foreground" />
              ) : (
                <FileText className="h-3 w-3 text-muted-foreground" />
              )}
              <span className="truncate text-[11.5px] text-foreground/80">{file.fileName}</span>
              <button
                type="button"
                disabled={disabled}
                onClick={() => onFilesChange(filesRef.current.filter((item) => item.id !== file.id))}
                className="rounded p-0.5 text-muted-foreground transition hover:bg-border hover:text-foreground disabled:opacity-50"
                aria-label="移除文件"
              >
                <X className="h-3 w-3" />
              </button>
            </span>
          ))}
        </div>
      ) : null}

      {error ? (
        <p className="flex items-center gap-1.5 text-[11.5px] text-rose-600">
          <CircleAlert className="h-3 w-3" />
          {error}
        </p>
      ) : null}

      <div className="flex items-center gap-1">
        <input
          ref={inputRef}
          type="file"
          multiple
          accept=".jpg,.jpeg,.png,.pdf,.xls,.xlsx"
          onChange={handlePick}
          className="hidden"
        />
        <button
          type="button"
          disabled={disabled || files.length >= MAX_FILES}
          onClick={() => inputRef.current?.click()}
          title="上传文件（图片 / PDF / Excel）"
          className={cn(
            'flex h-8 w-8 items-center justify-center rounded-lg text-muted-foreground transition-colors',
            'hover:bg-muted hover:text-foreground disabled:opacity-50'
          )}
        >
          <Paperclip className="h-4 w-4" />
        </button>
        <EnvVarPanel value={envVars} onChange={onEnvVarsChange} disabled={disabled} />
        <span className="text-[11px] text-muted-foreground">
          {files.length > 0 ? `已选 ${files.length}/${MAX_FILES} 个文件` : ''}
        </span>
      </div>
    </div>
  );
}
