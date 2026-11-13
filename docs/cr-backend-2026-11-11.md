# 后端代码审查报告（CR）

| 项 | 内容 |
| --- | --- |
| 审查对象 | `faust-back-end`（Spring Boot 3.5 + MyBatis-Plus + Spring AI Alibaba + Redisson） |
| 审查日期 | 2026-11-11 |
| 审查范围 | `src/main/java` 全部 220 个 Java 文件 + `resources` 配置与 DDL |
| 审查维度 | 正确性与缺陷、安全与权限、可维护性与规范 |
| 审查方式 | 分层分模块静态审查，逐条代码复核；**仅读，未修改任何文件** |
| 结论 | **P0 6 项、P1 15 项、P2 18 项** |

---

## 1. 结论摘要

| 级别 | 数量 | 说明 |
| --- | --- | --- |
| P0 | 6 | 存在越权、可用性阻断、凭据泄露、以及"安全承诺未实现"四类问题，建议上线前处理 |
| P1 | 15 | 集中在并发/事务边界、接口契约与后端约束不一致、第三方调用缺少兜底 |
| P2 | 18 | 可维护性、注释与实现不符、索引缺失、参数边界 |

**上线前必须处理（Top 6）**

1. 会话记忆接口可越权读取任意 `chatId` 的完整对话上下文（§2.1）
2. `getAgentByCode` 在同编码多版本时抛异常，导致详情与整个对话链路 500，并且第二个版本永远建不成（§2.2）
3. 构造函数里可用的 S3 AK/SK、加密密钥、DB 口令、Druid 默认口令（§2.4）
4. DB 工具的"只读"承诺仅靠关键字黑名单，可被绕过并读写服务器文件（§2.5）
5. 数据源连接测试接受任意 JDBC URL，可作内网探测与 JDBC 攻击面（§2.6）
6. 身份完全取自可伪造的 `user-id` 头（§2.3，取决于部署形态）

---

## 2. P0 阻断项

### 2.1 会话记忆接口无归属校验，任意 chatId 可读他人会话

- 位置：`src/main/java/com/faust/controller/AgentController.java:96`、`src/main/java/com/faust/service/AgentService.java:447`
- 代码：

```java
public ApiResponse<Object> getChatHistory(@RequestParam("chatId") @NotBlank(message = "会话记忆不能为空") String chatId) {
    return ApiResponse.success(agentService.getMemory(chatId));
```

```java
public Object getMemory(@Valid String chatId) {
    RunnableConfig config = RunnableConfig.builder()
        .threadId(chatId).build();
    Optional<Checkpoint> checkpointOpt = redisSaver.get(config);
```

- 问题：`getMemory` 直接用调用方传入的 `chatId` 构造 `threadId` 读 Redis checkpoint，**全程未查 `conversation_record`、未校验 `ucId`、未过 `spaceGuard`**。而同类的 `ChatService.getChatHistory` 是有归属校验的，此处是遗漏。
- 触发：`GET /api/agent/message/memory?chatId=<他人 chatId>`。`chatId` 会出现在前端 URL、日志、分享截图里，不是不可猜测的秘密。
- 影响：返回该会话的 `messages`，包含完整对话、工具调用入参与返回（可能是业务库查询结果、上传文件地址），属跨用户数据泄露。
- 建议：先按 `chatId + UserContext.getUcId()` 反查会话记录，查不到直接抛业务码；`threadId` 也建议改为 `ucId + ":" + chatId`，避免 checkpoint 命名空间跨用户共享。

```java
AgentConversationRecord record = conversationRecordService.getByChatId(chatId, UserContext.getUcId());
ThrowExceptionFunction.isNull(record).throwMessage(ErrorMessageEnum.CHAT_NOT_FOUND);
```

- 复核：已核对源码，确认无校验。

### 2.2 `getAgentByCode` 使用 `getOne`，同编码多版本时抛异常并阻断创建

- 位置：`src/main/java/com/faust/repository/impl/AgentInfoRepositoryImpl.java:175`
- 代码：

```java
wrapper.eq(AgentInfo::getAgentCode, agentCode);
return getOne(wrapper);
```

- 问题：建表脚本 `schema.sql:35` 的唯一键是 `(agent_code, version)`，**允许同编码多版本共存**；而这里按 code 单值查询且无 `LIMIT`，MyBatis-Plus `getOne` 默认 `throwEx=true`，命中多行即抛 `TooManyResultsException`。
- 触发：`foo/1.0.0` 已存在，再创建 `foo/1.1.0` —— `createAgent` 事务末尾回查即抛异常、整个创建回滚，**第二个版本永远建不成**；若历史数据已有多版本，`detail`、`base`、`chat/start`、`chat/start/sse` 全部 500。
- 影响：核心链路不可用，多版本能力形同虚设，且报错是无业务码的"系统内部异常"。
- 建议：按 `(agentCode, version)` 精确查；若确需"取最新版本"语义，显式 `orderByDesc(...).last("LIMIT 1")` 并在方法注释写明。对话鉴权应复用 `getPublishedAgentByCodeAndVersion`。
- 复核：已核对源码，确认无 `LIMIT`。

