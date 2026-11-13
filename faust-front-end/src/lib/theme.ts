/**
 * 明暗主题。
 *
 * 默认深色（Aurora Obsidian），与 IDE / 终端场景一致；
 * 用户手动切换后写入 localStorage，刷新保持一致。
 */

export type Theme = 'dark' | 'light';

const THEME_KEY = 'faust.theme';

const THEME_COLOR: Record<Theme, string> = {
  dark: '#0b0a14',
  light: '#fafafd'
};

export function readTheme(): Theme {
  const raw = window.localStorage.getItem(THEME_KEY);
  return raw === 'light' ? 'light' : 'dark';
}

export function applyTheme(theme: Theme) {
  const root = document.documentElement;
  root.classList.toggle('dark', theme === 'dark');
  root.style.colorScheme = theme;
  window.localStorage.setItem(THEME_KEY, theme);
  document.querySelector('meta[name="theme-color"]')?.setAttribute('content', THEME_COLOR[theme]);
}
