import * as React from 'react';
import type { PageResult } from '@/services/agent-center';

export interface UseInfiniteListResult<T> {
  /** 已加载的全部数据（首屏 + 后续累加） */
  list: T[];
  total: number;
  /** 首屏加载中（骨架屏态） */
  loading: boolean;
  /** 追加加载中（列表底部 loading 态） */
  loadingMore: boolean;
  error: string | null;
  /** 是否还有下一页 */
  hasMore: boolean;
  /** 追加下一页 */
  loadMore: () => void;
  /** 保持已加载数量重新拉取（发布 / 删除后刷新，不打断滚动位置） */
  reload: () => void;
  /** 回到第一页（筛选条件变化） */
  reset: () => void;
}

interface InfiniteState<T> {
  list: T[];
  total: number;
  loading: boolean;
  loadingMore: boolean;
  error: string | null;
  /** 当前已加载条数，用于计算下一页的 current 与刷新时的 size */
  loadedSize: number;
  /** 后端返回空页（total 与实际数据不一致）时兜底，避免 observer 反复触发 */
  exhausted: boolean;
}

/**
 * 无限滚动列表数据源（卡片流用）。
 *
 * 与 `usePagedList` 的差别：不保留页码，只维护「已加载集合」，
 * 每次 loadMore 追加一页；筛选条件变化时整体重置回第一页。
 * 同样做了竞态淘汰：只有最新一次请求可以落地。
 */
export function useInfiniteList<T>(
  fetcher: (params: Record<string, unknown>) => Promise<PageResult<T>>,
  params: Record<string, unknown> = {},
  options: { pageSize?: number; enabled?: boolean } = {}
): UseInfiniteListResult<T> {
  const { pageSize = 12, enabled = true } = options;

  const [state, setState] = React.useState<InfiniteState<T>>({
    list: [],
    total: 0,
    loading: enabled,
    loadingMore: false,
    error: null,
    loadedSize: pageSize,
    exhausted: false
  });

  const seqRef = React.useRef(0);
  const inflightRef = React.useRef(false);
  const paramsRef = React.useRef(params);
  paramsRef.current = params;
  // 让 loadMore / reload 读到最新的已加载数量，而不必把它们挂进依赖数组
  const stateRef = React.useRef(state);
  stateRef.current = state;

  // 用序列化后的条件做依赖，避免调用方每次渲染都传新对象导致无限请求
  const paramsKey = JSON.stringify(params);

  const request = React.useCallback(
    async (current: number, size: number, mode: 'replace' | 'append') => {
      if (!enabled) return;
      const seq = seqRef.current + 1;
      seqRef.current = seq;
      inflightRef.current = true;
      setState((prev) => ({
        ...prev,
        loading: mode === 'replace',
        loadingMore: mode === 'append',
        error: null
      }));
      try {
        const result = await fetcher({ ...paramsRef.current, current, size });
        if (seq !== seqRef.current) return;
        const records = result?.records ?? [];
        setState((prev) => ({
          list: mode === 'append' ? [...prev.list, ...records] : records,
          total: result?.total ?? 0,
          loading: false,
          loadingMore: false,
          error: null,
          loadedSize: mode === 'append' ? prev.loadedSize + size : size,
          exhausted: mode === 'append' && records.length === 0
        }));
      } catch (error) {
        if (seq !== seqRef.current) return;
        setState((prev) => ({
          ...prev,
          // 首屏失败保留空列表；追加失败保留已有数据，避免整屏闪空
          list: mode === 'append' ? prev.list : [],
          loading: false,
          loadingMore: false,
          error: error instanceof Error ? error.message : '加载失败'
        }));
      } finally {
        if (seq === seqRef.current) inflightRef.current = false;
      }
    },
    [enabled, fetcher]
  );

  React.useEffect(() => {
    // 条件变化：先清空，回到骨架屏态，避免旧数据与新条件错位
    setState((prev) => ({
      ...prev,
      list: [],
      total: 0,
      loading: enabled,
      loadingMore: false,
      error: null,
      loadedSize: pageSize,
      exhausted: false
    }));
    request(1, pageSize, 'replace');
    // paramsKey 变化即视为条件变化 → 回到第一页
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [paramsKey, enabled]);

  const loadMore = React.useCallback(() => {
    if (!enabled || inflightRef.current) return;
    const { loading, error, list, total, loadedSize, exhausted } = stateRef.current;
    if (loading || error || exhausted || list.length >= total) return;
    request(Math.floor(loadedSize / pageSize) + 1, pageSize, 'append');
  }, [enabled, pageSize, request]);

  const reload = React.useCallback(() => {
    if (!enabled) return;
    // 保持已加载数量，刷新后不会把用户已滚出来的卡片缩回第一页。
    // 这里不拦 in-flight：发布 / 删除后的刷新必须能抢先落地，旧的请求会被 seq 淘汰。
    request(1, Math.max(stateRef.current.loadedSize, pageSize), 'replace');
  }, [enabled, pageSize, request]);

  const reset = React.useCallback(() => {
    if (!enabled) return;
    setState((prev) => ({
      ...prev,
      list: [],
      total: 0,
      error: null,
      loadedSize: pageSize,
      exhausted: false
    }));
    request(1, pageSize, 'replace');
  }, [enabled, pageSize, request]);

  const hasMore = !state.exhausted && state.total > state.list.length;

  return { ...state, hasMore, loadMore, reload, reset };
}
