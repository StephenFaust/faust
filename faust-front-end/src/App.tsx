import * as React from 'react';
import { Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { AppLayout } from '@/components/layout/app-layout';
import { SpaceBoundary } from '@/components/space-provider';

const AgentsPage = React.lazy(() => import('@/pages/agents'));
const SkillAdminPage = React.lazy(() => import('@/pages/skill-admin'));
const ToolAdminPage = React.lazy(() => import('@/pages/tool-admin'));
const DataSourceAdminPage = React.lazy(() => import('@/pages/datasource-admin'));
const SpacesPage = React.lazy(() => import('@/pages/spaces'));
const ChatPage = React.lazy(() => import('@/pages/chat'));

function PageFallback() {
  return (
    <div className="flex min-h-[320px] items-center justify-center">
      <span className="h-5 w-5 animate-spin rounded-full border-2 border-primary/25 border-t-primary" />
    </div>
  );
}

/**
 * 资源页统一包一层 SpaceBoundary：切换工作空间后整页重挂载，
 * 列表回到第一页、详情/表单状态清零，避免把上一个空间的数据带进来。
 */
function SpacePage({ children }: { children: React.ReactNode }) {
  return (
    <SpaceBoundary>
      <React.Suspense fallback={<PageFallback />}>{children}</React.Suspense>
    </SpaceBoundary>
  );
}

export default function App() {
  const location = useLocation();

  return (
    <Routes>
      {/* 智能体：工作台与 Agent 管理合并后的唯一入口 */}
      <Route
        path="/"
        element={
          <AppLayout>
            <SpacePage>
              <AgentsPage />
            </SpacePage>
          </AppLayout>
        }
      />
      {/* 旧入口兼容：历史链接 /agents 统一回到智能体 */}
      <Route path="/agents" element={<Navigate to="/" replace />} />
      <Route
        path="/skills"
        element={
          <AppLayout>
            <SpacePage>
              <SkillAdminPage />
            </SpacePage>
          </AppLayout>
        }
      />
      <Route
        path="/tools"
        element={
          <AppLayout>
            <SpacePage>
              <ToolAdminPage />
            </SpacePage>
          </AppLayout>
        }
      />
      <Route
        path="/datasource"
        element={
          <AppLayout>
            <SpacePage>
              <DataSourceAdminPage />
            </SpacePage>
          </AppLayout>
        }
      />
      {/* 空间管理：跨空间视角，不随当前空间重挂载 */}
      <Route
        path="/spaces"
        element={
          <AppLayout>
            <React.Suspense fallback={<PageFallback />}>
              <SpacesPage />
            </React.Suspense>
          </AppLayout>
        }
      />
      <Route
        path="/chat/:agentCode"
        element={
          <AppLayout flush>
            <SpaceBoundary>
              <React.Suspense fallback={<PageFallback />}>
                {/* 以 location.key 作为 key：每次从外部进入（含对同一 Agent 再次「新建对话」）
                    都重新挂载对话页，从而落在新会话而不是续上历史 */}
                <ChatPage key={location.key} />
              </React.Suspense>
            </SpaceBoundary>
          </AppLayout>
        }
      />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