### 2.3 身份完全来自可伪造的 `user-id` 请求头

- 位置：`src/main/java/com/faust/interceptor/LoginInterceptor.java:30`、`src/main/java/com/faust/config/WebConfig.java:21`
- 代码：

```java
Optional.ofNullable(ucid).map(Long::valueOf).ifPresent(user::setUcId);
```

- 问题：`preHandle` 无条件 `return true`，用户身份等于客户端自报的 HTTP 头，无 token/session/签名校验；`ucId` 为空也不拒绝，直接放行到下游。
- 触发：`curl -H 'user-id: 12345' -H 'x-space-id: 9' ...`。
- 影响：所有 `SpaceGuard.assertMember` 校验都建立在伪造身份之上；若服务可直接访问，等同于无认证开放（加成员、删 Agent、读数据源连接串）。另外 `Long::valueOf` 未捕获 `NumberFormatException`，`user-id: abc` 会抛未处理异常，且该拦截器不在 `GlobalExceptionHandler` 范围内，直接 500。
- 建议：
  1. 确认部署形态——若在强制覆盖 `user-id` 的可信网关之后，请在 `LoginInterceptor` 注释中写明该前提，并禁止服务端口对外暴露；
  2. 否则引入真实凭证（JWT 或网关签名头），校验通过才写入 `UserContext`；
  3. `ucId` 为空时直接拒绝，`Long::valueOf` 包 `try/catch` 转 401。
- 复核：已核对源码，确认无鉴权逻辑。**该条是否构成 P0 取决于部署形态，请与运维确认。**

### 2.4 配置文件中内置可用的对象存储 AK/SK、加密密钥与默认口令

- 位置：`src/main/resources/application.yml:28`、`:46`、`:76`、`:77`、`:84`
- 代码：

```yaml
password: ${MYSQL_PASSWORD:123456}
...
login-username: ${DRUID_USERNAME:druid}
login-password: ${DRUID_PASSWORD:druid}
...
ak: ${S3_ACCESS_KEY:EC1D9ABSPKC39QOWFZHS}
sk: ${S3_SECRET_KEY:4sb1710eic36KfHCAjSi8Loo7vatjIzuMuwM8ofI}
...
secret: ${AGENT_PASSWORD_SECRET:please-change-me-in-production}
```

- 问题：真实形态的 S3 密钥、可用的数据源密码加密密钥、DB 与 Druid 控制台口令，全部以 `${VAR:默认值}` 形式提交进仓库。文件头注释声称"敏感信息均通过环境变量注入"，与实现自相矛盾。
- 触发：拿到仓库读权限的人；或部署环境未注入环境变量时直接生效。
- 影响：对象存储桶可被读写删除；加密密钥可知 → `data_source_config.pwd` 密文可离线解密，全部业务库凭据泄露；Druid 控制台暴露 SQL 与连接串。
- 建议：
  1. 立即轮换上述 AK/SK 与加密密钥（已进入提交历史即视为泄露）；
  2. 删除所有默认值（`${S3_ACCESS_KEY}` 不加冒号），启动时对空值 fail-fast；
  3. 非本地 profile 关闭 `stat-view-servlet`，或强口令 + IP 白名单 + 独立端口。
- 复核：已核对 `application.yml` 原文。

### 2.5 DB 工具的"只读"承诺仅靠关键字黑名单，可被绕过

- 位置：`src/main/java/com/faust/agent/tools/db/DbToolExecutor.java:91`
- 代码：

```java
// 1. 仅允许只读查询（SELECT 或 WITH 开头的 CTE 查询）
String trimmedSql = sql.trim();
// 2. 拦截写操作 / DDL 关键字（DELETE、UPDATE、ALTER 等）
if (FORBIDDEN_SQL_PATTERN.matcher(trimmedSql).find()) {
```

```java
"\\b(DELETE|UPDATE|ALTER|INSERT|DROP|TRUNCATE|CREATE|REPLACE|MERGE|GRANT|REVOKE|RENAME|COMMENT|EXEC|EXECUTE|SET)\\b"
```

