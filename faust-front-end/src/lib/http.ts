/**
 * 轻量 HTTP 客户端
 *
 * 契约（对齐 faust-back-end `ApiResponse`）：
 *   { code: 0, message: 'success', data: <业务数据> }
 * - code === 0 视为成功，直接返回 data
 * - 失败抛出 ApiError，message 取后端 message
 *
 * 身份：后端 `LoginInterceptor` 从请求头 `user-id` 读取用户 ID，
 * 开源版由前端本地设置（见 `lib/session.ts`）后统一注入。
 */

import { USER_ID_HEADER } from '@/lib/session';
import { SPACE_ID_HEADER, readSpaceId } from '@/lib/space';

const SUCCESS_CODES = [0, 200];

export class ApiError extends Error {
  code?: number;
  status?: number;

  constructor(message: string, code?: number, status?: number) {
    super(message);
    this.name = 'ApiError';
    this.code = code;
    this.status = status;
  }
}

export interface ApiResponse<T = unknown> {
  code: number;
  message?: string;
  msg?: string;
  data: T;
}

export function getUcId(): number {
  const raw = window.localStorage.getItem('faust.ucId');
  const parsed = Number(raw);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : 1;
}

export function getUcName(): string {
  return window.localStorage.getItem('faust.ucName') || '本地用户';
}

function buildHeaders(extra?: HeadersInit): Headers {
  const headers = new Headers(extra);
  headers.set('x-requested-with', 'XMLHttpRequest');
  headers.set(USER_ID_HEADER, String(getUcId()));
  // 当前工作空间：后端据此做列表过滤与创建归属，缺省时后端回落到个人空间
  const spaceId = readSpaceId();
  if (spaceId) headers.set(SPACE_ID_HEADER, String(spaceId));
  return headers;
}

function stringifyQuery(params?: Record<string, unknown>): string {
  if (!params) return '';
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value === undefined || value === null || value === '') return;
    if (Array.isArray(value)) {
      value.forEach((item) => search.append(key, String(item)));
    } else {
      search.append(key, String(value));
    }
  });
  const qs = search.toString();
  return qs ? `?${qs}` : '';
}

async function unwrap<T>(response: Response): Promise<T> {
  if (!response.ok) {
    throw new ApiError(`请求失败 ${response.status} ${response.statusText}`, undefined, response.status);
  }

  const text = await response.text();
  if (!text) return undefined as T;

  let payload: ApiResponse<T>;
  try {
    payload = JSON.parse(text);
  } catch {
    throw new ApiError('响应解析失败：返回内容不是合法 JSON', undefined, response.status);
  }

  if (SUCCESS_CODES.includes(payload.code)) {
    return payload.data;
  }
  throw new ApiError(payload.message || payload.msg || '请求失败', payload.code, response.status);
}

export async function get<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  const response = await fetch(`${url}${stringifyQuery(params)}`, {
    method: 'GET',
    credentials: 'include',
    headers: buildHeaders({ Accept: 'application/json' })
  });
  return unwrap<T>(response);
}

export async function post<T>(url: string, body?: unknown): Promise<T> {
  const isForm = body instanceof FormData;
  const response = await fetch(url, {
    method: 'POST',
    credentials: 'include',
    headers: buildHeaders(isForm ? { Accept: 'application/json' } : {
      Accept: 'application/json',
      'Content-Type': 'application/json; charset=utf-8'
    }),
    body: isForm ? body : JSON.stringify(body ?? {})
  });
  return unwrap<T>(response);
}
