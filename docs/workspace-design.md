# 工作空间（Workspace）设计与实施方案

> 目标：用「工作空间 + 成员」取代现有白名单，作为 **Agent / 工具 / 技能 / 数据源** 的统一权限边界。
> 本文覆盖：数据模型、后端改造、前端改造、接口清单、数据迁移、遗漏点与风险、分阶段计划、验收清单。
> 适用范围：`faust-back-end`（Spring Boot 3.5 + MyBatis-Plus + MySQL 8）与 `faust-front-end`（React + TS + Vite）。

---

## 0. 结论摘要（TL;DR）

1. **两条基础表**：`work_space`（空间）+ `work_space_user`（空间成员）。个人空间由系统按 `owner_uc_id` 自动生成，且 **个人空间也写一条成员记录**（`role=OWNER`），这样"能不能访问"永远只查一张表。
2. **四张业务表加 `workspace_id`**：`agent_info`、`agent_tool`、`agent_skill`、`data_source_config`；`workspace_id = 0` 保留给 **系统内置 NATIVE 工具**（全局共享、只读）。
3. **两个核心原则**（后面所有改造都围绕它们）：
   - **列表按"当前空间"过滤**：当前空间由请求头 `x-space-id` 决定，缺省回落到个人空间（保证旧客户端/curl 可用）。
   - **单个资源按"资源所属空间"鉴权**：先按 id/code 捞资源，再校验 `当前用户是该资源 workspace 的成员`。这样即使前端乱改 header，也越权不到别的空间（防 IDOR）。
4. **白名单整体下线**：删表、删后端 6 个类 + 4 个 DTO、删前端弹窗与 3 个 service 方法。原 `card/list`、`sub/agent/list` 的白名单过滤改为"当前空间内已发布"。
5. **编码仍全局唯一**（`agent_code` / `tool_code` / `skill_code`），不改现有唯一索引。理由见 §2 D2：`agentCode` 是 URL 与运行时定位键（`/chat/:agentCode`、`AgentFactory` 按 code+version 加载），改成空间内唯一会让整条 by-code 链路都要带空间维度，收益低、漏改风险高。

---

## 1. 需求拆解

| 编号 | 需求 | 落点 |
|---|---|---|
| R1 | 新增 `work_space`、`work_space_user` 两张表 | §3 DDL |
| R2 | 两种空间类型：个人 / 团队 | `work_space.space_type` |
| R3 | 个人空间：只有自己，默认生成 | 后端懒创建 + 唯一索引兜底 |
| R4 | 团队空间：可新建、可加人 | `POST /api/space/create`、`/member/add` |
| R5 | 两种空间可切换 | 侧边栏空间切换器 + `x-space-id` 头 |
| R6 | 新增「空间管理」tab | `pages/spaces` + 路由 `/spaces` |
| R7 | 自己创建的团队空间：重命名 / 加人 / 删人 / 解散 | 仅 `OWNER` 可重命名、删人、解散 |
| R8 | 加入的空间：加人 / 退出 | 成员即可加人；退出仅对 `MEMBER` |
| R9 | 所有资源关联 `workspace_id`，仅空间内可见/可编辑/可对话/可删 | §4 统一 Guard |
| R10 | 去掉白名单，用空间做隔离 | §4.5 删除清单 |

---

## 2. 关键设计决策（含取舍）

### D1 当前空间用「请求头」而不是「每个接口加参数」

- **选请求头 `x-space-id`**：一个注入点覆盖所有动词，SSE 上传也能带；和现有 `user-id` 的模式一致（`lib/http.ts: buildHeaders`）。
- 代价：前端切换空间时请求参数不变，需要显式触发刷新 → 用 React `key={spaceId}` 重新挂载页面（§6.3），不污染各页面的 query 结构。
- 兜底：header 缺失时后端回落到个人空间，`curl` 与旧前端不会 500。

### D2 资源编码保持**全局唯一**

`agent_code` 等编码会进入 URL（`/chat/:agentCode`）和运行时装载（`AgentFactory#getPublishedAgentByCodeAndVersion`）。若改成"空间内唯一"，需要：
`agentCode` 解析全部带 `spaceId` → `AgentFactory` / `AgentExecutor` / `chat` 链路签名全改 → 任何一处漏传就是串空间。

**结论**：唯一索引不动；跨空间重名返回 `编码已存在`（文案不暴露对方空间）。若后续确实要空间内重名，改造清单见 §8-3。

### D3 `workspace_id = 0` 表示系统级共享资源（仅 NATIVE 工具）

NATIVE 工具是运行时内置能力（`bean_name` 指向 Spring Bean），不属于任何团队。做法：
- 迁移时把 `type = 'NATIVE'` 的行置为 `workspace_id = 0`；
- 列表查询统一 `workspace_id IN (0, :spaceId)`；
- `workspace_id = 0` 的数据一律禁止写操作（现在也已由 `TOOL_NATIVE_NOT_OPERABLE` 拦住）。

否则新建团队空间里看不到原生工具，Agent 无法绑定 `ExcelTools`、`CodeExecuteTools` 等。

### D4 个人空间也建成员记录

`work_space_user` 里给个人空间写 `(space_id, owner_uc_id, OWNER)` 一行。好处：所有鉴权都是同一条 SQL：

```sql
SELECT role FROM work_space_user WHERE space_id = ? AND uc_id = ?
```

坏处：多一次插入（仅首次），可忽略。

### D5 解散空间：先"清空"再"解散"，不做级联硬删