- 问题：注释第 1 步声称"仅允许 SELECT/WITH"，实际 `trimmedSql` 算完只用于第 2 步黑名单匹配，**没有前缀白名单校验**；黑名单是词边界正则，下列写法均不命中：

  | 绕过手法 | 说明 |
  | --- | --- |
  | `SELECT ... INTO OUTFILE '/tmp/x'` | `INTO`/`OUTFILE` 不在黑名单 |
  | `SELECT LOAD_FILE('/etc/passwd')` | `LOAD_FILE` 不在黑名单 |
  | `SELECT SLEEP(3600)` / `BENCHMARK(...)` | 无关键字命中，可长期占用连接 |
  | `/*!50000UPDATE*/` | `\b` 前是数字、后接 `U` 均为词字符，不构成边界 |

- 触发：提示注入让模型生成上述语句，或模型幻觉产出。
- 影响：以数据源账号权限执行任意单语句——读服务器本地文件、写文件（有 FILE 权限时）、长时间占连接；"只读"承诺被实质绕过。另外注释说"使用 PreparedStatement 绑定参数"，实现是 `conn.createStatement()`，无参数绑定。
- 建议：
  1. 用只读账号 + `Connection.setReadOnly(true)`，权限在数据库侧兜底；
  2. SQL 前缀白名单：`^\s*(select|with)\b`；
  3. 显式拒绝 `into\s+(outfile|dumpfile)`、`load_file\s*\(`、`sleep\s*\(`、`benchmark\s*\(`；
  4. 有条件时引入 JSqlParser 做 AST 校验。
- 复核：已核对源码 91-115 行。

### 2.6 数据源"连接测试"接受任意 JDBC URL（SSRF / JDBC 攻击面）

- 位置：`src/main/java/com/faust/service/DataSourceConfigService.java:223`
- 代码：

```java
public ConnectionTestResp connectionTest(ConnectionTestReq req) {
    try (DruidDataSource dateSource = dataSourceFactory
        .getDateSource(req.getDbType(), req.getUrl(), req.getUser(), req.getPwd());
```

- 问题：`dbType/url/user/pwd` 全部由请求体直传（`ConnectionTestReq` 仅 `@NotBlank`），服务端立即发起真实连接；无协议/主机白名单，无空间归属校验。异常时 `errMsg(e.getMessage())` 还把原始 SQL 异常回显给前端。
- 触发：`url=jdbc:mysql://10.0.0.5:3306/x`、`jdbc:postgresql://127.0.0.1:5432/x`，或带 `autoDeserialize=true`、`allowLoadLocalInfile=true` 等危险参数（具体可利用性取决于 classpath 中的驱动，**待确认**）。
- 影响：内网端口/实例测绘；驱动支持时可形成反序列化/远程加载链；异常回显放大拓扑泄露。
- 建议：
  1. 测试改为"按已保存的数据源 id 测试"，不接受原始 url；
  2. 若必须接受 url，做协议枚举白名单 + host 解析后拒绝私网/保留网段 + 拒绝危险参数；
  3. 对外只返回布尔结果，错误详情仅进日志。
- 复核：已核对源码 222-240 行。

---

## 3. P1 高风险项

### 3.1 安全与权限

#### 3.1.1 CORS 通配来源且允许携带凭证

- 位置：`src/main/java/com/faust/config/CorsConfig.java:16`
- 代码：`config.setAllowCredentials(true);` / `config.addAllowedOriginPattern("*");`
- 影响：任意站点可带凭证跨域调用本服务并按响应判断业务数据；一旦改用 Cookie 鉴权即变 CSRF + 数据外带。
- 建议：来源收敛为配置化白名单，非白名单不返回 CORS 头；凭证模式不要与通配来源同用。

#### 3.1.2 文件上传无类型白名单，且产出 24 小时公开 URL

- 位置：`src/main/java/com/faust/service/FileService.java:26`、`:61`
- 代码：

```java
String fileName = file.getOriginalFilename();
String fileKey = generateUploadFileKey(file.getOriginalFilename());
awsS3Client.upload(fileKey, file.getSize(), bufferedStream, file.getContentType());
```

- 问题：只校验大小；后缀取自客户端文件名，`Content-Type` 原样透传；无归属绑定；预签名地址有效期 1440 分钟（`application.yml:81`）。
- 触发：上传 `x.html` / `x.svg` 后引导他人访问；或无成本占用对象存储（单文件 200MB、无配额、无频率限制）。
- 影响：与站点同域时为存储型 XSS；也可作为钓鱼落地页。
- 建议：扩展名 + 魔数双白名单；`Content-Type` 由服务端映射；可内联渲染的类型强制 `Content-Disposition: attachment`；key 前缀带 `ucId`，下载前反查归属；历史消息改为存 `fileKey`、出参实时签发短效 URL（见 §4 第 12 条）。

