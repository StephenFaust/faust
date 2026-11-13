/**
 * 开源版的「当前用户」。
 *
 * 后端 `LoginInterceptor` 从请求头 `user-id` 读取用户 ID，
 * 因此这里只需要在本地维护一个可切换的 userId / 昵称，并持久化到 localStorage。
 * 部署到带统一登录的网关时，把 `readUser()` 换成从 Cookie / 网关 Header 读取即可。
 *
 * 请求头名统一由 `USER_ID_HEADER` 提供，避免各处手写字面量导致前后端协议漂移。
 */

export const USER_ID_HEADER = 'user-id';

const UC_ID_KEY = 'faust.ucId';
const UC_NAME_KEY = 'faust.ucName';

export interface CurrentUser {
  ucId: number;
  ucName: string;
}

export function readUser(): CurrentUser {
  const rawId = Number(window.localStorage.getItem(UC_ID_KEY));
  return {
    ucId: Number.isFinite(rawId) && rawId > 0 ? rawId : 1,
    ucName: window.localStorage.getItem(UC_NAME_KEY) || '本地用户'
  };
}

export function saveUser(user: CurrentUser) {
  window.localStorage.setItem(UC_ID_KEY, String(user.ucId));
  window.localStorage.setItem(UC_NAME_KEY, user.ucName);
  window.dispatchEvent(new CustomEvent('faust:user-changed'));
}

export function subscribeUser(listener: (user: CurrentUser) => void) {
  const handler = () => listener(readUser());
  window.addEventListener('faust:user-changed', handler);
  window.addEventListener('storage', handler);
  return () => {
    window.removeEventListener('faust:user-changed', handler);
    window.removeEventListener('storage', handler);
  };
}
