# Faust · Agent Center (Web)

Faust Agent Center 的 Web 控制台，用于可视化编排 Agent、管理技能 / 工具 / 数据源，并与之流式对话。

## 技术栈

| 维度 | 选型 |
| --- | --- |
| 构建 | Vite 5 |
| 框架 | React 18 + TypeScript |
| 路由 | React Router 6 |
| 样式 | Tailwind CSS 3 + CSS Variables |
| 组件 | shadcn/ui 风格自建组件（Radix UI 无样式底座） |
| 图标 | lucide-react |
| 提示 | sonner |
| Markdown | markdown-it + markdown-it-cjk-friendly + remend |

不依赖任何内部私有包：所有组件、请求层、流式解析都在本仓库内实现。

## 设计系统

视觉基调 **Aurora Obsidian**（极光黑曜）：冷调中性底 + 电光靛蓝主色 + 青绿辅助 + 玫瑰强调。

| 维度 | 定义 |
| --- | --- |
| 主色 | `hsl(244 82% 57%)` 电光靛蓝，按钮白字满足 AA 对比度 |
| 辅助色 | `hsl(178 62% 45%)` 青绿，次级动作与标签 |
| 三级强调 | `hsl(347 92% 66%)` 玫瑰，差异化内容 |
| 圆角 | `--radius: 14px`；卡片 20px，按钮 10px，标签全圆 |
| 字体 | Space Grotesk（标题/数字）+ Inter（正文）+ JetBrains Mono（代码） |
| 阴影 | 双层低透明度投影，键名 `panel` / `float` |

> 阴影键名不能与 `colors` 里的 `card` / `popover` 重名，否则 Tailwind 会额外生成
> `shadow-{color}` 规则并覆盖真实阴影，导致深色下卡片没有投影。

材质层集中在 `src/index.css`，全部只改 `transform` / `opacity`，走 GPU 合成：

| 类名 | 作用 |
| --- | --- |
| `aurora` | 三色极光光斑，28s 慢漂移 |
| `grain` | SVG 噪声，给平色加胶片颗粒 |
| `grid-field` | 32px 网格，顶部渐隐制造纵深 |
| `spotlight` | 光标跟随聚光，由 `SpotlightCard` 写入 `--mx` / `--my`，不触发重渲染 |
| `ring-gradient` | 1px 渐变描边，hover 点亮 |
| `btn-sheen` | 主按钮高光扫过 |
| `stagger` | 子元素交错入场 |
| `skeleton-sheen` | 骨架屏扫光 |

主题默认深色，侧边栏左下角可切换，选择写入 `localStorage`；`index.html` 内联脚本在
首帧前应用，避免明暗闪烁。

> 修改 `tailwind.config.js` 后需要重启 dev server —— Vite 不会热更新 Tailwind 配置。

## 快速开始

```bash
npm install
cp .env.example .env
npm run dev          # http://localhost:5173
```

Vite 会把 `/api`、`/file` 代理到 `VITE_API_TARGET`（默认 `http://localhost:8080`），
所以只需保证后端在跑，无需额外配置 CORS。

## 目录结构

```
src/
├── components/
│   ├── ui/              # 基础组件（Button / Dialog / Select / DataTable …）
│   ├── layout/          # 应用框架与页面头
│   ├── markdown.tsx     # Markdown 渲染（支持流式分块）
│   └── status-badge.tsx # 状态 / 类型 / 标签
├── lib/
│   ├── http.ts          # fetch 封装：注入 user-id / x-space-id、解包 ApiResponse
│   ├── sse.ts           # SSE 帧解析 + 断连/超时兜底
│   ├── streamReducer.ts # 一轮回复的状态机（纯函数）
│   ├── markdown.ts      # Markdown 渲染内核
│   ├── session.ts       # 本地身份（用户 ID / 昵称）
│   ├── space.ts         # 当前工作空间（localStorage + 切换事件）
│   └── constants.ts     # 枚举与字典
├── pages/
│   ├── agents/          # 智能体（对话入口 + 配置管理合并页）
│   ├── skill-admin/     # 技能管理（技能本体 + 条目，独立于 Agent）
│   ├── tool-admin/      # 工具管理
│   ├── datasource-admin/# 数据源管理
│   ├── spaces/          # 空间管理（个人 / 我创建的 / 我加入的）
│   └── chat/            # 流式对话
└── services/
    ├── agent-center.ts  # Agent / 技能 / 工具 / 数据源接口（只做请求 + 解包）
    └── space.ts         # 工作空间与成员接口
```

