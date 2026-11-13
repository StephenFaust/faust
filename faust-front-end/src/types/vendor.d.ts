declare module 'remend' {
  interface RemendOptions {
    linkMode?: 'text-only' | 'keep';
  }
  export default function remend(text: string, options?: RemendOptions): string;
}

declare module 'markdown-it-cjk-friendly' {
  import type MarkdownIt from 'markdown-it';
  const plugin: (md: MarkdownIt, options?: Record<string, unknown>) => void;
  export default plugin;
}