#### 3.1.3 Excel 工具对任意 URL 发起下载（SSRF + OOM）

- 位置：`src/main/java/com/faust/agent/tools/local/ExcelTools.java:69`
- 代码：

```java
HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
```

- 问题：`excelUrl` 完全来自模型参数，无 scheme/host 校验，重定向默认跟随，响应体全量读入堆。
- 触发：提示注入让模型读 `http://169.254.169.254/latest/meta-data/...` 或内网服务；或指向数 GB 文件。
- 影响：SSRF 探测内网与云元数据；大响应 OOM。
- 建议：host 白名单 + 解析后拒私网、禁跟随重定向、`Content-Length` 上限 + 流式截断、校验 `Content-Type` 与文件魔数。

#### 3.1.4 SSE 与对话接口原样下发内部异常信息

- 位置：`src/main/java/com/faust/agent/executor/AgentExecutor.java:136`、`:170`
- 代码：`.data(ErrorMessageBuilder.errorMsg(errorMessage, errorType))`
- 问题：`error.getMessage()` 与异常类名未脱敏直接下发；`ErrorMessageBuilder` 用 `String.format` 拼 JSON，消息含 `"` 时会生成非法 JSON。
- 触发：LLM 网关 401（可能带 baseUrl/部分 key）、DB 连接失败（带 jdbc url 与账号）、Redis 超时（带内网 IP）。
- 影响：内部拓扑与凭据细节泄露；非法 JSON 让前端解析失败。
- 建议：对外只返回错误码 + 通用文案，详情落日志；改用 `JSON.toJSONString(Map.of(...))` 组装。

### 3.2 并发、事务与缓存

#### 3.2.1 `@Transactional` 包在分布式锁外层，锁等待期间占用 DB 连接

- 位置：`src/main/java/com/faust/service/AgentService.java:74`；同类：`AgentService:126/222`、`SpaceService:223`、`ToolService:169/198`、`SkillService:100/158/184`
- 代码：

```java
@Transactional(rollbackFor = Exception.class)
public AgentBaseInfoResp createAgent(AgentCreateReq req) {
    List<String> keys = relationLockKeys(LockKeys.space(spaceId), req.getToolIdList(), req.getSkillIdList());
    return lockTemplate.call(keys, LOCK_WAIT_EDIT, () -> doCreateAgent(req, spaceId));
```

- 问题：事务开启时即从池取连接，而 `tryLock` 发生在事务内（最长 2000ms）。`application.yml:32` 的 `max-active: 5` 放大该问题。
- 触发：多个请求抢占同一 `space:{id}` / `agent:{id}` 锁。
- 影响：5 个并发锁等待即可占满连接池，全站接口（含列表查询）`max-wait: 60000` 后超时，表现为大面积 500。
- 建议：把加锁提到事务外层（Controller 或非事务入口方法），锁内再调另一个 Bean 的 `@Transactional` 方法；或用 `TransactionTemplate` 把事务范围缩到最小。

#### 3.2.2 唯一键竞态未转业务码（先查后插）

- 位置：`src/main/java/com/faust/repository/impl/AgentInfoRepositoryImpl.java:39`（另 `AgentToolRepositoryImpl:24`、`AgentSkillRepositoryImpl:97`）
- 代码：

```java
if (lambdaQuery()
    .eq(AgentInfo::getAgentCode, agentInfo.getAgentCode())
    .eq(AgentInfo::getVersion, agentInfo.getVersion())
    .count() > 0) {
```

- 问题：`count` 与 `save` 非原子，且未 catch `DuplicateKeyException`。
- 触发：并发提交相同 `code + version`（空间锁只覆盖同一空间，编码是全局唯一）。
- 影响：用户拿到"系统内部异常"，无法判断是编码重复。
- 建议：保留预查做友好提示，同时 catch `DuplicateKeyException` 统一转 `*_CODE_DUPLICATE` / `AGENT_VERSION_DUPLICATE`。

#### 3.2.3 Redis saver 读锁失败静默返回空，把"有历史"当成"新会话"

- 位置：`src/main/java/com/faust/agent/checkpoint/saver/NewRedisSaver.java:210`
- 代码：

```java
tryLock = lock.tryLock(500, TimeUnit.MILLISECONDS);
if (!tryLock) {
    return List.of();
```

