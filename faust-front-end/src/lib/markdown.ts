/**
 * Markdown -> HTML，基于 markdown-it（^14）。
 *
 * 安全：`html: false`，模型输出中的 HTML 标签会被转义，消除 XSS 面；
 * 外链补 `target="_blank" rel="noopener noreferrer"`，危险协议由 validateLink 拦截。
 *
 * CJK 强调修复：见 markdown-it-cjk-friendly，解决 `**` 紧贴中文标点时闭合失败。
 *
 * 块级增量渲染：流式场景每帧整篇 parse 是 O(N²)，这里切成「已完成块 + 未完成尾部」，
 * 已完成块的 HTML 按原文缓存，每帧只 parse 尾部。
 */
import MarkdownIt from 'markdown-it';
import cjkFriendly from 'markdown-it-cjk-friendly';
import remend from 'remend';

const md = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: true,
  typographer: false
});

md.use(cjkFriendly as never);

const defaultLinkOpen =
  md.renderer.rules.link_open ||
  ((tokens, idx, options, _env, self) => self.renderToken(tokens, idx, options));

md.renderer.rules.link_open = (tokens, idx, options, env, self) => {
  const href = tokens[idx].attrGet('href') || '';
  if (/^https?:\/\//i.test(href)) {
    tokens[idx].attrSet('target', '_blank');
    tokens[idx].attrSet('rel', 'noopener noreferrer');
  }
  return defaultLinkOpen(tokens, idx, options, env, self);
};

const REMEND_OPTIONS = { linkMode: 'text-only' as const };

/** 流式渲染入口：返回按块切好的 HTML 片段数组 */
export function mdParseBlocks(text: string): string[] {
  if (!text) return [];
  const { blocks, tail } = splitIntoBlocks(String(text));
  const html: string[] = [];
  for (const block of blocks) html.push(renderCached(block));
  html.push(renderOne(tail));
  return html;
}

/** 静态渲染入口（详情页系统提示词 / SKILL）：一次性整篇渲染 */
export function mdRender(text?: string | null): string {
  if (!text) return '';
  return md.render(text);
}

const BLOCK_HTML_CACHE = new Map<string, string>();
const BLOCK_CACHE_LIMIT = 800;

function renderCached(blockText: string): string {
  const cached = BLOCK_HTML_CACHE.get(blockText);
  if (cached !== undefined) return cached;
  const html = md.render(blockText);
  if (BLOCK_HTML_CACHE.size >= BLOCK_CACHE_LIMIT) BLOCK_HTML_CACHE.clear();
  BLOCK_HTML_CACHE.set(blockText, html);
  return html;
}

function renderOne(text: string): string {
  if (!text || !text.trim()) return '';
  return md.render(remend(text, REMEND_OPTIONS));
}

const BLANK_LINE = /^\s*$/;
const FENCE_LINE = /^ {0,3}(`{3,}|~{3,})/;
const LIST_START = /^\s*([-*+]|\d{1,9}[.)])(\s+|$)/;
const ORDERED_LIST_START = /^\s*\d{1,9}[.)](\s+|$)/;

interface LineInfo {
  start: number;
  end: number;
  text: string;
}

export function splitIntoBlocks(src: string): { blocks: string[]; tail: string } {
  if (!src) return { blocks: [], tail: '' };
  const lines = toLines(src);
  const n = lines.length;
  const ranges: Array<{ start: number; end: number; text: string }> = [];
  let i = 0;

  while (i < n) {
    if (BLANK_LINE.test(lines[i].text)) {
      i += 1;
      continue;
    }
    const startLine = i;
    let j = i;
    const fenceMatch = FENCE_LINE.exec(lines[i].text);
    if (fenceMatch) {
      const open = fenceMatch[1];
      j += 1;
      while (j < n) {
        const m = FENCE_LINE.exec(lines[j].text);
        if (
          m &&
          m[1][0] === open[0] &&
          m[1].length >= open.length &&
          BLANK_LINE.test(lines[j].text.slice(m[1].length))
        ) {
          j += 1;
          break;
        }
        j += 1;
      }
    } else {
      while (j < n && !BLANK_LINE.test(lines[j].text)) j += 1;
    }
    ranges.push({
      start: lines[startLine].start,
      end: j < n ? lines[j].start : lines[startLine].end,
      text: lines[startLine].text
    });
    i = j;
  }

  let committed = 0;
  for (let b = 0; b < ranges.length - 1; b += 1) {
    if (isSameList(ranges[b].text, ranges[b + 1].text)) break;
    committed = b + 1;
  }

  const blocks: string[] = [];
  for (let b = 0; b < committed; b += 1) {
    blocks.push(src.slice(ranges[b].start, ranges[b].end));
  }
  const tailStart = committed < ranges.length ? ranges[committed].start : src.length;
  return { blocks, tail: src.slice(tailStart) };
}

function isSameList(prevLine: string, nextLine: string): boolean {
  if (!LIST_START.test(prevLine) || !LIST_START.test(nextLine)) return false;
  return ORDERED_LIST_START.test(prevLine) === ORDERED_LIST_START.test(nextLine);
}

function toLines(src: string): LineInfo[] {
  const lines: LineInfo[] = [];
  let pos = 0;
  const end = src.length;
  while (pos <= end) {
    const nl = src.indexOf('\n', pos);
    const lineEnd = nl === -1 ? end : nl;
    lines.push({ start: pos, end: lineEnd, text: src.slice(pos, lineEnd) });
    if (nl === -1) break;
    pos = nl + 1;
  }
  return lines;
}
