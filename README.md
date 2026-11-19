# Faust · Agent Center

**配置驱动的多智能体平台**：把「大模型 + 工具 + 业务知识（Skill）+ 记忆」装配成一个个可被前端直接调用的 Agent。
业务方不需要写代码，只在控制台里配好 Agent、模型、工具和 Skill，平台就会生成一个会「思考 → 调工具 → 记住上下文」的助手。

内核基于 [Spring AI](https://spring.io/projects/spring-ai) 与 [Spring AI Alibaba Agent Framework](https://github.com/alibaba/spring-ai-alibaba)，
支持 ReAct 模式、SSE 流式输出、Redis 检查点记忆与代码执行沙箱。

| 模块 | 目录 | 技术栈 |
| --- | --- | --- |
| 后端 | [`faust-back-end/`](./faust-back-end) | Java 17 · Spring Boot 3.5.8 · Spring AI 1.1.2 · MyBatis-Plus · MySQL 8 · Redis |
| 前端 | [`faust-front-end/`](./faust-front-end) | React 18 · TypeScript · Vite 5 · Tailwind CSS 3 · Radix UI |

---

## 目录

- [核心概念](#核心概念)
- [功能特性](#功能特性)
- [系统架构](#系统架构)
- [技术栈](#技术栈)
- [仓库结构](#仓库结构)
- [快速开始](#快速开始)
- [配置说明](#配置说明)
- [数据库](#数据库)
- [接口概览](#接口概览)
- [流式对话协议](#流式对话协议)
- [生产部署](#生产部署)
- [常见问题](#常见问题)
- [安全提示](#安全提示)

---

## 核心概念

| 概念 | 说明 |
| --- | --- |
| **Agent** | 智能体。由系统提示词、模型配置、工具集、技能集组成；`agent_code` 标识一个 Agent，`version` 区分版本，同一 `agent_code` 的各版本共享编码 |
| **Model** | 每个 Agent 独立的模型配置（`base_url` / `api_key` / `model_name` / 温度 / 超时），走 OpenAI 兼容协议，因此可接 GPT、通义千问、DeepSeek、私有化部署模型等 |
| **Tool** | 工具。四种类型：`NATIVE`（内置原生工具）、`HTTP`（按 JSON Schema 配置 url / method / headers / parameters 调外部接口，支持 `${env.XXX}` 引用环境变量）、`DB`（对已配置数据源执行 SQL）、`SUB_AGENT`（把另一个 Agent 当作工具调用） |
| **Skill** | 业务知识包。由若干条目组成：`CONTENT`（正文，如 `SKILL.md`）、`REFERENCE`（参考资料）、`SCRIPT`（脚本）；注册进 `SkillRegistry` 后由 Agent 通过 `read_skill` 按需读取，避免一次性塞满上下文 |
| **DataSource** | 数据库数据源，密码以 AES 加密落库，供 `DB` 类型工具使用，并提供连接测试 |
| **Space** | 工作空间。`PERSONAL`（个人，系统默认、不可解散）与 `TEAM`（团队，可加人）；Agent / 工具 / 技能 / 数据源均按空间隔离 |
| **Chat** | 会话。短记忆存 Redis（检查点，TTL 15 天），长记忆落 `conversation_record` / `message_record_item` |
| **Global Env** | 用户级全局环境变量，按「用户 + Agent」维度保存，可在对话中注入 |

## 功能特性

**Agent 能力**

- **ReAct 循环**：`agent/executor/AgentExecutor` 驱动「推理 → 选工具 → 观察 → 再推理」，直到产出最终结论
- **SSE 流式输出**：`POST /api/agent/chat/start/sse` 返回 `text/event-stream`，前端边流边渲染
- **工具动态装配**：原生工具用 `@Tool` 注解注册；HTTP / DB / 子智能体工具由 `ToolCallbackFactory` 运行时组装
- **技能按需加载**：`DefaultSkillRegistry` 实现 MySQL 版技能注册表，支持 `skill://{skillName}/references/{file}`、`/scripts/{file}` 虚拟路径，Agent 可用 `read_skill` 逐步读取附属文件
- **双层记忆**：短期记忆走 Redis 检查点，长期记忆落库，可查询历史消息与记忆
- **代码沙箱**：Spring AI Alibaba Sandbox + GraalVM Polyglot，沙箱会话映射存 Redis
- **内置工具**：当前时间、Excel 解析（POI）、代码解释执行
- **多租户隔离**：所有业务接口按 `x-space-id` 头归属空间，`SpaceGuard` 统一校验；写路径带分布式锁

**控制台能力**

- 智能体工作台：列表 / 详情 / 发布 / 下线 / 删除 / 版本管理，与对话入口合并
- 技能管理：技能本体 + 条目编辑，支持拖拽导入目录（自动识别 frontmatter 与附属文件）
- 工具管理：四类工具的配置与连接参数、发布状态
- 数据源管理：连接配置、连接测试、密码加密存储
- 空间管理：个人 / 团队空间切换、成员管理、解散与退出
- 流式对话：打字机平滑输出、思考过程折叠、工具调用过程展示、Markdown 渲染（CJK 友好）、文件上传
- 视觉系统 Aurora Obsidian：深色优先、明暗可切，极光光斑 / 网格 / 聚光跟随等材质层均为 GPU 合成

## 系统架构

```
浏览器
└── React 18 控制台（dev :5173 / 构建产物 dist）
    ├── services/*.ts       只做请求 + 解包
    ├── lib/http.ts         注入 user-id / x-space-id，解包 ApiResponse
    ├── lib/sse.ts          SSE 帧解析 + 断连/超时兜底
    └── lib/streamReducer   单轮回复状态机（纯函数）
        │
        │  /api/**  /file/**      dev: Vite 代理    prod: Nginx 反向代理
        ▼
Spring Boot 3.5（faust-back-end :8080）
├── LoginInterceptor → UserContext（user-id / supplierCode / channelSource）
├── controller → service → repository → mapper → entity
└── agent/
    ├── AgentFactory          按 DB 配置装配 Agent
    ├── AgentExecutor         ReAct 执行 + SSE 帧输出
    ├── ToolCallbackFactory   NATIVE / HTTP / DB / SUB_AGENT
    ├── DefaultSkillRegistry  skill:// 虚拟路径解析
    ├── NewRedisSaver         短期记忆检查点
    └── NativeToolRegister    内置工具注册
        │
        ├──► MySQL 8        配置 / 会话 / 长记忆
        ├──► Redis 7        检查点 / 沙箱会话 / 分布式锁
        ├──► S3 兼容存储     MinIO / OSS / COS
        └──► 模型服务        OpenAI 兼容协议
```

**请求链路**：`controller → service → repository → mapper → entity`
**身份链路**：请求头 `user-id`（Long 型用户 ID）→ `LoginInterceptor` → `UserContext`；`supplierCode`、`channelSource` 为可选头。

## 技术栈

### 后端

| 维度 | 选型 |
| --- | --- |
| 语言 / 运行时 | Java 17 |
| 框架 | Spring Boot 3.5.8（web + webflux + validation + actuator） |
| Agent 内核 | Spring AI 1.1.2 + Spring AI Alibaba Agent Framework 1.1.2.2 |
| 持久层 | MyBatis-Plus 3.5.9 + Druid 1.2.24 + MySQL 8 |
| 会话记忆 | Redis 7（Redisson 4.7.0） |
| 对象存储 | AWS SDK v2 for S3（兼容 MinIO / 阿里云 OSS / 腾讯云 COS） |
| 代码沙箱 | Spring AI Alibaba Sandbox + GraalVM Polyglot / GraalPy |
| 接口文档 | springdoc-openapi 2.8.6 |
| 辅助库 | Lombok、Hutool、Fastjson、Guava、POI |

### 前端

| 维度 | 选型 |
| --- | --- |
| 构建 | Vite 5（Node 18+） |
| 框架 | React 18 + TypeScript 5.7 |
| 路由 | React Router 6（页面级 `React.lazy` 懒加载） |
| 样式 | Tailwind CSS 3 + CSS Variables |
| 组件 | shadcn/ui 风格自建组件（Radix UI 无样式底座） |
| 图标 / 提示 | lucide-react / sonner |
| Markdown | markdown-it + markdown-it-cjk-friendly + remend |

> 不依赖任何内部私有包：组件、请求层、流式解析都在仓库内实现。

## 仓库结构

```
faust/
├── faust-back-end/                  # Spring Boot 服务
│   ├── src/main/java/com/faust/
│   │   ├── FaustApplication.java     # 启动类
│   │   ├── controller/               # REST 接口层（10 个 Controller）
│   │   ├── service/                  # 业务编排
│   │   ├── repository/               # 数据访问聚合（IService + impl）
│   │   ├── mapper/ entity/ dto/      # MyBatis-Plus Mapper / 实体 / 出入参
│   │   ├── agent/                    # Agent 内核
│   │   │   ├── executor/             #   ReAct 执行器、错误消息构建
│   │   │   ├── factory/              #   Agent / Tool / DataSource 装配工厂
│   │   │   ├── tools/                #   native / http / db / subagent
│   │   │   ├── skills/registry/      #   MySQL 版 SkillRegistry
│   │   │   ├── checkpoint/saver/     #   Redis 检查点
│   │   │   └── sandbox/ cache/ hooks/
│   │   ├── common/ config/ enums/    # 响应包装、用户上下文、加解密、配置
│   │   ├── interceptor/ support/     # 身份拦截器、空间校验、S3 客户端、锁
│   │   └── exception/                # 业务异常 + 全局异常处理
│   ├── src/main/resources/
│   │   ├── application.yml           # 全部敏感项走环境变量
│   │   ├── logback-spring.xml
│   │   └── db/schema.sql             # 建库建表脚本（MySQL 8.0+）
│   ├── Dockerfile                    # 多阶段构建 → JRE 17 运行镜像
│   └── docker-compose.yml            # MySQL 8 + Redis 7（+ 可选 app / minio）
│
└── faust-front-end/                 # React 控制台
    ├── src/
    │   ├── components/ui/            # 基础组件（Button / Dialog / Select / DataTable …）
    │   ├── components/layout/        # 应用框架与页面头
    │   ├── lib/                      # http / sse / streamReducer / markdown / session / space
    │   ├── pages/                    # agents / skill-admin / tool-admin / datasource-admin / spaces / chat
    │   ├── services/                 # 接口层（只做请求 + 解包）
    │   └── index.css                 # 材质层与主题变量
    ├── vite.config.ts                # 端口 5173，代理 /api 与 /file
    └── tailwind.config.js
```

## 快速开始

### 0. 环境要求

| 依赖 | 版本 |
| --- | --- |
| JDK | 17+ |
| Maven | 3.9+ |
| Node.js | 18+（建议 20/22） |
| Docker | 20+，含 Docker Compose v2 |
| MySQL / Redis | 可用容器代替，MySQL 需 8.0+ |

### 1. 启动基础设施（MySQL + Redis）

```bash
cd faust-back-end
cp .env.example .env          # 按需修改数据库密码等
docker compose up -d          # MySQL 3306 / Redis 6379
```

`docker-compose.yml` 把 `src/main/resources/db/schema.sql` 挂载为 MySQL 初始化脚本，
**首次启动**会自动建库建表。数据卷已存在时不会重复执行；字典表 `agent_dict` 需按需自行初始化。

> 已有自建 MySQL 时可手动执行：`mysql -u root -p < src/main/resources/db/schema.sql`

### 2. 启动后端

Spring Boot **不会自动读取** `.env`，需要先把变量导入当前 shell：

```bash
cd faust-back-end
cp .env.example .env
set -a && source .env && set +a
mvn spring-boot:run
```

打包运行：

```bash
mvn clean package -DskipTests
set -a && source .env && set +a
java -jar target/faust-back-end.jar
```

用 IDEA 调试时，在 `Run → Edit Configurations → Environment variables` 里粘贴 `.env` 内容，
或安装 [EnvFile](https://plugins.jetbrains.com/plugin/7861-envfile) 插件指向 `.env`。

启动后：

| 入口 | 地址 |
| --- | --- |
| 健康检查 | <http://localhost:8080/actuator/health> |
| 接口文档 | <http://localhost:8080/swagger-ui/index.html> |
| Druid 监控 | <http://localhost:8080/druid> |

### 3. 启动前端

```bash
cd faust-front-end
npm install
cp .env.example .env          # VITE_API_TARGET 默认 http://localhost:8080
npm run dev                   # http://localhost:5173
```

Vite 会把 `/api`、`/file` 代理到 `VITE_API_TARGET`，因此本地不需要配置 CORS。

### 4. 配一个能跑的 Agent

平台**不内置任何模型密钥**，模型配置存在数据库里。先给某个 Agent 配上模型：

```sql
UPDATE agent_model_config
SET base_url   = 'https://api.openai.com',
    api_key    = 'sk-xxx',
    model_name = 'gpt-4o-mini'
WHERE agent_id = 1;
```

然后在控制台依次完成：创建 Agent（填系统提示词）→ 选工具 → 选技能 → 发布 → 进入对话。
文件上传依赖对象存储，未配置 S3 时上传会失败，其余功能不受影响。

## 配置说明

所有敏感配置都通过环境变量注入，完整清单见 [`faust-back-end/.env.example`](./faust-back-end/.env.example)。

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `SERVER_PORT` | `8080` | 服务端口 |
| `SPRING_PROFILES_ACTIVE` | `local` | 激活的 profile（Docker 镜像内置 `prod`） |
| `MYSQL_URL` | `jdbc:mysql://localhost:3306/faust?...` | JDBC 连接串，**含 `&` 必须用单引号包裹** |
| `MYSQL_USERNAME` / `MYSQL_PASSWORD` | `root` / 见 `.env.example` | 数据库账号，**必须与 compose 中 MySQL 的 root 密码一致** |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` / `REDIS_DATABASE` | `localhost` / `6379` / 空 / `0` | Redis 连接 |
| `S3_BUCKET` / `S3_ACCESS_KEY` / `S3_SECRET_KEY` | 无 | 对象存储，**必须配置才能上传文件** |
| `S3_ENDPOINT` / `S3_PUBLIC_ENDPOINT` | 无 | 内网 / 公网访问地址 |
| `S3_SIGNING_REGION` / `S3_PRESIGN_EXPIRE_MINUTES` | `cn-north-1` / `1440` | 签名区域与预签名有效期 |
| `AGENT_PASSWORD_SECRET` | `please-change-me-in-production` | 数据源密码加解密密钥，**生产必须替换** |
| `AGENT_LOCK_ENABLED` | `true` | 写路径分布式锁，生产必须为 `true` |
| `DRUID_USERNAME` / `DRUID_PASSWORD` | `druid` / `druid` | Druid 监控台账号，公网部署前请修改或关闭 |

前端只有一个：

| 变量 | 说明 |
| --- | --- |
| `VITE_API_TARGET` | 仅 dev server 代理使用，默认 `http://localhost:8080`；构建产物走相对路径，不需要配置 |

## 数据库

默认库名 **`faust`**（见 `schema.sql` 与 `application.yml` 的默认连接串），字符集 `utf8mb4`。

| 表 | 用途 |
| --- | --- |
| `agent_info` | Agent 主表（按 `agent_code` + `version` 唯一，含发布状态、是否可作子智能体） |
| `agent_model_config` | Agent 模型配置（base_url / api_key / model_name / 温度 / 超时） |
| `agent_system_prompt` | 系统提示词 |
| `agent_tool` / `agent_tool_rel` | 工具定义与 Agent-工具关联 |
| `agent_skill` / `agent_skill_item` / `agent_skill_rel` | 技能本体、技能条目、Agent-技能关联 |
| `data_source_config` | 数据源（密码 AES 加密） |
| `agent_global_env_config` | 用户维度的全局环境变量（JSON） |
| `agent_dict` | 数据字典（状态、工具类型、技能条目类型等，需按业务自行初始化） |
| `conversation_record` / `message_record_item` | 会话与消息长记忆 |
| `work_space` / `work_space_user` | 工作空间与成员（`PERSONAL` / `TEAM`，角色 `OWNER` / `MEMBER`） |

业务表统一带 `create_time` / `update_time` / `create_by` / `update_by`；删除接口为物理删除（脚本中无 `is_deleted` 软删字段），删除前请确认无引用。

## 接口概览

统一响应：

```json
{ "code": 0, "message": "success", "data": {} }
```

`code === 0` 为成功，前端 `lib/http.ts` 直接返回 `data`；失败抛 `ApiError`，消息取 `message`。

| 模块 | 前缀 | 主要接口 |
| --- | --- | --- |
| Agent 管理 | `/api/agent` | `create` `update` `publish` `takeOff` `delete` `list` `card/list` `sub/agent/list` `detail/{agentCode}` `base/{agentCode}` `message/memory` |
| 会话 | `/api/agent/chat` | `start` `start/sse` `list` `history` `env/var` |
| 全局环境变量 | `/api/agent/global/env` | `save` |
| 工具管理 | `/api/tool` | `create` `update` `publish` `takeOff` `delete` `list` `published-list` `detail/{toolId}` |
| 技能管理 | `/api/skill` | `create` `update` `publish` `takeOff` `delete` `list` `published-list` `detail/{skillId}` |
| 数据源 | `/api/datasource` | `create` `update` `delete` `list` `detail/{id}` `connection/test` |
| 空间 | `/api/space` | `list` `detail` `create` `rename` `dissolve` `member/list` `member/add` `member/remove` `member/leave` |
| 字典 | `/api/dict` | `GET /{dictType}` |
| 文件 | `/file` | `upload` |

约定：

- 分页参数为 `current` / `size`，返回 MyBatis-Plus `Page`（`{ records, total, current, size }`）；对话列表用 `current` / `pageSize`
- 业务接口需带 `x-space-id` 头（当前空间），缺省回落到个人空间；`/api/space/**` 不参与该头部校验
- 身份通过 `user-id` 头传递（`LoginInterceptor` 读取为 Long）

## 流式对话协议

`POST /api/agent/chat/start/sse`，`Content-Type: text/event-stream`。

请求体 `ChatReq` 要求 `chatId`、`agentCode`、`version`、`userMsg` 均非空，其中 `chatId` 由前端生成后传入。

| 帧 | 含义 |
| --- | --- |
| `{"chatId":"..."}` | 首帧回传会话 id |
| `{"message":{"messageType":"assistant"},"chunk":"..."}` | 正文增量，进「思考块」 |
| `{"message":{"messageType":"tool-request"}}` | 工具调用请求 |
| `{"message":{"messageType":"tool-response"}}` | 工具返回 |
| `{"message":{"messageType":"final-assistant"}}` | 本轮最终结论，流关闭后合并铺出 |
| `event: error` | 错误帧 `{"error":true,"errorMessage":"..."}` |

前端对正文做了平滑打字机处理（`lib/hooks/use-typewriter.ts`），同时兼容 `chunk` 增量与
`message.content` 累积快照两种下发语义；断连与超时有兜底（`lib/sse.ts`）。

## 生产部署

### 方案一：后端走 Docker，前端走 Nginx（推荐）

**1）准备环境变量**

```bash
cd faust-back-end
cp .env.example .env
# 必改：MYSQL_URL / MYSQL_PASSWORD / S3_* / AGENT_PASSWORD_SECRET / DRUID_PASSWORD
```

**2）构建并启动后端（含 MySQL、Redis）**

```bash
docker compose --profile app up -d --build
```

`app` 服务在 compose 中已把 `MYSQL_URL` 覆盖为容器内地址、`REDIS_HOST` 覆盖为 `redis`，
并将 `SPRING_PROFILES_ACTIVE` 置为 `prod`；镜像 `Dockerfile` 为多阶段构建，运行时是 JRE 17。

> **注意**：`schema.sql` 建的库是 `faust`，而 `docker-compose.yml` 的 `app` 服务里
> `MYSQL_URL` 仍指向 `faust_agent_center`。使用 `app` profile 时请把该值改成 `.../faust?...`，
> 或把 `schema.sql` 里的库名一并改掉，否则服务连的是一个空库。

**3）构建前端**

```bash
cd faust-front-end
npm ci
npm run build          # 产物在 dist/
```

**4）Nginx 托管 + 反向代理**

把 `dist/` 拷到服务器（如 `/usr/share/nginx/html`），使用如下配置。
关键是 **关闭 SSE 的响应缓冲**，否则流式输出会卡住直到结束才一次性吐出：

```nginx
server {
    listen       80;
    server_name  agent.example.com;

    root  /usr/share/nginx/html;
    index index.html;

    # 与后端 multipart 上限对齐（业务上限 200MB）
    client_max_body_size 210m;

    # 静态资源长缓存
    location /assets/ {
        expires 1y;
        add_header Cache-Control "public, immutable";
    }

    # 业务接口（含 SSE）
    location /api/ {
        proxy_pass         http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header   Host              $host;
        proxy_set_header   X-Real-IP         $remote_addr;
        proxy_set_header   X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header   X-Forwarded-Proto $scheme;

        # SSE 必须项
        proxy_set_header   Connection        "";
        proxy_buffering    off;
        proxy_cache        off;
        chunked_transfer_encoding on;
        proxy_read_timeout 3600s;
        proxy_send_timeout 3600s;
    }

    # 文件上传与下载
    location /file/ {
        proxy_pass       http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }

    # SPA 路由回退：/skills、/chat/xxx 刷新不能 404
    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

```bash
nginx -t && nginx -s reload
```

前后端同源后，浏览器不再有跨域问题，`VITE_API_TARGET` 在生产构建中不生效，无需改动。

### 方案二：完全不用 Docker

```bash
# 1) 数据库
mysql -u root -p < faust-back-end/src/main/resources/db/schema.sql

# 2) 后端
cd faust-back-end
cp .env.example .env                 # 首次部署时准备，按需修改
mvn clean package -DskipTests
set -a && source .env && set +a
nohup java -Xms512m -Xmx1024m -jar target/faust-back-end.jar > app.log 2>&1 &

# 3) 前端
cd ../faust-front-end
npm ci && npm run build
# 再把 dist/ 交给 Nginx / 任意静态服务器，按方案一的 server 块配置
```

系统服务方式（systemd）示例：

```ini
[Unit]
Description=Faust Agent Center
After=network.target mysql.service redis.service

[Service]
User=faust
WorkingDirectory=/opt/faust
EnvironmentFile=/opt/faust/.env
ExecStart=/usr/bin/java -Xms512m -Xmx1024m -jar /opt/faust/faust-back-end.jar
SuccessExitStatus=143
Restart=on-failure

[Install]
WantedBy=multi-user.target
```

### 部署检查清单

- [ ] `AGENT_PASSWORD_SECRET` 已替换为随机值（`openssl rand -base64 32`），且**之后不要再换**，否则历史数据源密码无法解密
- [ ] `AGENT_LOCK_ENABLED=true`
- [ ] Druid 监控台已改密码，或直接关闭 `stat-view-servlet`
- [ ] MySQL / Redis 不对公网暴露，账号按最小权限分配
- [ ] 对象存储的 AK/SK 使用专用子账号，仅授权目标 bucket
- [ ] Nginx 已配 `proxy_buffering off`，SSE 实测能逐字输出
- [ ] 网关层补齐鉴权：当前 `user-id` 由请求头透传，**后端不做登录校验**，直接暴露公网等于允许任何人冒充任意用户
- [ ] 已配置 HTTPS，并确认 `/api` 长连接不被中间层（CDN / SLB）60s 超时切断

## 常见问题

**Q：`docker compose up -d` 之后后端连不上库？**
compose 默认只起 MySQL + Redis，后端需本地 `mvn spring-boot:run`。且需确认 `.env` 里的库名与 `schema.sql` 建的一致（默认 `faust`）。

**Q：启动报 `Access denied` 或表不存在？**
`MYSQL_PASSWORD` 必须与 `docker-compose.yml` 中 MySQL 的 root 密码一致；表由初始化脚本只在**首次**启动时创建，改过脚本需 `docker compose down -v` 重置数据卷。

**Q：`source .env` 报错？**
`MYSQL_URL` 含 `&`，必须写成 `MYSQL_URL='jdbc:mysql://...&...'` 单引号包裹。

**Q：前端页面刷新 404？**
SPA 需要 Nginx `try_files $uri $uri/ /index.html` 回退。

**Q：对话一直转圈、最后一次性出全文？**
中间层缓冲了 SSE。检查 Nginx 的 `proxy_buffering off`、`chunked_transfer_encoding on`，以及 CDN/SLB 的响应缓冲与超时设置。

**Q：改了 `tailwind.config.js` 不生效？**
Vite 不会热更新 Tailwind 配置，需要重启 dev server。

**Q：上传文件失败？**
检查 `S3_BUCKET` / `S3_ACCESS_KEY` / `S3_SECRET_KEY` / `S3_ENDPOINT` 是否配置、bucket 是否存在、`S3_PUBLIC_ENDPOINT` 是否可被浏览器访问。

**Q：Agent 报模型错误？**
模型配置在 `agent_model_config`，`base_url` 需为 OpenAI 兼容地址（一般以 `/v1` 结尾），`api_key` 与 `model_name` 要对得上。

**Q：想到换 `AGENT_PASSWORD_SECRET`？**
不要直接换。旧密文用旧密钥加密，换密钥后需要重新录入所有数据源密码。

## 安全提示

- 所有密钥都通过环境变量注入，`.env` 已在 `.gitignore` 中；`.env.example` 与 `application.yml`
  里的默认值只能作为本地占位，**部署前必须全部替换**
- 如果曾经把真实的对象存储 AK/SK、数据库密码写进过 `.env.example` 或 `application.yml` 并提交，
  请立即在对应平台轮换，并改用环境变量或密钥管理服务注入
- 数据源密码用 AES-256 派生密钥加密存储，依赖 `AGENT_PASSWORD_SECRET`
- 工具支持 `DB`（执行 SQL）、`HTTP`（外发请求）、`NATIVE`（代码沙箱）等高权限能力，
  上线前应确认 Agent 的工具授权范围，并对数据源账号做最小权限控制
- 当前身份仅靠 `user-id` 请求头传递，适合内网或已有网关鉴权的场景；对公网开放前务必接入统一登录态校验

## License

Apache License 2.0
