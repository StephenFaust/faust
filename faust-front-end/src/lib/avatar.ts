import type { CSSProperties } from 'react';

/**
 * 头像色相：只在大地色相邻的三个色相内取值（靛蓝 / 紫罗兰 / 青绿），
 * 保持「同一套色系」的系统感，而不是五种撞色乱飞。
 */
const HUE_SHIFT = [0, 22, -60];

/** 用名称做种子生成稳定的渐变头像底色，同一实体每次渲染颜色一致 */
export function gradientAvatarStyle(seed: string): CSSProperties {
  let hash = 0;
  for (let i = 0; i < seed.length; i += 1) hash = (hash * 31 + seed.charCodeAt(i)) % 9973;
  const base = 244 + HUE_SHIFT[hash % HUE_SHIFT.length];
  return {
    backgroundImage: `linear-gradient(142deg, hsl(${base} 92% 70%) 0%, hsl(${base + 16} 78% 50%) 100%)`
  };
}
