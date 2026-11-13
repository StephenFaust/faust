import * as React from 'react';

/**
 * 平滑打字机：把突发到达的 chunk 放进缓冲队列，用 requestAnimationFrame 匀速吐出。
 *
 * 沿用 bop 版本的调参（后端链路仍会成块突发），差异只有类型化与 target 语义：
 *   process —— 思考块（流式过程中的正文）
 *   final   —— 流关闭后合并出的最终正文（以更快字速铺出）
 *
 * 收尾约定：`onDone` 只在 `flush()` / `finish()` 时触发，不因缓冲临时清空自动触发，
 * 否则流式过程中 chunk 间断到达会误把「正在回复」状态解除。
 */
const BASE_CPS = 70;
const MAX_CPS = 200;
const MAX_CPS_FAST = 480;
const RATE_TAU = 0.8;
const SUPPLY_MARGIN = 1.15;
const CATCHUP = 0.3;
const CPS_RAMP = 0.12;
const MAX_CHARS_PER_FRAME = 12;

export type TypewriterTarget = 'process' | 'final';
export type CommitFn = (text: string, target: TypewriterTarget) => void;

interface Block {
  text: string;
  target: TypewriterTarget;
  fast: boolean;
}

export interface Typewriter {
  push: (text: string, target?: TypewriterTarget, fast?: boolean) => void;
  flush: () => void;
  clear: () => void;
  enqueue: (callback: () => void) => void;
  finish: () => void;
}

export function useTypewriter(commit: CommitFn, onDone?: () => void): Typewriter {
  const commitRef = React.useRef(commit);
  commitRef.current = commit;
  const onDoneRef = React.useRef(onDone);
  onDoneRef.current = onDone;

  const bufferRef = React.useRef<Block[]>([]);
  const rafRef = React.useRef(0);
  const lastRef = React.useRef(0);
  const accRef = React.useRef(0);
  const cpsRef = React.useRef(BASE_CPS);
  const rateRef = React.useRef(0);
  const arrivedRef = React.useRef(0);
  const queueRef = React.useRef<Array<() => void>>([]);
  const stepRef = React.useRef<(now: number) => void>(() => undefined);
  const pendingDoneRef = React.useRef(false);

  const stopLoop = () => {
    if (rafRef.current) {
      cancelAnimationFrame(rafRef.current);
      rafRef.current = 0;
    }
    lastRef.current = 0;
    accRef.current = 0;
    cpsRef.current = BASE_CPS;
  };

  const drainQueue = () => {
    const queue = queueRef.current;
    queueRef.current = [];
    queue.forEach((callback) => callback());
  };

  const fireDone = () => {
    pendingDoneRef.current = false;
    onDoneRef.current?.();
  };

  const finishLoop = () => {
    stopLoop();
    drainQueue();
    fireDone();
  };

  const renderImmediate = () => {
    const blocks = bufferRef.current;
    bufferRef.current = [];
    blocks.forEach((block) => {
      if (block.text) commitRef.current(block.text, block.target);
    });
    stopLoop();
    drainQueue();
  };

  stepRef.current = (now: number) => {
    const prev = lastRef.current || now;
    lastRef.current = now;
    const dt = Math.min(now - prev, 100) / 1000;

    const instant = arrivedRef.current / Math.max(dt, 0.001);
    arrivedRef.current = 0;
    rateRef.current += (instant - rateRef.current) * Math.min(1, dt / RATE_TAU);

    const head = bufferRef.current[0];
    const pending = head ? head.text.length : 0;
    const cpsMax = head?.fast ? MAX_CPS_FAST : MAX_CPS;
    const target = Math.min(cpsMax, Math.max(BASE_CPS, rateRef.current * SUPPLY_MARGIN + pending * CATCHUP));

    if (head?.fast) cpsRef.current = cpsMax;
    else cpsRef.current += (target - cpsRef.current) * CPS_RAMP;

    accRef.current += dt * cpsRef.current;
    let count = Math.floor(accRef.current);
    if (count > 0) {
      if (count > MAX_CHARS_PER_FRAME) count = MAX_CHARS_PER_FRAME;
      if (count > pending) count = pending;
      if (count > 0) {
        accRef.current -= count;
        const segment = bufferRef.current[0];
        const out = segment.text.slice(0, count);
        segment.text = segment.text.slice(count);
        if (!segment.text) bufferRef.current.shift();
        if (out) commitRef.current(out, segment.target);
      }
    }

    if (bufferRef.current.length) {
      rafRef.current = requestAnimationFrame((time) => stepRef.current(time));
    } else {
      stopLoop();
      drainQueue();
      if (pendingDoneRef.current) fireDone();
    }
  };

  const push = (text: string, target: TypewriterTarget = 'process', fast = false) => {
    if (!text) return;
    arrivedRef.current += text.length;
    bufferRef.current.push({ text, target, fast });
    if (rafRef.current) return;

    if (typeof document !== 'undefined' && document.hidden) {
      renderImmediate();
      return;
    }

    lastRef.current = 0;
    accRef.current = 0;
    const head = bufferRef.current[0];
    if (head?.text) {
      const count = Math.min(head.text.length, fast ? 12 : 3);
      const out = head.text.slice(0, count);
      head.text = head.text.slice(count);
      if (!head.text) bufferRef.current.shift();
      commitRef.current(out, head.target);
    }
    if (bufferRef.current.length) {
      rafRef.current = requestAnimationFrame((time) => stepRef.current(time));
    } else {
      stopLoop();
      drainQueue();
      if (pendingDoneRef.current) fireDone();
    }
  };

  const enqueue = (callback: () => void) => {
    if (!callback) return;
    if (!bufferRef.current.length && !rafRef.current) {
      callback();
      return;
    }
    queueRef.current.push(callback);
  };

  const flush = () => {
    const blocks = bufferRef.current;
    bufferRef.current = [];
    blocks.forEach((block) => {
      if (block.text) commitRef.current(block.text, block.target);
    });
    finishLoop();
  };

  const finish = () => {
    if (bufferRef.current.length || rafRef.current) {
      pendingDoneRef.current = true;
      return;
    }
    drainQueue();
    fireDone();
  };

  const clear = () => {
    bufferRef.current = [];
    queueRef.current = [];
    pendingDoneRef.current = false;
    stopLoop();
  };

  React.useEffect(() => stopLoop, []);

  // 标签页切到后台时 rAF 会被暂停：直接落盘，避免切回可见才「赶工」补播
  React.useEffect(() => {
    if (typeof document === 'undefined' || !document.addEventListener) return undefined;
    const onHide = () => {
      if (!document.hidden) return;
      if (bufferRef.current.length) renderImmediate();
      if (pendingDoneRef.current) fireDone();
    };
    document.addEventListener('visibilitychange', onHide);
    return () => document.removeEventListener('visibilitychange', onHide);
  }, []);

  return { push, flush, clear, enqueue, finish };
}
