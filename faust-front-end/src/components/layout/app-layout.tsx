import * as React from 'react';
import { NavLink } from 'react-router-dom';
import {
  Bot,
  Boxes,
  Building2,
  Database,
  Layers,
  MessageSquareCode,
  Moon,
  Settings2,
  Sparkles,
  Sun
} from 'lucide-react';
import { cn } from '@/lib/utils';
import { readUser, saveUser, subscribeUser, type CurrentUser } from '@/lib/session';
import { applyTheme, readTheme, type Theme } from '@/lib/theme';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger
} from '@/components/ui/dropdown-menu';
import {
  Dialog,
  DialogBody,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { NewChatDialog } from '@/components/new-chat-dialog';
import { SpaceSwitcher } from '@/components/space-switcher';

const NAV_ITEMS = [
  { to: '/', label: '智能体', icon: Bot, end: true },
  { to: '/skills', label: '技能', icon: Layers },
  { to: '/tools', label: '工具', icon: Boxes },
  { to: '/datasource', label: '数据源', icon: Database },
  { to: '/spaces', label: '空间', icon: Building2 }
];

const THEME_OPTIONS: { value: Theme; label: string; icon: typeof Moon }[] = [
  { value: 'dark', label: '深色', icon: Moon },
  { value: 'light', label: '浅色', icon: Sun }
];

export function AppLayout({ children, flush }: { children: React.ReactNode; flush?: boolean }) {
  const [user, setUser] = React.useState<CurrentUser>(() => readUser());
  const [open, setOpen] = React.useState(false);
  const [pickerOpen, setPickerOpen] = React.useState(false);
  const [draftId, setDraftId] = React.useState(String(user.ucId));
  const [draftName, setDraftName] = React.useState(user.ucName);
  const [theme, setTheme] = React.useState<Theme>(() => readTheme());

  React.useEffect(() => subscribeUser(setUser), []);
  React.useEffect(() => applyTheme(theme), [theme]);

  const submitUser = () => {
    const ucId = Number(draftId);
    if (!Number.isFinite(ucId) || ucId <= 0) return;
    saveUser({ ucId, ucName: draftName.trim() || '本地用户' });
    setOpen(false);
  };

  return (
    <div className="relative flex h-screen w-full overflow-hidden bg-background">
      {/* 背景光场：顶部品牌辉光 + 渐隐网格，制造纵深 */}
      <div aria-hidden className="pointer-events-none absolute inset-0 overflow-hidden">
        <div className="brand-glow absolute left-1/2 top-[-42%] h-[720px] w-[1240px] -translate-x-1/2 rounded-full blur-[130px]" />
        <div className="absolute left-[248px] right-0 top-0 h-[520px] grid-field opacity-[0.5]" />
      </div>

      <aside className="relative flex w-[248px] shrink-0 flex-col overflow-hidden border-r border-sidebar-border bg-sidebar text-sidebar-foreground">
        {/* 极光 + 噪声：侧边栏的氛围来源（浅色下自动减弱） */}
        <div aria-hidden className="aurora pointer-events-none absolute inset-0" />
        <div
          aria-hidden
          className="grain pointer-events-none absolute inset-0 opacity-[0.06] mix-blend-soft-light"
        />
        <div
          aria-hidden
          className="pointer-events-none absolute inset-x-0 top-0 h-px bg-gradient-to-r from-transparent via-sidebar-border to-transparent"
        />

        <div className="relative flex h-[68px] items-center gap-3 px-5">
          <span className="relative flex h-9 w-9 items-center justify-center rounded-[11px] bg-gradient-to-br from-[hsl(244_92%_66%)] to-[hsl(272_84%_52%)] shadow-glow">
            <span className="absolute inset-0 rounded-[11px] ring-1 ring-inset ring-white/25" />
            <Sparkles className="relative h-[17px] w-[17px] text-white" />
          </span>
          <div className="flex flex-col leading-none">
            <span className="font-display text-[17px] font-semibold tracking-[-0.02em] text-brand">
              Faust
            </span>
            <span className="mt-1 text-[9.5px] font-medium tracking-[0.2em] text-sidebar-muted">
              AGENT CENTER
            </span>
          </div>
        </div>

        {/* 空间切换器：个人空间 / 我创建的 / 我加入的 */}
        <SpaceSwitcher />

        <nav className="relative flex flex-1 flex-col gap-0.5 px-3 pb-3 pt-1">
          <p className="px-2.5 pb-2 pt-3 text-[11px] font-semibold tracking-[0.1em] text-sidebar-muted">
            控制台
          </p>
          {NAV_ITEMS.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              className={({ isActive }) =>
                cn(
                  'group relative flex h-9 items-center gap-2.5 rounded-[10px] px-2.5 text-[13px] font-medium transition-all duration-200',
                  isActive
                    ? 'bg-sidebar-accent text-sidebar-foreground shadow-[inset_0_0_0_1px_hsl(var(--sidebar-border))]'
                    : 'text-sidebar-muted hover:bg-sidebar-hover hover:text-sidebar-foreground'
                )
              }
            >
              {({ isActive }) => (
                <>
                  {/* 发光轨道：active 时点亮，带模糊光晕 */}
                  <span
                    className={cn(
                      'absolute left-0 top-1/2 h-5 w-[2px] -translate-y-1/2 rounded-full bg-gradient-to-b from-primary to-[hsl(178_62%_45%)] transition-all duration-300',
                      isActive
                        ? 'opacity-100 shadow-[0_0_10px_2px_hsl(var(--primary)/0.45)]'
                        : 'opacity-0'
                    )}
                  />
                  <item.icon
                    className={cn(
                      'h-[15px] w-[15px] transition-colors',
                      isActive ? 'text-primary' : 'group-hover:text-sidebar-foreground'
                    )}
                  />
                  {item.label}
                </>
              )}
            </NavLink>
          ))}

          <p className="px-2.5 pb-2 pt-5 text-[11px] font-semibold tracking-[0.1em] text-sidebar-muted">
            快捷入口
          </p>
          <button
            type="button"
            onClick={() => setPickerOpen(true)}
            className="group flex h-9 w-full items-center gap-2.5 rounded-[10px] px-2.5 text-[13px] text-sidebar-muted transition-all duration-200 hover:bg-sidebar-hover hover:text-sidebar-foreground"
          >
            <MessageSquareCode className="h-[15px] w-[15px] transition-colors group-hover:text-[hsl(178_80%_62%)]" />
            新建对话
          </button>
        </nav>

        <div className="relative border-t border-sidebar-border p-3">
          <div className="mb-2 flex items-center justify-between gap-1 rounded-[10px] bg-sidebar-hover p-1">
            <span className="pl-1.5 text-[10.5px] font-medium text-sidebar-muted">外观</span>
            <div className="flex items-center gap-0.5">
              {THEME_OPTIONS.map((option) => {
                const active = theme === option.value;
                return (
                  <button
                    key={option.value}
                    type="button"
                    onClick={() => setTheme(option.value)}
                    aria-pressed={active}
                    title={`切换到${option.label}`}
                    className={cn(
                      'flex h-6 items-center gap-1 rounded-[7px] px-1.5 text-[10.5px] font-medium transition-colors',
                      active
                        ? 'bg-sidebar-accent text-sidebar-foreground shadow-[inset_0_0_0_1px_hsl(var(--sidebar-border))]'
                        : 'text-sidebar-muted hover:text-sidebar-foreground'
                    )}
                  >
                    <option.icon className="h-3 w-3" />
                    {option.label}
                  </button>
                );
              })}
            </div>
          </div>

          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <button className="group flex w-full items-center gap-2.5 rounded-[10px] px-2 py-2 text-left transition-colors hover:bg-sidebar-hover">
                <span className="relative flex h-7 w-7 items-center justify-center rounded-full bg-gradient-to-br from-[hsl(244_70%_58%)] to-[hsl(272_60%_44%)] text-[11px] font-semibold text-white ring-1 ring-white/20">
                  {user.ucName.slice(0, 1).toUpperCase()}
                </span>
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-[12.5px] font-medium text-sidebar-foreground">
                    {user.ucName}
                  </span>
                  <span className="block truncate font-mono text-[10.5px] tracking-tight text-sidebar-muted">
                    ID {user.ucId}
                  </span>
                </span>
                <Settings2 className="h-3.5 w-3.5 text-sidebar-muted transition-transform duration-300 group-hover:rotate-45" />
              </button>
            </DropdownMenuTrigger>
            <DropdownMenuContent side="top" align="start" className="w-[224px]">
              <DropdownMenuLabel>当前身份</DropdownMenuLabel>
              <DropdownMenuItem
                onClick={() => {
                  setDraftId(String(user.ucId));
                  setDraftName(user.ucName);
                  setOpen(true);
                }}
              >
                <Settings2 className="h-3.5 w-3.5" />
                切换用户 ID
              </DropdownMenuItem>
              <DropdownMenuSeparator />
              <div className="px-2 py-1.5 text-[11px] leading-relaxed text-muted-foreground">
                开源版从本地读取身份，通过 <code className="text-[10.5px] text-primary">user-id</code>{' '}
                透传给后端。
              </div>
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
      </aside>

      {/*
        滚动策略：普通页面的滚动容器是 main，内层必须是「高度自适应」的块，
        否则内层被 flex-1 钉成一屏高、内容溢出后底部 py 不再生效，
        会出现「滚到底部内容贴着视口下沿」的问题。对话页仍需撑满一屏。
      */}
      <main
        className={cn(
          'relative z-10 flex min-w-0 flex-1 flex-col',
          flush ? 'overflow-hidden' : 'overflow-y-auto'
        )}
      >
        {flush ? (
          <div className="flex min-h-0 flex-1 flex-col">{children}</div>
        ) : (
          <div className="mx-auto w-full max-w-[1680px] px-6 pb-12 pt-7">{children}</div>
        )}
      </main>

      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent className="max-w-sm">
          <DialogHeader>
            <DialogTitle>切换本地身份</DialogTitle>
          </DialogHeader>
          <DialogBody className="flex flex-col gap-4">
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="user-id">用户 ID</Label>
              <Input
                id="user-id"
                value={draftId}
                onChange={(event) => setDraftId(event.target.value)}
                placeholder="例如 10086"
                inputMode="numeric"
              />
              <p className="text-[11.5px] leading-relaxed text-muted-foreground">
                用于会话隔离与工作空间权限校验，会写入请求头。
              </p>
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="uc-name">显示名称</Label>
              <Input
                id="uc-name"
                value={draftName}
                onChange={(event) => setDraftName(event.target.value)}
                placeholder="例如 张三"
              />
            </div>
          </DialogBody>
          <DialogFooter>
            <Button variant="outline" onClick={() => setOpen(false)}>
              取消
            </Button>
            <Button onClick={submitUser}>保存</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <NewChatDialog open={pickerOpen} onOpenChange={setPickerOpen} />
    </div>
  );
}