- 问题：读路径只等 500ms，失败返回空集合，调用方无法区分"没有检查点"与"没抢到锁"；而 `put()` 持锁期间要做全量 GZIP 反序列化 + 序列化 + 写 Redis，持锁时间随历史长度增长。
- 触发：同一会话并发续聊（双击、断线重试），或长会话 `put` 超 500ms。
- 影响：模型"失忆"，回复与上文无关，且无告警。
- 建议：读路径不要加分布式锁（读整体 value 用 `RBucket` 原子读即可）；至少失败要抛错或重试，不要用空集合伪装成功。

#### 3.2.4 DB 工具在查询异常时关闭共享连接池，且重建 Agent 时从不关闭

- 位置：`src/main/java/com/faust/agent/tools/db/DbToolExecutor.java:50`、`src/main/java/com/faust/agent/tools/db/DbToolExecutor.java:57`
- 代码：

```java
closeDataSource();
```

- 问题：`catch (Exception e)` 里无条件关闭实例级 `DruidDataSource`；而 `AgentFactory` 开了 `parallelToolExecution(true)`，同一轮内并发调用会命中同一实例，`close()` 会关掉在用连接。另一面：`closeDataSource()` 全工程只有这一处调用（异常路径），每次重建 Agent 都 new 一批 `DbToolExecutor`，旧实例的 Druid 池无人关闭。
- 触发：一次 SQL 失败 + 同轮并行调用；或反复编辑/发布绑定 DB 工具的 Agent（每次 bump 代际号都会重建）。
- 影响：正常查询被无关错误带崩（`connection closed`）；每份池 `initialSize=1` 至少 1 条物理连接长期不释放，最终打满数据库 `max_connections`。
- 建议：查询失败只返回错误结果不关池；池按 `dataSourceConfigId` 全局单例管理 + `@PreDestroy` 统一关闭，或在缓存 `removalListener` 里关闭；`dataSource` 字段补 `volatile`（当前双检锁发布不安全）。

#### 3.2.5 子 Agent 互相引用无环检测

- 位置：`src/main/java/com/faust/agent/factory/ToolCallbackFactory.java:76`
- 代码：`ReactAgent subAgent = agentFactory.getSubAgent(subAgentInfo.getAgentCode(), subAgentInfo.getVersion());`
- 触发：A 引用 B、B 引用 A（或自引用）。
- 影响：Guava 对同 key 重入加载抛 `IllegalStateException: Recursive load of ...`，被包成 `INTERNAL_ERROR` 并透传类名，A 与 B 同时永久不可用；链更长时可能 `StackOverflowError`。
- 建议：构建前用 `ThreadLocal<Set<String>>` 记录路径，命中环抛可读业务异常（"存在循环引用 A→B→A"）；保存子 Agent 关联时做一次 DFS 直接拒绝。

### 3.3 接口契约与健壮性

#### 3.3.1 `updateAgent` 在关联列表为 null 时静默清空全部绑定

- 位置：`src/main/java/com/faust/service/AgentService.java:163`
- 代码：

```java
agentSkillRelService.unbindAllSkill(req.getAgentId());
if (!CollectionUtils.isEmpty(req.getSkillIdList())) {
```

- 问题：技能"先无条件解绑再按需绑定"，工具在 `else` 分支同样 `unbindAllTool`；`AgentUpdateReq` 的两个列表既无 `@NotNull`，也不区分"未传"与"传空数组"。
- 触发：只改名字，请求体不带 `skillIdList`/`toolIdList`。
- 影响：Agent 的能力被静默清空；若处于 PUBLISHED，缓存重建后线上实例直接失去技能与工具，无二次确认、无审计。
- 建议：定义 `null = 不改，[] = 清空` 语义，或对两字段加 `@NotNull` 并在接口文档写明"全量替换必须显式传值"。

#### 3.3.2 发布未下架同 `agentCode` 的其他版本，与接口文档矛盾

- 位置：`src/main/java/com/faust/service/AgentService.java:184`（实现）、`src/main/java/com/faust/controller/AgentController.java:43`（文档）
- 问题：`@Operation` 写明"同一 agentCode 下其他已发布版本自动取消发布"，实现只把当前行置为 PUBLISHED，DB 也没有"每个 code 仅一个 PUBLISHED"的约束。
- 触发：依次发布 v1、v2。
- 影响：卡片列表出现同一 Agent 多张卡片；旧版本仍可被加载对话；叠加 §2.2 后整条链路 500。
- 建议：锁内先批量下架同 code 的其他 PUBLISHED 行，或用生成列 + 唯一索引在 DB 层保证。

#### 3.3.3 `chatId` 可由客户端指定，`updateConversation` 缺少 `uc_id` 条件

- 位置：`src/main/java/com/faust/service/ChatService.java:169`、`src/main/java/com/faust/repository/impl/AgentConversationRecordRepositoryImpl.java:62`
- 代码：

