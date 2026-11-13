/**
 * 当前工作空间（前端本地状态）。
 *
 * 后端 `SpaceInterceptor` 从请求头 `x-space-id` 解析当前空间，
 * 因此这里只维护「用户选中了哪个空间」，并在 `lib/http.ts` 统一注入。
 *
 * 切换空间会派发 `faust:space-changed`，由 `SpaceProvider` 触发页面重挂载。
 */

export const SPACE_ID_HEADER = 'x-space-id';

const SPACE_ID_KEY = 'faust.spaceId';

export function readSpaceId(): number | null {
  if (typeof window === 'undefined') return null;
  const raw = Number(window.localStorage.getItem(SPACE_ID_KEY));
  return Number.isFinite(raw) && raw > 0 ? raw : null;
}

export function saveSpaceId(spaceId: number | null) {
  if (spaceId && spaceId > 0) {
    window.localStorage.setItem(SPACE_ID_KEY, String(spaceId));
  } else {
    window.localStorage.removeItem(SPACE_ID_KEY);
  }
  window.dispatchEvent(new CustomEvent('faust:space-changed'));
}

export function subscribeSpace(listener: () => void) {
  const handler = () => listener();
  window.addEventListener('faust:space-changed', handler);
  window.addEventListener('storage', handler);
  return () => {
    window.removeEventListener('faust:space-changed', handler);
    window.removeEventListener('storage', handler);
  };
}