## 页面与接口映射

| 页面 | 路由 | 依赖接口 |
| --- | --- | --- |
| 智能体 | `/` | `/api/agent/list`、`/detail/{code}`、`/create`、`/update`、`/publish`、`/takeOff`、`/delete`、`/api/skill/published-list`、`/api/tool/published-list`、`GET /api/dict/{type}` |
| 技能管理 | `/skills` | `/api/skill/list`、`/published-list`、`/detail/{skillId}`、`/create`、`/update`、`/publish`、`/takeOff`、`/delete` |
| 工具管理 | `/tools` | `/api/tool/list`、`/detail/{id}`、`/create`、`/update`、`/publish`、`/takeOff`、`/delete`、`/api/datasource/list`、`/api/agent/sub/agent/list` |
| 数据源管理 | `/datasource` | `/api/datasource/list`、`/detail/{id}`、`/create`、`/update`、`/delete`、`/connection/test` |
| 空间管理 | `/spaces` | `/api/space/list`、`/detail`、`/create`、`/rename`、`/dissolve`、`/api/space/member/*` |
| 流式对话 | `/chat/:agentCode` | `/api/agent/base/{code}`、`/api/agent/chat/list`、`/history`、`/env/var`、`/chat/start/sse`、`/file/upload` |

所有业务接口都会带上 `x-space-id` 请求头（当前工作空间），后端据此做列表过滤与创建归属；
缺省时后端回落到个人空间。空间自身的接口（`/api/space/**`）不参与该头部校验。

分页接口统一接收 `current` / `size`，返回 MyBatis-Plus 的 `Page` 结构
（`{ records, total, current, size }`）；对话列表用 `current` / `pageSize`。

### 流式对话协议

`POST /api/agent/chat/start/sse`，`text/event-stream`，帧类型：

| 帧 | 含义 |
| --- | --- |
| `{"chatId":"..."}` | 首帧回传会话 id（本仓库前端自行生成 chatId，可忽略） |
| `{"message":{"messageType":"assistant"},"chunk":"..."}` | 流式正文增量，进「思考块」 |
| `{"message":{"messageType":"tool-request"}}` | 工具调用请求 |
| `{"message":{"messageType":"tool-response"}}` | 工具返回 |
| `{"message":{"messageType":"final-assistant"}}` | 本轮最终结论，流关闭后合并铺出 |
| `event: error` | 错误帧，`{"error":true,"errorMessage":"..."}` |

前端对正文做过平滑打字机处理（`lib/hooks/use-typewriter.ts`），并兼容
`chunk` 增量与 `message.content` 累积快照两种下发语义。

## 后端

配套后端在 `../faust-back-end`（Spring Boot 3.5 + Spring AI Alibaba）。启动依赖 MySQL 8 与 Redis 7：

```bash
cd ../faust-back-end
docker compose up -d          # MySQL 3306 / Redis 6379，首次启动自动执行 db/schema.sql
mvn spring-boot:run           # http://localhost:8080
```

`ChatReq` 要求 `chatId`、`agentCode`、`version`、`userMsg` 均非空，其中 `chatId` 由前端生成后传入。


接口响应统一为：

```json
{ "code": 0, "message": "success", "data": {} }
```

`code === 0` 视为成功，`lib/http.ts` 直接返回 `data`；失败抛出 `ApiError`，消息取 `message`。

身份通过请求头 `user-id` 传递（后端 `LoginInterceptor` 读取），
本仓库默认从 `localStorage` 读取，可在左下角头像处切换。
头名统一由 `lib/session.ts` 导出 `USER_ID_HEADER`，SSE、上传等绕过 `http.ts` 的请求也复用它。

## 脚本

```bash
npm run dev        # 开发
npm run build      # 类型检查 + 构建
npm run preview    # 预览构建产物
npm run typecheck  # 仅类型检查
```