```java
updateWrapper.eq(AgentConversationRecord::getChatId, req.getChatId())
```

- 问题：`chatId` 只在为空时由服务端生成，非空则直接使用；查不到（或不属于自己）就走"新会话"分支插入客户端给的值；更新条件只有 `chat_id` 没有 `uc_id`。
- 触发：B 传 A 的 `chatId` → 撞 `uk_chat_id` 抛 `DuplicateKeyException` 变 500；或后续调整唯一键时改写他人会话的 `env_var`。
- 影响：稳定 500；潜在跨用户会话污染。
- 建议：`chatId` 只由服务端生成；续聊要求必须命中且 `ucId` 匹配；`updateConversation` 签名补 `ucId` 并加 `.eq(getUcId, ucId)`；创建路径 catch `DuplicateKeyException` 转 update。

#### 3.3.4 SSE 端点的错误路径返回 JSON 响应体

- 位置：`src/main/java/com/faust/controller/ChatController.java:39`
- 代码：`@PostMapping(path = "/start/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)`
- 问题：该端点返回 `Flux` 之前会同步执行鉴权（无权限 1033、agent 不存在 1001），异常由 `@ExceptionHandler` 以 `ApiResponse` JSON 写出，但响应已声明 `text/event-stream`。
- 影响：SSE 解析器无法识别 JSON 帧，前端只能看到"连接异常"，无法区分"无权限"与"网络错误"。
- 建议：在 `chatStream` 内部把鉴权异常转为 `ServerSentEvent.builder().event("error").data(...)`；同时显式配置 `spring.mvc.async.request-timeout` 并加心跳帧。

#### 3.3.5 分页参数无上下界，且有 `last("limit " + pageSize)` 拼接

- 位置：`src/main/java/com/faust/repository/impl/MessageRecordItemRepositoryImpl.java:37`；同类 `AgentListQueryReq:20`、`ToolListQueryReq:22`、`SkillListQueryReq:22`、`ChatListQueryReq:21`
- 代码：`wrapper.last("limit " + pageSize);`
- 触发：`pageSize=-1` → `limit -1` 非法 SQL 500；`pageSize=10000000` → 单请求拉全表；JSON 显式传 `null` → `lastMessageId > 0` 拆箱 NPE。
- 影响：500 与 OOM 风险。
- 建议：DTO 加 `@Min(1) @Max(100)` + 默认值，仓储入口再 `Math.min/max` 兜底；改用 `Page` 分页。

#### 3.3.6 ReAct 循环缺少迭代上限与整体超时

- 位置：`src/main/java/com/faust/agent/factory/AgentFactory.java:466`
- 代码：`Builder builder = ReactAgent.builder()`
- 问题：未显式设置 `recursionLimit`，框架默认 `Integer.MAX_VALUE`（**依赖框架版本默认值，建议实测确认**），终止条件只有"最后一条 AssistantMessage 不含 toolCalls"。
- 触发：模型反复调用同一失败工具；工具一直返回"看起来失败"的结果。
- 影响：单次请求可发起上百次 LLM 调用，Token 成本失控、请求不返回、占满 `agentParallelExecutor`（`CPU*2` 线程 + `CallerRunsPolicy`）。
- 建议：显式设 `recursionLimit` 12~25，并在 `doChatStream` 外层包 `Flux.timeout(...)` 兜底；同时把 `AgentModelMateInfo.timeout` 真正接到 HTTP 客户端（当前只赋值未使用）。

#### 3.3.7 全局异常处理覆盖不全，`SkillController` 重复注册

- 位置：`src/main/java/com/faust/exception/GlobalExceptionHandler.java:17`
- 代码：

```java
@ControllerAdvice(assignableTypes = {
    AgentController.class,
    ToolController.class,
    SkillController.class,
    DictController.class,
    ChatController.class,
    SpaceController.class,
    DataSourceConfigController.class,
    SkillController.class
})
```

- 问题：漏了 `FileController`、`GlobalEnvConfigController`、`WelcomeController`；`SkillController` 列了两次。
- 触发：上传超限（`FILE_SIZE_EXCEED`）、环境变量 key 非法。
- 影响：返回 Spring 默认错误结构，前端按 `code` 分支全部失效，用户看到"系统异常"。
- 建议：去掉 `assignableTypes`，改为 `@RestControllerAdvice` + `basePackages = "com.faust.controller"`；补 `MaxUploadSizeExceededException` 映射。

#### 3.3.8 已发布资源仍可原地修改（工具/技能）

