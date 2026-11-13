# Faust Agent Center — 后端

一个**配置驱动**的多智能体平台后端：把「大模型 + 工具 + 业务知识（Skill）+ 记忆」装配成一个个可被前端调用的 Agent。
业务方无需写代码，只需在数据库里配置 Agent、Skill、Tool、Model，平台即可生成一个会「思考 → 调用工具 → 记住上下文」的助手。

内核基于 [Spring AI](https://spring.io/projects/spring-ai) 与 [Spring AI Alibaba Agent Framework](https://github.com/alibaba/spring-ai-alibaba)，支持 ReAct 模式、SSE 流式输出、Redis 检查点记忆。

## 技术栈

| 层次 | 选型 |
|---|---|
| 语言 / 运行时 | Java 17 |
| 框架 | Spring Boot 3.5.8 |
| Agent 内核 | Spring AI 1.1.2 + Spring AI Alibaba Agent Framework 1.1.2.2 |
| 持久层 | MyBatis-Plus 3.5.9 + Druid + MySQL 8 |
| 会话记忆 | Redis（Redisson） |
| 对象存储 | AWS SDK v2 for S3（兼容 MinIO / 阿里云 OSS / 腾讯云 COS） |
| 代码执行沙箱 | GraalVM Polyglot + GraalPy |
| 接口文档 | springdoc-openapi |

## 快速开始

### 1. 准备依赖

需要 JDK 17、Maven 3.9+、Docker（用于起 MySQL 与 Redis）。

```bash
cp .env.example .env      # 按需修改其中的账号密码
docker compose up -d      # 启动 MySQL 8 + Redis 7，并自动执行建表脚本
```

`docker-compose.yml` 会把 `src/main/resources/db/schema.sql` 挂载为 MySQL 的初始化脚本，首次启动自动建库建表。

> 已有自建 MySQL 时，也可以手动执行：
> `mysql -u root -p < src/main/resources/db/schema.sql`

### 2. 启动服务

Spring Boot 不会自动读取 `.env`，需要先把变量导入当前 shell（或用下文的 IDE / Docker 方式）：

```bash
# macOS / Linux：把 .env 里的变量导出到当前终端
set -a && source .env && set +a
mvn spring-boot:run
```

或者打包后运行（变量同样需要先导入）：

```bash
mvn clean package -DskipTests
set -a && source .env && set +a
java -jar target/faust-back-end.jar
```

也可以用 Docker 直接跑（compose 会自动读 `.env`）：

```bash
docker compose --profile app up -d
```

用 IDEA 调试时，在 `Run → Edit Configurations → Environment variables` 里展开粘贴 `.env` 内容（每行一个 `KEY=VALUE`），
或安装 [EnvFile](https://plugins.jetbrains.com/plugin/7861-envfile) 插件直接指向 `.env` 文件。

> `docker compose up -d`（不带 `--profile app`）只启动 MySQL 与 Redis，后端仍在本地跑。

默认端口 `8080`，启动后可访问：

- 健康检查：<http://localhost:8080/actuator/health>
- 接口文档：<http://localhost:8080/swagger-ui/index.html>
- Druid 监控：<http://localhost:8080/druid>

### 3. 配置一个可用的 Agent

平台不自带任何模型密钥，需要在数据库中为每个 Agent 配置模型：

```sql
UPDATE agent_model_config
SET base_url  = 'https://api.openai.com',
    api_key   = 'sk-xxx',
    model_name = 'gpt-4o-mini'
WHERE agent_id = 1;
```

然后按前端调用约定依次创建 Agent、Skill、Tool 即可。

## 环境变量

所有敏感配置都通过环境变量注入，完整清单见 `.env.example`。

| 变量 | 默认值 | 说明 |
|---|---|---|
| `SERVER_PORT` | `8080` | 服务端口 |
| `SPRING_PROFILES_ACTIVE` | `local` | 激活的 profile |
| `MYSQL_URL` | `jdbc:mysql://localhost:3306/faust_agent_center...` | JDBC 连接串 |
| `MYSQL_USERNAME` / `MYSQL_PASSWORD` | `root` / `root` | 数据库账号 |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | `localhost` / `6379` / 空 | Redis 连接 |
| `S3_BUCKET` / `S3_ACCESS_KEY` / `S3_SECRET_KEY` | `faust` / 空 / 空 | 对象存储 |
| `S3_ENDPOINT` / `S3_PUBLIC_ENDPOINT` | `http://localhost:9000` | 对象存储地址 |
| `AGENT_PASSWORD_SECRET` | `please-change-me-in-production` | 数据源密码加解密密钥，**生产必须替换** |
| `DRUID_USERNAME` / `DRUID_PASSWORD` | `druid` / `druid` | Druid 监控台账号 |

## 目录结构

经典 MVC 分层，单模块工程：

```
src/main/java/com/faust/
├── FaustApplication.java          启动类
├── controller/                    REST 接口层（8 个 Controller）
├── service/                       业务编排层
├── repository/                    数据访问聚合层（MyBatis-Plus IService）
│   └── impl/
├── mapper/                        MyBatis-Plus Mapper
├── entity/                        数据库实体
├── dto/                           出入参对象，按 agent / chat / tool / skill / datasource / file / whitelist 分组
├── enums/                         枚举
├── exception/                     业务异常 + 全局异常处理
├── agent/                         Agent 内核
│   ├── executor/                  ReAct 执行器
│   ├── factory/                   Agent / Tool / DataSource 装配工厂
│   ├── hooks/                     消息落库与处理钩子
│   ├── tools/                     工具实现（native / http / db / subagent）
│   ├── skills/                    Skill 注册表
│   └── checkpoint/                Redis 检查点存储（会话记忆）
├── common/                        通用能力（响应包装、用户上下文、加解密、类型处理器）
├── config/                        Spring 配置
└── interceptor/                   身份拦截器
```

调用链路：`controller → service → repository → mapper → entity`。

## 接口一览

所有接口统一返回 `{ "code": 0, "message": "success", "data": ... }`，`code != 0` 表示业务失败。

| 模块 | 前缀 | 主要接口 |
|---|---|---|
| Agent 管理 | `/api/agent` | `create` `update` `publish` `takeOff` `delete` `list` `detail/{agentCode}` `base/{agentCode}` `card/list` `sub/agent/list` `message/memory` |
| 会话 | `/api/agent/chat` | `start` `start/sse` `list` `history` `env/var` |
| 白名单 | `/api/agent/whitelist` | `create` `delete` `list` |
| 工具管理 | `/api/tool` | `create` `update` `publish` `takeOff` `delete` `list` `published-list` `detail/{toolId}` |
| 技能管理 | `/api/skill` | `create` `update` `publish` `takeOff` `delete` `list` `published-list` `detail/{skillId}` |
| 数据源 | `/api/datasource` | `create` `update` `delete` `list` `detail/{id}` `connection/test` |
| 字典 | `/api/dict` | `{dictType}` |
| 文件 | `/file` | `upload` |

流式对话走 `POST /api/agent/chat/start/sse`，返回 `text/event-stream`，帧类型分四类：
`assistant-stream`、`final-assistant`、`tool-request`、`tool-response`。

## Agent 内核要点

- **ReAct 循环**：`agent/executor/AgentExecutor` 驱动「推理 → 选工具 → 观察 → 再推理」。
- **工具注册**：原生工具用 `@Tool` 注解（`agent/tools/local`），HTTP / DB / 子智能体工具由 `ToolCallbackFactory` 动态装配。
- **Skill 加载**：`agent/skills` 按关键词命中后，把知识片段按需注入系统提示词，避免一次性塞满上下文。
- **记忆机制**：短期记忆存活在 Redis（`NewRedisSaver`，TTL 15 天），长期记忆落 `conversation_record` / `message_record_item`。
- **身份透传**：`LoginInterceptor` 从请求头 `user-id` 解析当前用户（`Long` 型用户 ID）写入 `UserContext`；`supplierCode` / `channelSource` 为可选头。

## 安全提示

- 仓库内**不含任何真实凭据**，所有密钥通过环境变量注入。
- `AGENT_PASSWORD_SECRET` 用于加密 `data_source_config.pwd`，更换密钥会导致历史密文无法解密。
- `application.yml` 中的 Druid 监控台默认口令仅供本地调试，部署到公网前请务必修改或关闭。
- 生产环境建议叠加网关鉴权，并对 `data_source_config` 中配置的数据库做最小权限控制。

## License

Apache License 2.0
