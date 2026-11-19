/**
 * 技能文件夹导入：把本地 skill 文件夹解析成「技能文件」列表。
 *
 * 目录约定与本项目 SKILL 一致：
 *   my-skill/
 *     skill.md            → CONTENT（主文档，fileName 固定 skill.md）
 *     references/api.md   → REFERENCE，fileName = references__api.md
 *     scripts/run.py      → SCRIPT，fileName = scripts__run.py
 *
 * 说明：后端 skillFileName 只允许 中英文 / 数字 / 下划线 / 连字符 / 点，
 * 因此子目录用 `__` 拼接进文件名，保留来源路径信息且天然避免重名。
 */
import { SKILL_ITEM_TYPE } from '@/lib/constants';
import { SKILL_FILE_NAME_CONTENT, SKILL_FILE_NAME_MAX_LEN } from '@/lib/format';

export interface RawSkillFile {
  /** 相对 skill 根目录的路径，如 references/api.md */
  path: string;
  file: File;
}

export interface SkillFileDraft {
  itemType: string;
  fileName: string;
  content: string;
  /** 原始相对路径，仅用于提示文案，不提交后端 */
  path: string;
}

export interface SkillImportSkip {
  ignored: number;
  binary: number;
  tooLarge: number;
  empty: number;
}

export interface SkillImportResult {
  files: SkillFileDraft[];
  skipped: SkillImportSkip;
  /** 识别为主文档的原始路径（skill.md / content.md，或兜底的第一个 Markdown） */
  contentPath?: string;
  /** 超过数量上限未导入的文件数 */
  overflow: number;
}

/** 可读取为文本的扩展名（不区分大小写） */
const TEXT_EXT = new Set([
  'md', 'markdown', 'mdx', 'txt', 'text',
  'json', 'jsonl', 'yaml', 'yml', 'toml', 'ini', 'conf', 'cfg', 'env', 'properties',
  'py', 'sh', 'bash', 'zsh', 'js', 'mjs', 'cjs', 'ts', 'tsx', 'jsx', 'java', 'kt', 'kts',
  'go', 'rb', 'rs', 'php', 'pl', 'lua', 'r', 'sql', 'gradle', 'groovy', 'vue', 'svelte',
  'html', 'htm', 'css', 'scss', 'less', 'xml', 'svg', 'csv', 'tsv', 'log', 'proto', 'dockerfile'
]);

/** 归为 SCRIPT 类型的扩展名，其余文本统一为 REFERENCE */
const SCRIPT_EXT = new Set([
  'py', 'sh', 'bash', 'zsh', 'js', 'mjs', 'cjs', 'ts', 'tsx', 'jsx', 'java', 'kt', 'kts',
  'go', 'rb', 'rs', 'php', 'pl', 'lua', 'r', 'sql', 'gradle', 'groovy'
]);

/** 直接跳过、不进入技能文件的目录 */
const IGNORED_DIRS = new Set([
  '.git', '.svn', '.hg', '.idea', '.vscode', '.venv', 'venv', 'node_modules',
  '__pycache__', 'dist', 'build', 'target', 'out', '.next', '.cache', '.pytest_cache'
]);

/** 明确的主文档文件名（小写匹配，不含路径前缀） */
const MAIN_DOC_NAMES = new Set(['skill.md', 'content.md']);

const MAX_FILE_BYTES = 512 * 1024;
const MAX_FILES = 80;

