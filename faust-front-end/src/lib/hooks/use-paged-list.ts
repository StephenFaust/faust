import * as React from 'react';
import type { PageResult } from '@/services/agent-center';

export interface PagedListState<T> {
  list: T[];
  total: number;
  current: number;
  pageSize: number;
  loading: boolean;
  error: string | null;
}

export interface UsePagedListResult<T> extends PagedListState<T> {
  /** 重新拉取当前页（删除 / 发布后刷新用） */
  reload: () => void;
  /** 回到第一页重新拉取（筛选条件变化用） */
  reset: () => void;
  setPage: (page: number) => void;
  setPageSize: (size: number) => void;
}

/**
 * 分页列表数据源。
 *
 * 对应 bop 版的 `useTableData`（ahooks useAntdTable），但去掉了与 antd Form 的耦合：
 * 筛选条件由页面自己维护并作为 `params` 传入，此 hook 只负责分页、竞态淘汰和错误态。
 */
export function usePagedList<T>(
  fetcher: (params: Record<string, unknown>) => Promise<PageResult<T>>,
  params: Record<string, unknown> = {},
  options: { pageSize?: number; enabled?: boolean } = {}
): UsePagedListResult<T> {
  const { pageSize: initialPageSize = 10, enabled = true } = options;

  const [state, setState] = React.useState<PagedListState<T>>({
    list: [],
    total: 0,
    current: 1,
    pageSize: initialPageSize,
    loading: enabled,
    error: null
  });

  const seqRef = React.useRef(0);
  // 用序列化后的条件做依赖，避免调用方每次渲染都传新对象导致无限请求
  const paramsKey = JSON.stringify(params);
  const paramsRef = React.useRef(params);
  paramsRef.current = params;

  const fetchPage = React.useCallback(
    async (page: number, size: number, options?: { silent?: boolean }) => {
      if (!enabled) return;
      const seq = seqRef.current + 1;
      seqRef.current = seq;
      setState((prev) => ({ ...prev, loading: true, error: null }));
      try {
        const result = await fetcher({ ...paramsRef.current, current: page, size });
        if (seq !== seqRef.current) return;
        setState({
          list: result?.records ?? [],
          total: result?.total ?? 0,
          current: page,
          pageSize: size,
          loading: false,
          error: null
        });
      } catch (error) {
        if (seq !== seqRef.current) return;
        const message = error instanceof Error ? error.message : '加载失败';
        setState((prev) => ({
          ...prev,
          // 首次加载失败保留空列表；翻页失败保留上一页数据，避免整屏闪空
          list: options?.silent ? prev.list : [],
          loading: false,
          error: message
        }));
      }
    },
    [enabled, fetcher]
  );

  React.useEffect(() => {
    fetchPage(1, initialPageSize);
    // paramsKey 变化即视为条件变化 → 回到第一页
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [paramsKey, enabled]);

  return {
    ...state,
    reload: () => fetchPage(state.current, state.pageSize, { silent: true }),
    reset: () => fetchPage(1, state.pageSize),
    setPage: (page: number) => fetchPage(page, state.pageSize),
    setPageSize: (size: number) => fetchPage(1, size)
  };
}

/** 一次性拉取（下拉候选、字典等），带竞态淘汰 */
export function useAsyncData<T>(
  loader: (() => Promise<T>) | null,
  deps: React.DependencyList = []
): { data: T | null; loading: boolean; error: string | null; reload: () => void } {
  const [data, setData] = React.useState<T | null>(null);
  const [loading, setLoading] = React.useState(!!loader);
  const [error, setError] = React.useState<string | null>(null);
  const seqRef = React.useRef(0);
  const [tick, setTick] = React.useState(0);

  React.useEffect(() => {
    if (!loader) {
      setLoading(false);
      return;
    }
    const seq = seqRef.current + 1;
    seqRef.current = seq;
    setLoading(true);
    setError(null);
    loader()
      .then((result) => {
        if (seq !== seqRef.current) return;
        setData(result);
        setLoading(false);
      })
      .catch((err) => {
        if (seq !== seqRef.current) return;
        setError(err instanceof Error ? err.message : '加载失败');
        setLoading(false);
      });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, tick]);

  return { data, loading, error, reload: () => setTick((value) => value + 1) };
}