- 位置：`src/main/java/com/faust/service/ToolService.java:112`、`src/main/java/com/faust/service/SkillService.java:105`
- 问题：`doUpdate` 只校验 NATIVE 与空间，不校验 `status == PUBLISHED`；而 delete/takeOff 对已发布是有强约束的，口径不一致。
- 影响：线上 Agent 依赖的工具定义（含 schema、数据源指向）被静默改写并立即生效（会 evict 缓存），"发布审核"语义失效。
- 建议：已发布资源走"复制为新草稿"或强制先下架；至少要求 update 带 `expectedVersion` 做乐观校验。

#### 3.3.9 `ToolService.update` 修改 `toolCode` 不校验唯一

- 位置：`src/main/java/com/faust/service/ToolService.java:123`
- 代码：`tool.setToolCode(req.getToolCode());`
- 问题：create 有 count 校验，update 直接 `updateById`；`uk_tool_code` 是全局唯一键，冲突即 `DuplicateKeyException` → 500；`updateById` 返回值也未判断。
- 建议：复用 `validateToolCode(code, excludeId)`，catch 冲突转 `TOOL_CODE_DUPLICATE`，并校验影响行数。

---

## 4. P2 改进项

| # | 位置 | 问题 | 建议 |
| --- | --- | --- | --- |
| 1 | `SpaceService.java:79` | `mySpaces()` 自调用 `ensurePersonalSpace()`，`@Transactional` 不生效 | 抽到独立 Bean，或 `mySpaces` 上直接标事务 |
| 2 | `DataSourceConfigService.java:161`、`AgentService.java:239` | 注释写"逻辑删除 / @TableLogic"，实际全是物理删除（项目无 `@TableLogic`） | 更正注释与 `@Operation` 描述为"物理删除，不可恢复" |
| 3 | `AgentService.java:143` | `updateAgent` 允许改 `agentCode`，无唯一校验，且 `evictAgentCache` 仍用旧 code，会切断会话与环境变量关联 | 禁止更新 `agentCode`，或视为新建 |
| 4 | `AgentService.java:227` | 删除 Agent 不清理会话/消息/checkpoint，也不校验被 `sub_agent_id` 引用（会留下悬空引用） | 删除前检查引用并阻断；会话数据按策略异步清理 |
| 5 | `DataSourceConfigService.java:202` | 详情/列表原样回传 JDBC URL，可能含 `?user=&password=` | 入库剥离凭据参数，出参脱敏为 `jdbc:mysql://host:port/db` |
| 6 | `AgentController.java:21` | 类上缺 `@Validated`，方法参数上的 `@NotBlank` 静默失效 | 补 `@Validated`（参考 `SpaceController:31`） |
| 7 | `AgentFactory.java:185` | 缓存 key 用 `@` 拼接，`version` 未做字符校验，含 `@` 会切错段、误判子 Agent | `version` 加 `@Pattern`；key 改用不可出现的分隔符或 hash |
| 8 | `AgentFactory.java:167` | `subAgentDependencies` 只在 `evictByAgent` 清理，Guava 的 `maximumSize`/`expireAfterAccess` 淘汰不回调，索引无界增长 | 用 `removalListener` 同步清理索引 |
| 9 | `AgentFactory.java:193` | 缓存加载失败丢原始错误码，把 `ExecutionException.getMessage()`（含类名）抛给前端 | 取 `getCause()`，业务异常原样抛；未知异常记日志 |
| 10 | `AgentExecutor.java:66` | MDC 的 `chatId` 在流开始前 put、订阅前 remove，流内日志全部丢失会话标识 | 移到 `doOnSubscribe`/`doFinally`，或由 hook 从 metadata 取 |
| 11 | `NewRedisSaver.java:315` | checkpoint 只 push 不裁剪，每次 put 全量 GZIP 序列化并持锁，长会话线性变慢 | 只保留最近 N 个，或改用 `RList` 分片；序列化移出锁外 |
| 12 | `FileService.java:34` | 预签名 URL 有效期 24h 被持久化进 `file_list`，历史消息附件过期后全部 403 | 只存 `fileKey`，出参实时签发短效 URL |
| 13 | `AgentInfoRepositoryImpl.java:156` | 标签筛选用 `LIKE "%\"tag\"%"` 匹配 JSON 列，`%`/`_` 未转义且必然全表扫 | 改 `JSON_CONTAINS` + 生成列索引 |
| 14 | `schema.sql:252` | `message_record_item` 索引为 `(chat_id, date_time)`，但翻页按 `id` 排序 | 增 `KEY idx_chat_id_id (chat_id, id)` |
| 15 | `schema.sql:154` | `agent_tool.data_source_config_id` 无索引，数据源变更时全表扫且发生在持锁事务内 | 补索引 |
| 16 | `AgentSystemPromptRepositoryImpl.java:19` | 实体注释声称有 version 历史快照，表结构无该列且 `uk_agent_id` 只允许一行 | 删除误导注释，或补列并去掉唯一键 |
| 17 | `AgentInfoDetailResp.java:31` | `useNum` 声明为 `String`，实体是 `Integer`，`BeanUtils.copyProperties` 静默跳过 → 详情接口永远返回 null | 改为 `Integer` |
| 18 | `AgentSkillRelRepositoryImpl.java:55` | `bindIfPublished` 插入 0 行可能因 agent 不存在，却只报"跨空间引用" | 回查时区分 4 种原因分别抛码 |

