import { Badge } from '@/components/ui/badge';
import { STATUS_MAP } from '@/lib/constants';

export function StatusBadge({ status }: { status?: string }) {
  const config = STATUS_MAP[String(status || '').toUpperCase()];
  if (!config) {
    return <Badge tone="muted">{status || '未知'}</Badge>;
  }
  return <Badge tone={config.tone}>{config.text}</Badge>;
}

export function TypeBadge({ type }: { type?: string }) {
  const normalized = String(type || '').toUpperCase();
  const toneMap: Record<string, 'info' | 'success' | 'purple' | 'warning' | 'muted'> = {
    HTTP: 'info',
    DB: 'success',
    SUB_AGENT: 'purple',
    NATIVE: 'warning'
  };
  return <Badge tone={toneMap[normalized] || 'muted'}>{normalized || '未知'}</Badge>;
}

export function TagList({ tags, max = 4 }: { tags?: string[]; max?: number }) {
  const list = Array.isArray(tags) ? tags.filter(Boolean) : [];
  if (list.length === 0) return null;
  const visible = list.slice(0, max);
  const rest = list.length - visible.length;
  return (
    <div className="flex flex-wrap items-center gap-1">
      {visible.map((tag) => (
        <Badge key={tag} tone="outline" className="font-normal">
          {tag}
        </Badge>
      ))}
      {rest > 0 ? <Badge tone="muted">+{rest}</Badge> : null}
    </div>
  );
}
