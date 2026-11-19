import dayjs from 'dayjs';

/** 标签归一化：兼容数组或逗号分隔字符串；统一 trim 并丢弃空项 */
export function normalizeTags(tags: unknown): string[] {
  const toTag = (value: unknown) => (value === null || value === undefined ? '' : String(value).trim());
  if (Array.isArray(tags)) return tags.map(toTag).filter(Boolean);
  if (typeof tags === 'string') return tags.split(',').map(toTag).filter(Boolean);
  return [];
}

export const validators = {
  /** 对齐后端 AgentCreateReq / ToolCreateReq 的 code 正则：字母开头，仅字母/下划线/短横线 */
  code: (value: string) => /^[A-Za-z][A-Za-z_-]*$/.test(value),
  /** 对齐后端 SkillCreateReq 的 skillCode 正则：字母开头，仅字母/数字/下划线/短横线 */
  skillCode: (value: string) => /^[A-Za-z][A-Za-z0-9_-]*$/.test(value),
  version: (value: string) => /^\d+\.\d+\.\d+$/.test(value)
};

export const SKILL_FILE_NAME_MAX_LEN = 200;
export const SKILL_FILE_NAME_CONTENT = 'skill.md';

const SKILL_FILE_NAME_RE = /^[\u4e00-\u9fa5A-Za-z0-9._-]+$/;
const SKILL_FILE_NAME_EXT_RE = /\.[A-Za-z0-9]+$/;

/** SKILL 文件名校验：返回错误文案，'' 表示通过 */
export function skillFileNameError(value: unknown): string {
  const text = String(value === null || value === undefined ? '' : value).trim();
  if (!text) return '请填写文件名';
  if (text.length > SKILL_FILE_NAME_MAX_LEN) return `文件名不能超过 ${SKILL_FILE_NAME_MAX_LEN} 字符`;
  if (!SKILL_FILE_NAME_RE.test(text)) return '文件名不能包含特殊字符（仅支持中英文、数字、下划线、连字符、点）';
  if (!SKILL_FILE_NAME_EXT_RE.test(text)) return '文件名需带格式后缀，如 文档.md、脚本.py、skill.md';
  return '';
}

export function formatDateTime(value?: string | number | null): string {
  if (value === null || value === undefined || value === '') return '—';
  const parsed = dayjs(value);
  return parsed.isValid() ? parsed.format('YYYY-MM-DD HH:mm') : String(value);
}

export function formatRelativeTime(value?: string | number | null): string {
  if (!value) return '—';
  const parsed = dayjs(value);
  if (!parsed.isValid()) return String(value);
  const diffMinutes = dayjs().diff(parsed, 'minute');
  if (diffMinutes < 1) return '刚刚';
  if (diffMinutes < 60) return `${diffMinutes} 分钟前`;
  const diffHours = dayjs().diff(parsed, 'hour');
  if (diffHours < 24) return `${diffHours} 小时前`;
  const diffDays = dayjs().diff(parsed, 'day');
  if (diffDays < 7) return `${diffDays} 天前`;
  return parsed.format('YYYY-MM-DD');
}

export function stringifyJson(value: unknown): string {
  if (value === null || value === undefined || value === '') return '';
  if (typeof value === 'string') {
    try {
      return JSON.stringify(JSON.parse(value), null, 2);
    } catch {
      return value;
    }
  }
  try {
    return JSON.stringify(value, null, 2);
  } catch {
    return String(value);
  }
}
