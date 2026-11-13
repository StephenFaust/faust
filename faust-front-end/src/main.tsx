import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { Toaster } from 'sonner';
import App from './App';
import { ConfirmProvider } from '@/components/ui/confirm';
import { TooltipProvider } from '@/components/ui/tooltip';
import { SpaceProvider } from '@/components/space-provider';
import './index.css';

ReactDOM.createRoot(document.getElementById('root') as HTMLElement).render(
  <React.StrictMode>
    <BrowserRouter>
      <TooltipProvider delayDuration={200}>
        <ConfirmProvider>
          {/* 工作空间上下文：启动即拉取「我的空间」，请求头由 lib/http.ts 统一注入 */}
          <SpaceProvider>
            <App />
          </SpaceProvider>
          <Toaster
            position="top-center"
            // 背景 / 边框 / 文字色由 index.css 里覆盖的 sonner 变量统一接管（跟随明暗主题）
            toastOptions={{
              className: 'text-[13px] rounded-xl shadow-float'
            }}
          />
        </ConfirmProvider>
      </TooltipProvider>
    </BrowserRouter>
  </React.StrictMode>
);