`removeById` 在当前工程是**物理删除**（entity 上没有 `@TableLogic`，schema 里也没有 `is_deleted`）。若解散时级联删资源，一发不可收拾。

Phase 1 规则：
- 空间内资源数（Agent + 工具 + 技能 + 数据源）> 0 → 拒绝解散，返回 `SPACE_NOT_EMPTY`，提示"请先转移或删除资源"；
- 资源为 0 → 删成员记录 + 删空间记录；
- 前端二次确认（destructive），并展示当前资源数量。

Phase 2 补「一键转移到个人空间」，彻底解决"解散前逐个挪"的体验问题。

### D6 团队空间内是"共享可编辑"，不做资源级 ACL

需求原文是"仅空间内才能查看、编辑、对话、删除"，即空间即权限粒度。**空间内成员对彼此的 Agent 都可编辑**。
若要"仅创建者可改"，需在每个资源上加 `owner_uc_id` 做二次判断；建议先不做，但在 UI 上展示创建人，避免误改（§8-8）。

---

## 3. 数据模型

### 3.1 新表 DDL（可直接追加到 `src/main/resources/db/schema.sql`）

```sql
-- -------------------------------------------------------
-- 14. 工作空间：PERSONAL（个人，唯一）/ TEAM（团队，可多个）
--     workspace_id = 0 由迁移脚本创建，代表"系统空间"，仅存放 NATIVE 工具
-- -------------------------------------------------------
CREATE TABLE IF NOT EXISTS `work_space`
(
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`           VARCHAR(128) NOT NULL COMMENT '空间名称',
    `space_type`     VARCHAR(16)  NOT NULL COMMENT '类型：PERSONAL / TEAM',
    `owner_uc_id`    BIGINT       NOT NULL COMMENT '创建者/所有者 UCID',
    `owner_name`     VARCHAR(128)          DEFAULT NULL COMMENT '创建者昵称快照',
    `description`    VARCHAR(512)          DEFAULT NULL COMMENT '空间描述',
    `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `create_by`      BIGINT                DEFAULT NULL COMMENT '创建人 UCID',
    `update_by`      BIGINT                DEFAULT NULL COMMENT '更新人 UCID',
    -- 仅 PERSONAL 行有值，配合唯一索引保证"一人一个个人空间"
    `personal_owner` BIGINT GENERATED ALWAYS AS (IF(`space_type` = 'PERSONAL', `owner_uc_id`, NULL)) STORED,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_personal_owner` (`personal_owner`),
    KEY `idx_owner_type` (`owner_uc_id`, `space_type`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='工作空间';

-- -------------------------------------------------------
-- 15. 工作空间成员（个人空间也有一行，role=OWNER）
-- -------------------------------------------------------
CREATE TABLE IF NOT EXISTS `work_space_user`
(
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `space_id`    BIGINT       NOT NULL COMMENT '关联 work_space.id',
    `uc_id`       BIGINT       NOT NULL COMMENT '成员 UCID',
    `uc_name`     VARCHAR(128)          DEFAULT NULL COMMENT '成员昵称快照',
    `role`        VARCHAR(16)  NOT NULL DEFAULT 'MEMBER' COMMENT '角色：OWNER / MEMBER',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
    `create_by`   BIGINT                DEFAULT NULL COMMENT '邀请人 UCID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_space_uc` (`space_id`, `uc_id`),
    KEY `idx_uc_id` (`uc_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='工作空间成员';
```

> `personal_owner` 是 STORED 生成列：`PERSONAL` 行取 `owner_uc_id`，`TEAM` 行为 `NULL`。MySQL 唯一索引允许多个 `NULL`，所以"一人一个个人空间 + 团队不限个数"能被数据库层面强约束，不用靠应用层兜。

### 3.2 业务表加列

```sql
ALTER TABLE `agent_info`
    ADD COLUMN `workspace_id` BIGINT NOT NULL DEFAULT 0 COMMENT '所属工作空间' AFTER `id`,
    ADD KEY `idx_workspace_status` (`workspace_id`, `status`);

ALTER TABLE `agent_tool`
    ADD COLUMN `workspace_id` BIGINT NOT NULL DEFAULT 0 COMMENT '所属工作空间（0=系统内置）' AFTER `id`,
    ADD KEY `idx_workspace_status` (`workspace_id`, `status`);

ALTER TABLE `agent_skill`
    ADD COLUMN `workspace_id` BIGINT NOT NULL DEFAULT 0 COMMENT '所属工作空间' AFTER `id`,
    ADD KEY `idx_workspace_status` (`workspace_id`, `status`);

ALTER TABLE `data_source_config`
    ADD COLUMN `workspace_id` BIGINT NOT NULL DEFAULT 0 COMMENT '所属工作空间' AFTER `id`,
    ADD KEY `idx_workspace_id` (`workspace_id`);
```

`conversation_record` 建议同步加（仅用于空间维度统计/审计，隔离仍按 `uc_id`）：

```sql
ALTER TABLE `conversation_record`
    ADD COLUMN `workspace_id` BIGINT DEFAULT NULL COMMENT '会话所属空间快照' AFTER `uc_id`;
```

> 注意：`NOT NULL DEFAULT 0` 让存量数据先落在"系统空间"。真正的归属由 §7 的回填脚本改写，避免 DDL 期间因为 NULL 触发查询漏数据。

### 3.3 ER 关系

```
work_space 1 ─── n work_space_user
     │ 1
     ├── n agent_info ──1:n── agent_model_config / agent_system_prompt
     │        └─ n:m ── agent_skill_rel / agent_tool_rel  （两端必须同空间）
     ├── n agent_tool ──n:1── data_source_config（DB 工具）、agent_info（SUB_AGENT 工具）
     ├── n agent_skill ──1:n── agent_skill_item
     └── n data_source_config
```

---

## 4. 后端实现

### 4.1 新增文件清单

| 文件 | 说明 |
|---|---|
| `entity/WorkSpace.java` | `@TableName("work_space")`，`personalOwner` 标 `@TableField(exist = false)`（生成列只读，不参与写入） |
| `entity/WorkSpaceUser.java` | 成员 |
| `enums/SpaceTypeEnum.java` | `PERSONAL("PERSONAL","个人空间")`、`TEAM("TEAM","团队空间")` |
| `enums/SpaceRoleEnum.java` | `OWNER`、`MEMBER`（预留 `ADMIN`） |
| `mapper/WorkSpaceMapper.java`、`mapper/WorkSpaceUserMapper.java` | `BaseMapper` |
| `repository/WorkSpaceRepository(+Impl)`、`repository/WorkSpaceUserRepository(+Impl)` | 对齐现有 `repository` + `ServiceImpl` 分层 |
| `service/SpaceService.java` | 个人空间自举、CRUD、成员管理、资源计数 |
| `controller/SpaceController.java` | `/api/space/**` |
| `common/context/SpaceContext.java` | ThreadLocal，持有 `spaceId` / `role` |
| `interceptor/SpaceInterceptor.java` | 解析并校验 `x-space-id` |
| `dto/space/req/*` | `SpaceCreateReq`、`SpaceRenameReq`、`SpaceMemberAddReq`、`SpaceMemberOperateReq`、`SpaceIdReq` |
| `dto/space/resp/*` | `SpaceInfoResp`（列表项）、`SpaceDetailResp`（含资源计数）、`SpaceMemberResp` |
| `support/SpaceGuard.java` | `assertMember(spaceId)`、`assertOwner(spaceId)`、`assertResourceSpace(workspaceId)` |

### 4.2 上下文与拦截器

```java
// common/context/SpaceContext.java
public final class SpaceContext {
    private static final ThreadLocal<Long> SPACE_ID = new ThreadLocal<>();
    private static final ThreadLocal<SpaceRoleEnum> ROLE = new ThreadLocal<>();

    public static void set(Long spaceId, SpaceRoleEnum role) { SPACE_ID.set(spaceId); ROLE.set(role); }
    public static Long getSpaceId() { return SPACE_ID.get(); }
    public static Long requireSpaceId() {
        Long id = SPACE_ID.get();
        ThrowExceptionFunction.isNull(id).throwMessage(ErrorMessageEnum.SPACE_NOT_SELECTED);
        return id;
    }
    public static SpaceRoleEnum getRole() { return ROLE.get(); }
    public static void remove() { SPACE_ID.remove(); ROLE.remove(); }
}
```

```java
// interceptor/SpaceInterceptor.java —— 只做"解析 + 校验成员身份"，不写库
@Component
@RequiredArgsConstructor
public class SpaceInterceptor implements HandlerInterceptor {
    private final SpaceService spaceService;   // 内部只读查询

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod)) return true;
        Long ucId = UserContext.getUcId();
        Long spaceId = parse(request.getHeader(SpaceConst.HEADER));   // "x-space-id"
        if (spaceId == null) {
            // 兜底：老客户端/curl 没带头 → 落到个人空间（读，不创建）
            SpaceContext.set(spaceService.personalSpaceId(ucId), SpaceRoleEnum.OWNER);
            return true;
        }
        SpaceRoleEnum role = spaceService.memberRole(spaceId, ucId);
        // header 有值但不是成员 → 直接拒绝，绝不静默回落（避免"以为在 A 实际写进 B"）
        ThrowExceptionFunction.isNull(role).throwMessage(ErrorMessageEnum.SPACE_NO_PERMISSION);
        SpaceContext.set(spaceId, role);
        return true;
    }

    @Override
    public void afterCompletion(...) { SpaceContext.remove(); }
}
```

```java
// config/WebConfig.java —— 必须排在 LoginInterceptor 之后
registry.addInterceptor(loginInterceptor).addPathPatterns("/**").order(1);
registry.addInterceptor(spaceInterceptor).addPathPatterns("/**")
        .excludePathPatterns("/api/space/**")   // 空间接口自己判断（否则解散/退出会被当前空间卡住）
        .order(2);
```

> **响应式注意**：SSE 走 `Flux`，`ThreadLocal` 不会自动跨线程。凡是需要空间信息的下游逻辑，必须在 **Controller / Service 的同步段**取出来，再以参数或 DTO 字段传进去 —— 与现在 `ChatController` 处理 `ucId` 的方式保持一致（`request.setUcId(UserContext.getUcId())`）。

### 4.3 统一鉴权工具

```java
// support/SpaceGuard.java
@Component
@RequiredArgsConstructor
public class SpaceGuard {
    private final WorkSpaceUserRepository memberRepo;

    /** 列表/新建：当前空间 */
    public Long currentSpaceId() { return SpaceContext.requireSpaceId(); }

    /** 单资源：按资源自己的 workspaceId 鉴权，忽略 header */
    public void assertMember(Long workspaceId) {
        if (workspaceId != null && workspaceId == 0L) return;          // 系统资源，人人可读
        ThrowExceptionFunction.isTrue(memberRepo.memberRole(workspaceId, UserContext.getUcId()) == null)
            .throwMessage(ErrorMessageEnum.SPACE_NO_PERMISSION);
    }

    public void assertOwner(Long spaceId) {
        ThrowExceptionFunction.isTrue(memberRepo.memberRole(spaceId, UserContext.getUcId()) != SpaceRoleEnum.OWNER)
            .throwMessage(ErrorMessageEnum.SPACE_NO_PERMISSION);
    }

    /** 跨空间引用校验：Agent 绑工具/技能、工具绑数据源/子 Agent */
    public void assertSameSpace(Long ownerWorkspaceId, Long targetWorkspaceId) {
        ThrowExceptionFunction.isTrue(targetWorkspaceId != 0L && !Objects.equals(ownerWorkspaceId, targetWorkspaceId))
            .throwMessage(ErrorMessageEnum.RESOURCE_SPACE_MISMATCH);
    }
}
```

### 4.4 各服务改造点（逐文件）

| 文件 | 改动 | 代码要点 |
|---|---|---|
| `repository/impl/AgentInfoRepositoryImpl.java` | `listAgents` 增加 `workspaceId` 入参；`listPublishedByUcId` **删除**（白名单连表）；`listPublishedSubAgentByUcId` 改为 `listPublishedSubAgent(workspaceId)`；`getAgentByCode` 增加越权校验由 Service 做 | `wrapper.in(AgentInfo::getWorkspaceId, List.of(0L, spaceId))` |
| `repository/AgentInfoRepository.java` | 同步签名 | — |
| `service/AgentService.java` | `create` 写入 `workspaceId = SpaceGuard.currentSpaceId()`；`pageList` 用当前空间；`cardPublishedPageList` 改为"当前空间 + PUBLISHED"；`detail/base/publish/takeOff/delete` 先 `assertMember(agent.getWorkspaceId())`；绑技能/工具时 `assertSameSpace` | 见下方片段 |
| `service/ToolService.java` | `pageList` 加 `workspace_id IN (0, space)`；`create` 写 space；`detail/update/publish/takeOff/delete` 加 `assertMember`；DB 工具校验 `data_source_config.workspaceId`；SUB_AGENT 校验子 Agent 同空间 | 同上 |
| `service/SkillService.java` | `pageList` / `publishedList` 加 space 过滤；`create` 写 space；`detail/update/publish/takeOff/delete` 加 `assertMember` | — |
| `service/DataSourceConfigService.java` | `pageList` 加 space 过滤；`create` 写 space；`detail/update/delete/connectionTest` 加 `assertMember` | `connectionTest` 也要拦：否则可拿别人空间的库探测 |
| `service/ChatService.java` | `chat` / `chatStream` 入口先按 `agentCode` 捞 Agent，再 `assertMember(agent.getWorkspaceId())` | 同步段执行，不要放进 Flux |
| `entity/{AgentInfo,AgentTool,AgentSkill,DataSourceConfig}.java` | 新增 `private Long workspaceId;` | — |
| `enums/ErrorMessageEnum.java` | 新增错误码 1032+ | 见 §4.6 |
| `controller/AgentWhitelistController.java` 等 | 删除 | §4.5 |

`AgentService` 关键片段：

```java
public Long create(AgentCreateReq req) {
    Long spaceId = spaceGuard.currentSpaceId();          // 新建一定落在"当前空间"
    AgentInfo agent = new AgentInfo();
    BeanUtils.copyProperties(req, agent);
    agent.setWorkspaceId(spaceId);
    ...
}

public Page<AgentPageInfoResp> pageList(AgentListQueryReq req) {
    Long spaceId = spaceGuard.currentSpaceId();
    Page<AgentInfo> agentPage = agentInfoService.listAgents(page, spaceId, req.getKeyword(),
        req.getStatus(), req.getClassification(), req.getTags());
    ...
}

public AgentInfoDetailResp detail(String agentCode) {
    AgentInfo agent = agentInfoService.getAgentByCode(agentCode);
    ThrowExceptionFunction.isNull(agent).throwMessage(ErrorMessageEnum.AGENT_NOT_FOUND);
    spaceGuard.assertMember(agent.getWorkspaceId());     // ← 单资源鉴权
    ...
}
```

`AgentInfoRepositoryImpl#buildAgentQueryWrapper` 增加：

```java
wrapper.in(AgentInfo::getWorkspaceId, Arrays.asList(SystemSpace.ID, spaceId)); // 0 号系统空间
```

### 4.5 白名单下线清单

| 删除对象 | 路径 |
|---|---|
| Controller | `controller/AgentWhitelistController.java` |
| Service | `service/AgentWhitelistService.java` |
| Entity / Mapper / Repository(+Impl) | `entity/AgentWhitelist.java`、`mapper/AgentWhitelistMapper.java`、`repository/AgentWhitelistRepository.java`、`repository/impl/AgentWhitelistRepositoryImpl.java` |
| DTO × 4 | `dto/whitelist/req/*`（3 个）、`dto/whitelist/resp/*`（2 个） |
| 错误码 | `AGENT_WHITELIST_NOT_FOUND`、`AGENT_WHITELIST_DUPLICATE` |
| 表 | `DROP TABLE agent_whitelist;`（迁移脚本内，确认无回归后再执行） |
| 前端 | `pages/agents/whitelist-dialog.tsx`、`services/agent-center.ts` 的白名单段 |

顺带收益：`listPublishedByUcId` 里 `inSql("... WHERE uc_id = " + ucId)` 的**字符串拼接注入隐患**随白名单一起消失。

### 4.6 新增错误码

```java
SPACE_NOT_FOUND(1032, "工作空间不存在"),
SPACE_NO_PERMISSION(1033, "无权访问该工作空间"),
SPACE_NOT_SELECTED(1034, "请先选择工作空间"),
SPACE_NOT_EMPTY(1035, "空间内仍有资源，无法解散"),
SPACE_MEMBER_EXISTS(1036, "该用户已在空间中"),
SPACE_MEMBER_NOT_FOUND(1037, "成员不存在"),
SPACE_OWNER_CANNOT_QUIT(1038, "创建者不能退出，请先解散空间"),
SPACE_TYPE_INVALID(1039, "空间类型不支持该操作"),
SPACE_PERSONAL_NOT_ALLOWED(1040, "个人空间不支持该操作"),
RESOURCE_SPACE_MISMATCH(1041, "资源不属于当前工作空间"),
```

---

## 5. 接口清单

### 5.1 空间管理（`/api/space`，不参与 `x-space-id` 强制校验）

| 方法 | 路径 | 入参 | 返回 | 权限 |
|---|---|---|---|---|
| GET | `/api/space/list` | — | `SpaceInfoResp[]`（含 `spaceType`、`role`、`ownerUcId`、`memberCount`、`resourceCount`、`isCurrent`） | 登录；**顺带自举个人空间** |
| GET | `/api/space/detail` | `spaceId` | `SpaceDetailResp` | 成员 |
| POST | `/api/space/create` | `name`、`description?` | `SpaceInfoResp` | 登录 |
| POST | `/api/space/rename` | `spaceId`、`name` | `boolean` | OWNER，且必须 TEAM |
| POST | `/api/space/dissolve` | `spaceId` | `boolean` | OWNER + 空间为空 |
| GET | `/api/space/member/list` | `spaceId` | `SpaceMemberResp[]` | 成员 |
| POST | `/api/space/member/add` | `spaceId`、`ucId`、`ucName` | `boolean` | 成员（R8：加入的空间也能加人） |
| POST | `/api/space/member/remove` | `spaceId`、`ucId` | `boolean` | OWNER；不能移除自己 |
| POST | `/api/space/member/leave` | `spaceId` | `boolean` | MEMBER（OWNER 拒绝，见 1038） |
| POST | `/api/space/transfer`（P2） | `spaceId`、`ucId` | `boolean` | OWNER：转让空间 |

`GET /api/space/list` 的返回按 `role` + `ownerUcId` 让前端自然分组：`spaceType=PERSONAL` 一条；`TEAM && ownerUcId == me` 为"我创建的"；其余为"我加入的"。

### 5.2 业务接口的鉴权方式（不改 URL）

| 接口族 | 隔离方式 |
|---|---|
| `GET /api/agent/list`、`/api/skill/list`、`/api/tool/list`、`/api/datasource/list` | `workspace_id IN (0, 当前空间)`（`0` 仅工具会用上） |
| `GET .../published-list`、`/api/agent/card/list`、`/sub/agent/list` | 同上 + `status = PUBLISHED` |
| `POST .../create` | `workspace_id = 当前空间` |
| `GET .../detail/{id}`、`POST .../update|publish|takeOff|delete` | `assertMember(资源.workspaceId)` |
| `POST /api/agent/chat/start`、`/start/sse` | `assertMember(agent.workspaceId)` |
| `GET /api/agent/chat/list`、`/history` | 仍按 `uc_id + agent_code`（会话是私有的），不随空间过滤 |

---

## 6. 前端实现

### 6.1 新增 / 修改文件

| 文件 | 动作 | 说明 |
|---|---|---|
| `src/lib/space.ts` | 新增 | 当前空间 localStorage（`faust.spaceId`）+ `SPACE_ID_HEADER` + 订阅事件 |
| `src/lib/http.ts` | 修改 | `buildHeaders` 注入 `x-space-id` |
| `src/services/agent-center.ts` | 修改 | `startChatSSE` 补 header；删除白名单段 |
| `src/services/space.ts` | 新增 | 纯 API：list/create/rename/dissolve/member 系列 |
| `src/components/space-provider.tsx` | 新增 | `SpaceProvider` + `useSpace()`：拉列表、切换、失效回落 |
| `src/components/space-switcher.tsx` | 新增 | 侧边栏顶部下拉：个人空间 / 我的团队；底部「空间管理」 |
| `src/pages/spaces/index.tsx` | 新增 | 空间管理页（复用 `EntityCard` / `CardGrid` / `Dialog`） |
| `src/pages/spaces/member-dialog.tsx` | 新增 | 成员列表 + 添加 + 移除 |
| `src/pages/spaces/space-form-dialog.tsx` | 新增 | 新建 / 重命名 |
| `src/App.tsx` | 修改 | 新增 `/spaces` 路由；页面内容用 `key={spaceId}` 包一层 |
| `src/components/layout/app-layout.tsx` | 修改 | NAV 增加「空间管理」；侧边栏顶部挂 `SpaceSwitcher` |
| 四个管理页 | 修改 | 空态文案带上空间名；`PageHeader` 副标题显示当前空间 |

### 6.2 空间状态与请求头

```ts
// src/lib/space.ts
export const SPACE_ID_HEADER = 'x-space-id';
const SPACE_ID_KEY = 'faust.spaceId';

export function readSpaceId(): number | null {
  const raw = Number(window.localStorage.getItem(SPACE_ID_KEY));
  return Number.isFinite(raw) && raw > 0 ? raw : null;
}

export function saveSpaceId(id: number) {
  window.localStorage.setItem(SPACE_ID_KEY, String(id));
  window.dispatchEvent(new CustomEvent('faust:space-changed'));
}

export function subscribeSpace(listener: () => void) {
  const handler = () => listener();
  window.addEventListener('faust:space-changed', handler);
  window.addEventListener('storage', handler);
  return () => {
    window.removeEventListener('faust:space-changed', handler);
    window.removeEventListener('storage', handler);
  };
}
```

```ts
// src/lib/http.ts
function buildHeaders(extra?: HeadersInit): Headers {
  const headers = new Headers(extra);
  headers.set('x-requested-with', 'XMLHttpRequest');
  headers.set(USER_ID_HEADER, String(getUcId()));
  const spaceId = readSpaceId();
  if (spaceId) headers.set(SPACE_ID_HEADER, String(spaceId));
  return headers;
}
```

```ts
// services/agent-center.ts —— SSE 是手写 fetch，必须单独补
headers: {
  ...,
  [USER_ID_HEADER]: String(readUser().ucId),
  ...(readSpaceId() ? { [SPACE_ID_HEADER]: String(readSpaceId()) } : {})
}
```

### 6.3 切换空间如何让页面刷新

`http.ts` 注入 header 后，页面不会自动重取（query 没变）。用 **key 重挂载**，比给每个页面塞 `spaceId` 依赖更省事，也天然清空详情/表单状态：

```tsx
// App.tsx
function SpaceBoundary({ children }: { children: React.ReactNode }) {
  const { spaceId } = useSpace();
  return <React.Fragment key={spaceId ?? 'none'}>{children}</React.Fragment>;
}
// 每个 Route 的 element 外层包一层 <SpaceBoundary>
```

`SpaceProvider` 逻辑：
1. 挂载时 `GET /api/space/list`；
2. `localStorage.spaceId` 不在返回列表里（被解散 / 换了用户）→ 回落到个人空间并 `saveSpaceId`；
3. 切换 → `saveSpaceId` → 内部 state 更新 → `key` 变化 → 页面重挂载、列表回到第一页。

聊天页额外处理：正在流式输出时切换空间 → 先 `abort()` 再切，避免"上一空间的消息写进新空间视图"。

### 6.4 空间切换器（侧边栏顶部）

```
┌──────────────────────────┐
│ [F] Faust            ⌄   │  ← 团队名/个人名 + 类型徽标（个人 / 团队）
├──────────────────────────┤
│ 个人空间        ✓ 当前   │
│ 美租·商品组    团队 3 人  │
│ 数据平台组     团队 8 人  │
├──────────────────────────┤
│ ⊕ 新建空间               │
│ ⚙ 空间管理               │
└──────────────────────────┘
```

### 6.5 空间管理页（`/spaces`）

- 顶部：`PageHeader`「空间管理」+ 描述 + 「新建空间」按钮。
- 个人空间：单独一张卡（不可删、不可改名、不可加人），标注「仅自己可见」。
- **我创建的团队空间**：`CardGrid` 卡片，展示名称、成员数、四项资源计数（Agent / 工具 / 技能 / 数据源）、创建时间；操作：`进入`（切为当前空间）、`重命名`、`成员`、`⋯`（解散）。
- **我加入的团队空间**：名称、所有者、成员数；操作：`进入`、`成员`（含加人）、`退出空间`。
- 空态：我创建的 → "还没有团队空间，创建一个和同事协作"；我加入的 → "还没有加入任何团队空间"。
- 解散确认：destructive 弹窗，展示 `该空间还有 3 个 Agent、1 个数据源…`；非空时按钮禁用并提示"请先转移或删除资源"。

---

## 7. 数据迁移

`src/main/resources/db/upgrade-workspace.sql`（按顺序执行）：

```sql
-- 1) 建表（见 §3.1）

-- 2) 系统空间：workspace_id = 0，存 NATIVE 工具
INSERT INTO work_space (id, name, space_type, owner_uc_id, owner_name, description)
VALUES (0, '系统空间', 'TEAM', 0, 'SYSTEM', '内置 NATIVE 工具的归属空间（全局共享）')
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- 3) 为每个历史创建者建个人空间 + 成员行
INSERT INTO work_space (name, space_type, owner_uc_id, owner_name)
SELECT CONCAT('个人空间-', u.uc_id), 'PERSONAL', u.uc_id, u.name
FROM (
    SELECT DISTINCT create_by AS uc_id, NULL AS name FROM agent_info   WHERE create_by IS NOT NULL
    UNION SELECT DISTINCT create_by FROM agent_tool                    WHERE create_by IS NOT NULL
    UNION SELECT DISTINCT create_by FROM agent_skill                   WHERE create_by IS NOT NULL
    UNION SELECT DISTINCT create_by FROM data_source_config            WHERE create_by IS NOT NULL
) u
ON DUPLICATE KEY UPDATE work_space.name = work_space.name;   -- 靠 uk_personal_owner 幂等

INSERT IGNORE INTO work_space_user (space_id, uc_id, uc_name, role, create_by)
SELECT w.id, w.owner_uc_id, w.owner_name, 'OWNER', w.owner_uc_id
FROM work_space w WHERE w.space_type = 'PERSONAL';

-- 4) NATIVE 工具 → 系统空间
UPDATE agent_tool SET workspace_id = 0 WHERE UPPER(type) = 'NATIVE';

-- 5) 其余资源按创建人回填到其个人空间
UPDATE agent_info a JOIN work_space w
    ON w.owner_uc_id = a.create_by AND w.space_type = 'PERSONAL'
SET a.workspace_id = w.id
WHERE a.workspace_id = 0;

UPDATE agent_tool t JOIN work_space w
    ON w.owner_uc_id = t.create_by AND w.space_type = 'PERSONAL'
SET t.workspace_id = w.id
WHERE t.workspace_id = 0 AND UPPER(t.type) <> 'NATIVE';

UPDATE agent_skill s JOIN work_space w
    ON w.owner_uc_id = s.create_by AND w.space_type = 'PERSONAL'
SET s.workspace_id = w.id WHERE s.workspace_id = 0;

UPDATE data_source_config d JOIN work_space w
    ON w.owner_uc_id = d.create_by AND w.space_type = 'PERSONAL'
SET d.workspace_id = w.id WHERE d.workspace_id = 0;

-- 6) 兜底：create_by 为空的历史数据 → 系统空间
UPDATE agent_info           SET workspace_id = 0 WHERE workspace_id = 0;
UPDATE agent_tool           SET workspace_id = 0 WHERE workspace_id = 0;
UPDATE agent_skill          SET workspace_id = 0 WHERE workspace_id = 0;
UPDATE data_source_config   SET workspace_id = 0 WHERE workspace_id = 0;

-- 7) 校验：不应再有 0 号以外的 NULL/0（工具除外）
SELECT 'agent_info', COUNT(*) FROM agent_info WHERE workspace_id = 0
UNION ALL SELECT 'agent_skill', COUNT(*) FROM agent_skill WHERE workspace_id = 0
UNION ALL SELECT 'data_source_config', COUNT(*) FROM data_source_config WHERE workspace_id = 0;

-- 8) 确认无回归后再删
-- DROP TABLE agent_whitelist;
```

**行为变化要提前告知**：原来靠白名单"指定可见"的 Agent，迁移后归到**创建者的个人空间** → 其他用户看不到。若想保留团队可见，需要创建者把 Agent 放到团队空间（Phase 2 的"转移"能力，或迁移期临时手改 `workspace_id`）。

> 迁移脚本里 `UPDATE ... JOIN work_space` 依赖 `create_by` 有值；工程里新建资源都会写 `create_by`，历史脏数据走第 6 步兜底到系统空间（0），再由管理员手工分配。

---

## 8. 遗漏点与设计合理性检查

1. **跨空间引用（必须做）**：`agent_tool_rel`、`agent_skill_rel` 只是两张关系表。若不校验，A 空间的 Agent 可以绑定 B 空间的工具（进而用 B 的数据源执行 SQL）。绑定时统一 `assertSameSpace`，并要求 `tool.workspaceId == 0` 的 NATIVE 工具放行。
2. **`connectionTest` 也要鉴权**：`POST /api/datasource/connection/test` 目前可以传任意 url/user/pwd 探测，改成按 `id` 复用已存配置 + `assertMember`，避免变成 SSRF/内网探测入口。
3. **编码唯一性范围（D2）**：若将来要求"空间内可重名"，改造清单 = `uk` 加 `workspace_id` + `getAgentByCode(code, spaceId)` + `AgentFactory`/`AgentExecutor` 签名透传 `spaceId` + `/chat/:agentCode` 路由带 space 头。工作量约 0.5 人日，但不做也不影响隔离。
4. **`use_num` 与统计口径**：空间切换后"共 N 个"按空间统计，全局热度需要另设入口（当前页面无此需求）。
5. **成员被移除后其资源怎么办**：资源属于空间而非个人，保留；但 `create_by` 是已退出成员。建议卡片/详情显示"创建人"，并允许 OWNER 接管（改动只在 UI 展示，不加 ACL）。
6. **OWNER 无法退出**：离职/交接场景需要"转让空间"。Phase 1 明确拒绝并提示，Phase 2 补 `POST /api/space/transfer`。
7. **成员可以加人（R8）的风险**：任何成员都能把外部 UCID 拉进空间 → 数据扩散。建议二选一：① 保持需求原样；② 在 `work_space` 加 `allow_member_invite` 开关（默认开），后续可关。建议 Phase 1 按需求实现，但在 UI 上提示"该成员将可查看/编辑空间内全部资源"。
8. **空间内资源共享可编辑（D6）**：与"每个 Agent 只有作者能改"的直觉冲突。建议卡片展示创建人，编辑按钮对非创建者加 tooltip 说明，减少误操作。
9. **附件存储未按空间隔离**：`/file/upload` 走 S3，URL 直出。空间化后建议对象 key 前缀改为 `space/{spaceId}/...`，并在下载时校验空间（Phase 2）。
10. **`conversation_record` 的 `workspace_id` 只是快照**：Agent 换空间后历史会话仍能打开（按 `uc_id`），这是期望行为，不做级联更新。
11. **拦截器兜底语义**：header 缺失 → 个人空间；header 有值但非成员 → 直接报错。**不要**"非成员就静默回落到个人空间"，否则前端拿着过期 spaceId 会写进自己的空间，属于数据错位。
12. **DB 名不一致（存量问题）**：`schema.sql` 用 `faust_agent_center`，`application.yml` 默认连 `faust`。迁移脚本执行前先统一，否则"建了表但服务连不到"。
13. **逻辑删除缺失**：`agent_info` 等表没有 `is_deleted`，`removeById` 是物理删除。解散空间因此必须"先清空"，不能级联删（见 D5）。
14. **个人空间不能被解散/退出/加人**：所有空间接口都要挡 `spaceType = PERSONAL`（否则解散个人空间后用户永久没有落点）。
15. **切换用户（右上角 UCID）**：`faust.spaceId` 必须一起失效并重新拉列表，否则 A 用户的空间 id 会被 B 用户带上去 → 直接 1033。`SpaceProvider` 订阅 `faust:user-changed` 处理。
16. **列表查询的 `IN (0, spaceId)` 只对工具生效**：Agent/Skill/DataSource 不要带 0，否则系统空间里的脏数据会到处冒出来。

---

## 9. 待确认决策

| # | 决策 | 推荐 | 影响 |
|---|---|---|---|
| 1 | 团队空间内"成员可加人"是否保留 | 按需求保留，但 UI 明确提示风险 | 影响 §4.3 的 `member/add` 权限 |
| 2 | 解散空间时若仍有资源 | **禁止**，先转移/删除（D5） | 影响 §5.1 与空间管理页交互 |
| 3 | `agent_code` 是否改为空间内唯一 | **不改**，保持全局唯一（D2） | 改了要动运行时装载链路 |
| 4 | NATIVE 工具是否全局共享 | **是**，`workspace_id = 0`（D3） | 否则新空间无法使用内置工具 |

---

## 10. 分阶段实施计划

| 阶段 | 前端 | 后端 | 产出 / 验收 |
|---|---|---|---|
| **P0 数据库**（0.5d） | — | `schema.sql` 追加两表、四张业务表加列；`upgrade-workspace.sql` | 本地库执行后 `SHOW CREATE TABLE` 校验；生成列唯一约束生效（重复插 PERSONAL 报 1062） |
| **P1 空间基座**（1d） | — | `WorkSpace*` 实体/仓库/服务/控制器、`SpaceContext`、`SpaceInterceptor`、`SpaceGuard`、错误码 | Swagger/curl 跑通：list（自动建个人空间）、create、rename、member add/remove/leave、dissolve |
| **P2 资源隔离**（1.5d） | — | 四张表 `workspaceId` 落库；各 Service 列表过滤 + 单资源鉴权；绑定关系同空间校验；白名单下线 | curl 造两个空间交叉访问：A 空间 agent 列表看不到 B；用 B 的 id 调详情返回 1033 |
| **P3 前端空间能力**（1.5d） | `lib/space.ts`、`services/space.ts`、`SpaceProvider`、`SpaceSwitcher`、`pages/spaces`、`App.tsx`、`http.ts`、SSE header | — | 切换空间后四个列表即时刷新；空间管理页 CRUD 全通；解散非空空间被拦住 |
| **P4 收尾**（1d） | 删白名单弹窗、空态/文案、README | 删白名单代码、补 `docs`、更新 `schema.sql` | `npx tsc --noEmit` + `mvn -o compile` 零错误；§11 清单逐条过 |

合计约 **5.5 人日**（前后端可并行，P0 必须先完成）。

---

## 11. 验收清单

**隔离**
- [ ] A 空间的 Agent / 工具 / 技能 / 数据源，在 B 空间列表里不可见
- [ ] 用 B 空间的资源 id 直接调 detail/update/delete → 1033
- [ ] `x-space-id` 传一个未加入的空间 → 1033（不回落）
- [ ] 不传 `x-space-id` → 落到个人空间
- [ ] Agent 绑定其它空间工具 → 1041；绑定 NATIVE → 成功

**空间**
- [ ] 首次进入自动生成个人空间，刷新不重复生成（并发也不重复）
- [ ] 个人空间不可改名/解散/加人；OWNER 不能退出团队空间
- [ ] 非 OWNER 调 rename/remove/dissolve → 1033
- [ ] 解散非空空间 → 1035
- [ ] 成员可加人、可退出

**前端**
- [ ] 切换空间 → 四个列表回到第一页且数据正确；聊天页不串数据
- [ ] 切换用户 → 空间失效回落个人空间，不报 1033
- [ ] 空间管理页三个分组正确（个人 / 我创建的 / 我加入的）
- [ ] 白名单入口、代码、接口全部消失（`grep -ri whitelist src` 为空）

**构建**
- [ ] `npx tsc --noEmit` 零错误
- [ ] `mvn -o compile` 通过

---

## 12. 实现状态（2026-11-11）

已按 §10 的 P0~P3 完成，`mvn -o compile` 与 `npx tsc --noEmit` 均通过。与上文计划的差异如下：

| 项 | 计划 | 实际 | 原因 |
|---|---|---|---|
| `SpaceDetailResp` | 独立 DTO | 复用 `SpaceInfoResp`（含四类资源计数） | 字段完全一致，少一个类 |
| 资源转移 | Phase 2 再补 | 未实现 | 解散非空空间已被拦截，暂不影响主流程 |
| `POST /api/space/transfer`（转让） | Phase 2 | 未实现 | OWNER 退出仍会被拒绝（1038） |
| `conversation_record.workspace_id` | 建议加 | 未加 | 会话隔离仍按 `uc_id`，加列属统计诉求，避免半成品 |
| `/api/datasource/connection/test` | 加空间校验 | 未加 | 该接口用于「保存前试连」，请求体只有 url/账号，没有 id；已保持登录态校验 |
| 白名单表 | 迁移脚本 `DROP` | 脚本中注释保留 | 便于回滚验证，确认无回归后再执行 |

**上线顺序**：先执行 `db/upgrade-workspace.sql`（建表 + 加列 + 回填），再重启后端；否则业务查询会因为缺 `workspace_id` 列报错。