---

## 5. 分模块问题分布

| 模块 | P0 | P1 | P2 | 主要问题类型 |
| --- | --- | --- | --- | --- |
| controller | 1 | 1 | 1 | 越权入口、SSE 错误体、`@Validated` 缺失 |
| service | 2 | 6 | 5 | 事务与锁边界、绑定语义、级联清理、URL 脱敏 |
| repository / mapper | 1 | 1 | 6 | `getOne` 多行、唯一键竞态、索引与查询不匹配 |
| agent（运行时/工具） | 2 | 6 | 4 | 只读绕过、SSRF、连接池、无迭代上限、环引用 |
| config / 拦截器 | 1 | 1 | 0 | 身份可信度、CORS、默认口令 |
| exception | 0 | 1 | 0 | 覆盖不全 |
| DDL / 配置 | 1 | 0 | 3 | 硬编码凭据、索引缺失 |

---

## 6. 建议修复批次

**批次一（上线前，1~2 天）**
1. §2.1 会话记忆加归属校验
2. §2.2 `getAgentByCode` 限定行数 / 改按版本查
3. §2.4 轮换并移除配置中的凭据默认值，生产关闭 Druid 控制台
4. §2.5 DB 工具改前缀白名单 + 数据库只读账号
5. §2.6 连接测试改为按 id 或做 host 白名单
6. §3.1.1 CORS 白名单化

**批次二（本周）**
7. §3.2.1 锁移到事务外层；同时评估 `max-active: 5` 是否够用
8. §3.3.1 关联列表 null 语义；§3.3.2 发布下架兄弟版本
9. §3.3.4 SSE 错误帧；§3.3.7 全局异常处理收敛
10. §3.3.5 分页参数校验
11. §3.2.2 / §3.3.9 唯一键冲突转业务码

**批次三（随迭代）**
12. §3.2.3~§3.2.5 缓存与连接池治理、环检测
13. §3.3.6 迭代上限与超时
14. §4 全部 P2

---

## 7. 已排除的疑似问题（避免重复排查）

以下是审查过程中被提出但经复核**不成立**的结论，记录在此以免后续重复讨论：

| 疑似问题 | 复核结论 |
| --- | --- |
| `doUpdateAgent` 的 CAS 未判断影响行数 | 不成立，`AgentInfoRepositoryImpl.updateAgent` 内部判了 `== 0` 并抛 1045 |
| 分布式锁"先释放后提交" | 不成立，`DistributedLockTemplate` 已在 `afterCommit` 后释放 |
| 字符串拼接 SQL 注入 | 未发现，`pageSize` 为 `Integer` 类型，不存在注入；问题仅是非法值与无上限 |
| 响应体回传数据库密码 / API Key | 详情接口已剔除 `pwd`/`apiKey` 字段；问题是 URL 中的内联凭据（§4 第 5 条） |
| req 直接落到实体导致 mass assignment | 未发现，`*UpdateReq` 不含 `workspaceId`/`status`，服务层显式重建实体 |
| `assertSameSpace` 对 `targetWorkspaceId == null` 放行 | 当前 schema 中 `workspace_id` 均 `NOT NULL`，暂不构成越权，列为观察项 |

> 另需与运维确认的部署前提：`user-id` 头是否由网关强制覆盖、服务端口是否对外可达。这是 §2.3 定级的关键。

---

## 附录：审查覆盖清单

- `controller/`：10 个 Controller 全覆盖
- `service/`：8 个 Service 全覆盖
- `repository/` + `repository/impl/`：16 组全覆盖
- `entity/` 16 个、`mapper/` 16 个、`dto/` 全部子包
- `agent/`：cache、checkpoint、executor、factory、hooks、skills、tools 全部子包
- `support/`：`AfterCommit`、`AwsS3Client`、`SpaceGuard`、`lock/`
- `common/`、`config/`、`exception/`、`enums/`
- `resources`：`application.yml`、`logback-spring.xml`、`db/schema.sql` 与全部 `upgrade-*.sql`