const normalizePath = (value: string) => value.replace(/\\/g, '/').replace(/^\.?\//, '');

const segmentsOf = (path: string) => normalizePath(path).split('/').filter(Boolean);

const extOf = (name: string) => {
  const index = name.lastIndexOf('.');
  return index > 0 ? name.slice(index + 1).toLowerCase() : '';
};

const baseNameOf = (path: string) => {
  const segments = segmentsOf(path);
  return (segments[segments.length - 1] ?? '').toLowerCase();
};

const isIgnoredPath = (segments: string[]) =>
  segments.some((segment, index) => {
    const isLast = index === segments.length - 1;
    if (segment.startsWith('.')) return true;
    return !isLast && IGNORED_DIRS.has(segment.toLowerCase());
  });

/**
 * 去掉所有文件共同的根目录名（选择文件夹时 webkitRelativePath 会带上它）。
 * 单独拖入的若干文件没有共同根目录，原样返回。
 */
function stripCommonRoot(paths: string[]): string[] {
  if (!paths.length) return paths;
  const firstSegments = paths.map((path) => segmentsOf(path)[0]);
  const root = firstSegments[0];
  if (!root) return paths;
  const sameRoot = paths.every((path) => segmentsOf(path).length > 1 && segmentsOf(path)[0] === root);
  if (!sameRoot) return paths;
  return paths.map((path) => segmentsOf(path).slice(1).join('/'));
}

/** 文件名净化：仅保留中英文 / 数字 / 下划线 / 连字符 / 点，超长时保留扩展名截断 */
function sanitizeFileName(name: string): string {
  const cleaned = name
    .replace(/[^\u4e00-\u9fa5A-Za-z0-9._-]/g, '-')
    .replace(/-{2,}/g, '-')
    .replace(/^[-.]+/, '');
  if (cleaned.length <= SKILL_FILE_NAME_MAX_LEN) return cleaned;
  const ext = extOf(cleaned);
  const suffix = ext ? `.${ext}` : '';
  return cleaned.slice(0, SKILL_FILE_NAME_MAX_LEN - suffix.length) + suffix;
}

/** 相对路径 → 合法 fileName（子目录用 __ 连接） */
function toFileName(path: string): string {
  return sanitizeFileName(segmentsOf(path).join('__'));
}

function withIndexSuffix(fileName: string, index: number): string {
  const dot = fileName.lastIndexOf('.');
  if (dot <= 0) return `${fileName}-${index}`;
  return `${fileName.slice(0, dot)}-${index}${fileName.slice(dot)}`;
}

function decodeText(raw: string): string {
  return raw.replace(/^\uFEFF/, '').replace(/\r\n?/g, '\n');
}

/** 粗判二进制：出现 NUL 字节基本可以确定不是文本 */
const looksBinary = (text: string) => text.slice(0, 8000).includes('\u0000');

interface ParsedFile {
  path: string;
  fileName: string;
  content: string;
  isText: boolean;
  tooLarge: boolean;
}

async function parseOne(raw: RawSkillFile): Promise<ParsedFile> {
  const path = normalizePath(raw.path);
  const name = segmentsOf(path).slice(-1)[0] ?? raw.file.name;
  const ext = extOf(name);
  const base: ParsedFile = { path, fileName: toFileName(path), content: '', isText: true, tooLarge: false };
  if (!TEXT_EXT.has(ext) && ext !== '') return { ...base, isText: false };
  if (!ext && !/^(dockerfile|makefile)$/i.test(name)) return { ...base, isText: false };
  if (raw.file.size > MAX_FILE_BYTES) return { ...base, tooLarge: true };
  try {
    const text = decodeText(await raw.file.text());
    if (looksBinary(text)) return { ...base, isText: false };
    return { ...base, content: text };
  } catch {
    return { ...base, isText: false };
  }
}

/**
 * 解析一组原始文件，产出可直接填充到表单的草稿列表：
 * 主文档排在最前并归为 CONTENT，其余按路径排序。
 */
export async function buildSkillDrafts(rawFiles: RawSkillFile[]): Promise<SkillImportResult> {
  const skipped: SkillImportSkip = { ignored: 0, binary: 0, tooLarge: 0, empty: 0 };

  const paths = stripCommonRoot(rawFiles.map((item) => normalizePath(item.path)));
  const candidates: RawSkillFile[] = [];
  rawFiles.forEach((item, index) => {
    const segments = segmentsOf(paths[index] ?? item.path);
    if (!segments.length || isIgnoredPath(segments)) {
      skipped.ignored += 1;
      return;
    }
    candidates.push({ path: segments.join('/'), file: item.file });
  });

  const parsed = await Promise.all(candidates.map(parseOne));
  const usable: ParsedFile[] = [];
  parsed.forEach((item) => {
    if (!item.isText) return void (skipped.binary += 1);
    if (item.tooLarge) return void (skipped.tooLarge += 1);
    // 空文件无法通过表单校验（content 必填），直接跳过
    if (!item.content.trim()) return void (skipped.empty += 1);
    usable.push(item);
  });

  usable.sort((a, b) => {
    const depthDiff = segmentsOf(a.path).length - segmentsOf(b.path).length;
    return depthDiff !== 0 ? depthDiff : a.path.localeCompare(b.path, 'en');
  });

  const contentFile =
    usable.find((item) => MAIN_DOC_NAMES.has(baseNameOf(item.path))) ??
    usable.find((item) => ['md', 'markdown', 'txt'].includes(extOf(item.path)));

  const ordered = contentFile ? [contentFile, ...usable.filter((item) => item !== contentFile)] : usable;
  const kept = ordered.slice(0, MAX_FILES);

  const usedNames = new Set<string>();
  const files: SkillFileDraft[] = kept.map((item) => {
    const isContent = item === contentFile;
    let fileName = isContent ? SKILL_FILE_NAME_CONTENT : item.fileName;
    if (!isContent && usedNames.has(fileName.toLowerCase())) {
      let index = 2;
      while (usedNames.has(withIndexSuffix(fileName, index).toLowerCase())) index += 1;
      fileName = withIndexSuffix(fileName, index);
    }
    usedNames.add(fileName.toLowerCase());
    return {
      itemType: isContent
        ? SKILL_ITEM_TYPE.CONTENT
        : SCRIPT_EXT.has(extOf(item.path))
          ? SKILL_ITEM_TYPE.SCRIPT
          : SKILL_ITEM_TYPE.REFERENCE,
      fileName,
      content: item.content,
      path: item.path
    };
  });

  return {
    files,
    skipped,
    contentPath: contentFile?.path,
    overflow: Math.max(0, ordered.length - kept.length)
  };
}

/** 导入结果 → 一句可读的 toast 文案 */
export function describeImportResult(result: SkillImportResult): string {
  const skippedParts: string[] = [];
  if (result.skipped.binary) skippedParts.push(`${result.skipped.binary} 个非文本文件`);
  if (result.skipped.tooLarge) skippedParts.push(`${result.skipped.tooLarge} 个超过 512KB 的文件`);
  if (result.skipped.empty) skippedParts.push(`${result.skipped.empty} 个空文件`);
  if (result.overflow) skippedParts.push(`${result.overflow} 个超出 ${MAX_FILES} 个上限的文件`);
  const skippedText = skippedParts.length ? `，已跳过 ${skippedParts.join('、')}` : '';
  const fallbackContent =
    result.contentPath && !MAIN_DOC_NAMES.has(baseNameOf(result.contentPath))
      ? `；已将 ${result.contentPath} 作为主文档`
      : '';
  return `已导入 ${result.files.length} 个文件${skippedText}${fallbackContent}`;
}

/** 从 <input type="file" webkitdirectory> / 普通多选文件读取 */
export function readFilesFromInput(fileList: FileList | null): RawSkillFile[] {
  if (!fileList) return [];
  return Array.from(fileList).map((file) => ({
    path: (file as File & { webkitRelativePath?: string }).webkitRelativePath || file.name,
    file
  }));
}

/** 递归读取拖拽进来的目录（readEntries 单次最多 100 条，需循环读到空） */
async function readDirectoryEntry(entry: FileSystemDirectoryEntry, out: RawSkillFile[]): Promise<void> {
  const reader = entry.createReader();
  for (;;) {
    const batch = await new Promise<FileSystemEntry[]>((resolve) => {
      reader.readEntries(resolve, () => resolve([]));
    });
    if (!batch.length) return;
    for (const child of batch) {
      // eslint-disable-next-line no-await-in-loop
      await readEntry(child, out);
    }
  }
}

function readFileEntry(entry: FileSystemFileEntry): Promise<RawSkillFile> {
  return new Promise((resolve, reject) => {
    entry.file(
      (file) => resolve({ path: entry.fullPath || file.name, file }),
      () => reject(new Error(entry.name))
    );
  });
}

async function readEntry(entry: FileSystemEntry, out: RawSkillFile[]): Promise<void> {
  if (entry.isDirectory) {
    await readDirectoryEntry(entry as FileSystemDirectoryEntry, out);
    return;
  }
  try {
    out.push(await readFileEntry(entry as FileSystemFileEntry));
  } catch {
    /* 单个文件读取失败不影响整体导入 */
  }
}

/** 从拖拽事件读取：同时兼容「拖文件夹」与「拖多个文件」 */
export async function readFilesFromDrop(dataTransfer: DataTransfer | null): Promise<RawSkillFile[]> {
  if (!dataTransfer) return [];
  const items = Array.from(dataTransfer.items ?? []);
  const entries = items
    .map((item) => (item as DataTransferItem & { webkitGetAsEntry?: () => FileSystemEntry | null }).webkitGetAsEntry?.())
    .filter((entry): entry is FileSystemEntry => Boolean(entry));

  if (!entries.length) return readFilesFromInput(dataTransfer.files);

  const out: RawSkillFile[] = [];
  for (const entry of entries) {
    // eslint-disable-next-line no-await-in-loop
    await readEntry(entry, out);
  }
  return out;
}
