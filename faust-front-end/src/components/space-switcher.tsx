import * as React from 'react';
import { useNavigate } from 'react-router-dom';
import { Building2, Check, ChevronsUpDown, Loader2, Settings2, UserRound } from 'lucide-react';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger
} from '@/components/ui/dropdown-menu';
import { useSpace } from '@/components/space-provider';
import { cn } from '@/lib/utils';

/**
 * 侧边栏顶部的空间切换器：个人空间 / 我创建的团队空间 / 我加入的团队空间。
 * 切换后写入 localStorage，页面以 spaceId 为 key 重新挂载。
 */
export function SpaceSwitcher() {
  const { spaces, current, loading, switchSpace } = useSpace();
  const navigate = useNavigate();

  const personal = spaces.filter((space) => space.personal);
  const owned = spaces.filter((space) => !space.personal && space.owner);
  const joined = spaces.filter((space) => !space.personal && !space.owner);

  const renderItem = (space: (typeof spaces)[number]) => {
    const active = current?.spaceId === space.spaceId;
    return (
      <DropdownMenuItem
        key={space.spaceId}
        onSelect={() => {
          // 切换空间后统一回到「智能体」页：原页面的列表、详情都只属于上一个空间
          if (active) return;
          switchSpace(space.spaceId);
          navigate('/');
        }}
        className="gap-2"
      >
        {space.personal ? (
          <UserRound className="h-3.5 w-3.5 shrink-0 text-muted-foreground" />
        ) : (
          <Building2 className="h-3.5 w-3.5 shrink-0 text-muted-foreground" />
        )}
        <span className="min-w-0 flex-1 truncate" title={space.name}>
          {space.name}
        </span>
        <span className="shrink-0 text-[10.5px] text-muted-foreground">
          {space.personal ? '个人' : `${space.memberCount ?? 0} 人`}
        </span>
        {active ? <Check className="h-3.5 w-3.5 shrink-0 text-primary" /> : null}
      </DropdownMenuItem>
    );
  };

  return (
    <div className="relative px-3 pb-1">
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <button
            type="button"
            className={cn(
              'group flex w-full items-center gap-2.5 rounded-[10px] border border-sidebar-border bg-sidebar-hover/70 px-2.5 py-2 text-left',
              'transition-colors duration-200 hover:bg-sidebar-accent'
            )}
          >
            <span className="relative flex h-7 w-7 shrink-0 items-center justify-center rounded-[8px] bg-gradient-to-br from-[hsl(178_62%_42%)] to-[hsl(244_80%_58%)] text-white shadow-glow">
              {current?.personal ? (
                <UserRound className="h-3.5 w-3.5" />
              ) : (
                <Building2 className="h-3.5 w-3.5" />
              )}
            </span>
            <span className="min-w-0 flex-1">
              <span className="block truncate text-[12.5px] font-medium text-sidebar-foreground">
                {loading && !current ? '加载中…' : (current?.name ?? '未选择空间')}
              </span>
              <span className="block truncate text-[10.5px] text-sidebar-muted">
                {current
                  ? current.personal
                    ? '个人空间 · 仅自己'
                    : `团队空间 · ${current.memberCount ?? 0} 人`
                  : '工作空间'}
              </span>
            </span>
            {loading ? (
              <Loader2 className="h-3.5 w-3.5 shrink-0 animate-spin text-sidebar-muted" />
            ) : (
              <ChevronsUpDown className="h-3.5 w-3.5 shrink-0 text-sidebar-muted transition-transform duration-300 group-hover:translate-y-[1px]" />
            )}
          </button>
        </DropdownMenuTrigger>
        <DropdownMenuContent side="bottom" align="start" className="w-[236px]">
          <DropdownMenuLabel>切换工作空间</DropdownMenuLabel>
          {personal.length ? renderItem(personal[0]) : null}
          {owned.length ? (
            <>
              <DropdownMenuSeparator />
              <DropdownMenuLabel>我创建的</DropdownMenuLabel>
              {owned.map(renderItem)}
            </>
          ) : null}
          {joined.length ? (
            <>
              <DropdownMenuSeparator />
              <DropdownMenuLabel>我加入的</DropdownMenuLabel>
              {joined.map(renderItem)}
            </>
          ) : null}
          <DropdownMenuSeparator />
          <DropdownMenuItem onSelect={() => navigate('/spaces')}>
            <Settings2 className="h-3.5 w-3.5" />
            空间
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
    </div>
  );
}
