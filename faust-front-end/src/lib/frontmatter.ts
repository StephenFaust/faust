/**
 * Markdown 文件头（YAML frontmatter）解析。
 *
 * SKILL 的 skill.md 通常以
 *   ---
 *   name: xxx
 *   description: yyy
 *   ---
 * 开头，直接交给 markdown-it 会渲染成一整段纯文本（`breaks: true` 后就是一行一个 key），
 * 既没有层级也没法扫读。这里先把它切出来单独展示。
 *
 * 只覆盖 skill 文件实际会写到的子集：`key: value`、行内数组 `[a, b]`、块列表 `- a`、
 * 块标量 `|` / `>`、注释；不支持嵌套映射（遇到就跳过该行，不影响正文）。
 */

export interface FrontmatterEntry {
  key: string;
  /** 单值；数组为空串 */
  value: string;
  /** 数组值，渲染为标签 */
  list?: string[];
}

export interface SplitFrontmatterResult {
  /** 没有合法文件头时为 null */
  entries: FrontmatterEntry[] | null;
  /** 去掉文件头之后的正文 */
  body: string;
}

const OPEN_FENCE = /^---\s*$/;
const CLOSE_FENCE = /^(---|\.\.\.)\s*$/;
const KEY_LINE = /^([A-Za-z0-9_.-]+)\s*:\s*(.*)$/;
const LIST_ITEM = /^\s*-\s+(.*)$/;

function unquote(value: string): string {
  const text = value.trim();
  if (text.length < 2) return text;
  const head = text[0];
  if ((head === '"' || head === "'") && text.endsWith(head)) {
    return text
      .slice(1, -1)
      .replace(/\\"/g, '"')
      .replace(/\\n/g, '\n');
  }
  return text;
}

/** `[a, b]` / `["a", "b"]` → string[]；不是行内数组则返回 null */
function asInlineList(value: string): string[] | null {
  const text = value.trim();
  if (!text.startsWith('[') || !text.endsWith(']')) return null;
  const inner = text.slice(1, -1).trim();
  if (!inner) return [];
  try {
    const parsed: unknown = JSON.parse(`[${inner}]`);
    if (Array.isArray(parsed)) return parsed.map((item) => String(item));
  } catch {
    /* 非严格 JSON（如 [a, b]），退回逗号切分 */
  }
  return inner
    .split(',')
    .map((item) => unquote(item))
    .filter(Boolean);
}

/** 块标量 `|`（保留换行）/ `>`（折叠为空格） */
function readBlockScalar(lines: string[], start: number, folded: boolean): { text: string; next: number } {
  const collected: string[] = [];
  let index = start;
  let indent = -1;

  while (index < lines.length) {
    const line = lines[index];
    if (!line.trim()) {
      if (indent < 0) {
        index += 1;
        continue;
      }
      collected.push('');
      index += 1;
      continue;
    }
    const lineIndent = line.match(/^\s*/)?.[0].length ?? 0;
    if (indent < 0) {
      // 块标量后面直接是同级内容（缩进为 0）说明块是空的
      if (lineIndent === 0) break;
      indent = lineIndent;
    }
    if (lineIndent < indent) break;
    collected.push(line.slice(indent));
    index += 1;
  }

  while (collected.length && !collected[collected.length - 1].trim()) collected.pop();
  const text = folded ? collected.join(' ').replace(/[ \t]+/g, ' ').trim() : collected.join('\n');
  return { text, next: index };
}

export function parseFrontmatter(block: string): FrontmatterEntry[] {
  const lines = block.split('\n');
  const entries: FrontmatterEntry[] = [];
  let index = 0;

  while (index < lines.length) {
    const line = lines[index];
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) {
      index += 1;
      continue;
    }
    const match = KEY_LINE.exec(line);
    if (!match) {
      index += 1;
      continue;
    }
    const key = match[1];
    let rest = match[2].trim();
    index += 1;

    // 块标量
    if (/^[|>][+-]?$/.test(rest)) {
      const scalar = readBlockScalar(lines, index, rest.startsWith('>'));
      entries.push({ key, value: scalar.text });
      index = scalar.next;
      continue;
    }

    // 块列表：key 后面紧跟若干 `- item`
    if (!rest) {
      const items: string[] = [];
      let cursor = index;
      while (cursor < lines.length) {
        const itemMatch = LIST_ITEM.exec(lines[cursor]);
        if (!itemMatch) break;
        items.push(unquote(itemMatch[1]));
        cursor += 1;
      }
      if (items.length) {
        entries.push({ key, value: '', list: items });
        index = cursor;
        continue;
      }
    }

    const inline = asInlineList(rest);
    if (inline) {
      entries.push({ key, value: '', list: inline });
      continue;
    }
    entries.push({ key, value: unquote(rest) });
  }

  return entries;
}

/**
 * 把文本拆成「文件头 + 正文」。没有文件头时 entries 为 null、body 原样返回。
 */
export function splitFrontmatter(input?: string | null): SplitFrontmatterResult {
  const text = String(input ?? '').replace(/\r\n?/g, '\n');
  const body = { entries: null as FrontmatterEntry[] | null, body: text };
  if (!text) return body;

  const lines = text.split('\n');
  if (!OPEN_FENCE.test(lines[0] ?? '')) return body;

  let close = -1;
  for (let i = 1; i < lines.length; i += 1) {
    if (CLOSE_FENCE.test(lines[i])) {
      close = i;
      break;
    }
  }
  if (close < 0) return body;

  const entries = parseFrontmatter(lines.slice(1, close).join('\n'));
  // 解析不出任何字段时按普通正文处理，避免把 `---` 分隔线内容吞掉
  if (!entries.length) return body;

  return {
    entries,
    body: lines.slice(close + 1).join('\n').replace(/^\n+/, '')
  };
}
