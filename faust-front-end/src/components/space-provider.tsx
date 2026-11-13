import * as React from 'react';
import { Building2 } from 'lucide-react';
import { fetchSpaceList, type SpaceInfo } from '@/services/space';
import { readSpaceId, saveSpaceId, subscribeSpace } from '@/lib/space';
import { subscribeUser } from '@/lib/session';

interface SpaceContextValue {
  spaces: SpaceInfo[];
  current: SpaceInfo | null;
  spaceId: number | null;
  loading: boolean;
  error: string | null;
  /** 切换到指定空间（写入 localStorage 并触发页面重挂载） */
  switchSpace: (spaceId: number) => void;
  /** 重新拉取空间列表（新建 / 解散 / 成员变更后调用） */
  reload: () => Promise<void>;
}

const SpaceContext = React.createContext<SpaceContextValue | null>(null);

/**
 * 工作空间上下文：启动时拉取「我的空间」，并把选中项落盘到 localStorage。
 *
 * 选择顺序：localStorage 里仍有效的空间 → 个人空间 → 列表第一个。
 * 切换用户时会重新拉取，若原空间不可见则自动回落到个人空间。
 */
export function SpaceProvider({ children }: { children: React.ReactNode }) {
  const [spaces, setSpaces] = React.useState<SpaceInfo[]>([]);
  const [currentId, setCurrentId] = React.useState<number | null>(() => readSpaceId());
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);

  const load = React.useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const list = await fetchSpaceList();
      setSpaces(list);
      const stored = readSpaceId();
      const matched =
        list.find((space) => space.spaceId === stored) ??
        list.find((space) => space.personal) ??
        list[0];
      if (matched && matched.spaceId !== stored) {
        saveSpaceId(matched.spaceId);
      }
      setCurrentId(matched ? matched.spaceId : null);
    } catch (e) {
      setError(e instanceof Error ? e.message : '加载工作空间失败');
    } finally {
      setLoading(false);
    }
  }, []);

  React.useEffect(() => {
    void load();
  }, [load]);

  // 切换空间（含其他标签页写入 localStorage）时同步本地状态
  React.useEffect(() => subscribeSpace(() => setCurrentId(readSpaceId())), []);

  // 切换用户后重新拉取：原空间可能不属于新用户
  React.useEffect(() => subscribeUser(() => void load()), [load]);

  const switchSpace = React.useCallback((spaceId: number) => {
    if (!spaceId) return;
    saveSpaceId(spaceId);
    setCurrentId(spaceId);
  }, []);

  const value = React.useMemo<SpaceContextValue>(() => {
    const current = spaces.find((space) => space.spaceId === currentId) ?? null;
    return {
      spaces,
      current,
      spaceId: current ? current.spaceId : null,
      loading,
      error,
      switchSpace,
      reload: load
    };
  }, [spaces, currentId, loading, error, switchSpace, load]);

  return <SpaceContext.Provider value={value}>{children}</SpaceContext.Provider>;
}

export function useSpace() {
  const context = React.useContext(SpaceContext);
  if (!context) {
    throw new Error('useSpace 必须在 SpaceProvider 内使用');
  }
  return context;
}

/**
 * 以空间 ID 作为 key 包裹页面：切换空间时重新挂载，列表自动回到第一页，
 * 详情 / 表单等局部状态也一并清零，避免把上一个空间的数据带到新空间。
 */
export function SpaceBoundary({ children }: { children: React.ReactNode }) {
  const { spaceId } = useSpace();
  return <React.Fragment key={spaceId ?? 'none'}>{children}</React.Fragment>;
}

/** 页头里的「当前空间」提示，避免多空间下误操作 */
export function CurrentSpaceHint() {
  const { current, loading } = useSpace();
  if (loading && !current) return null;
  return (
    <span className="ml-1 inline-flex items-center gap-1 rounded-full bg-muted/70 px-2 py-[2px] align-middle text-[11px] font-normal text-muted-foreground ring-1 ring-inset ring-border/50">
      <Building2 className="h-3 w-3" />
      当前空间：{current?.name ?? '—'}
    </span>
  );
}
