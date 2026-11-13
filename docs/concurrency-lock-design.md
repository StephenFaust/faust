# 同一工作空间内的并发操作分析与 Redisson 分布式锁方案

> 范围：`faust-back-end` 中 Agent / 工具 / 技能 / 数据源 / 空间 五类资源的写路径。
> 前置变化：引入工作空间后，同一份资源从「一人独享」变成「一空间多人共享」，写冲突从偶发变成常态。

---

## 1. 结论先行

当前代码里**没有一处做写互斥**，所有"读—判断—写"都是 check-then-act。同空间两人（或同一人两个标签页）同时操作时，有 6 个场景会直接破坏数据或产生运行时故障：

| # | 场景 | 后果 | 现有代码 |
|---|---|---|---|
| 1 | 两人同时编辑同一 Agent | 后提交者整行覆盖，前者修改静默丢失 | `AgentService#updateAgent` 只有"必须升版本"的输入校验，不是 CAS |
| 2 | 两人同时编辑 Agent 的工具/技能关联 | 全量替换交错，最终关联是两人交集/并集的混合体 | `unbindAll*` → 逐条 `bind` |
| 3 | 工具下架 与 Agent 绑定同时发生 | 工具已下架，却被绑到 Agent 上，对话时 `TOOL_NOT_FOUND` | 两侧都是"先查状态再写" |
| 4 | Agent 删除 与 发布同时发生 | 已发布的 Agent 被物理删掉 | `deleteAgent` 查状态后 `removeById` |
| 5 | 数据源删除 与 工具创建同时发生 | 工具指向已物理删除的数据源，DB 工具调用失败 | `delete` 查引用，`create` 查存在 |
| 6 | 空间解散 与 空间内新建资源同时发生 | 资源挂在已解散空间，谁都访问不到 | `dissolve` 统计为 0 后删空间 |

> 严重度：1、3、5 是"数据静默错误"，最难排查；4 是"不可恢复"；2、6 是"脏数据需人工清理"。

---

## 2. 并发来源

| 来源 | 说明 | 是否本次要治 |
|---|---|---|
| 空间成员 | 同一团队空间内多个成员同时维护同一份资源（新引入） | ✅ 主场景 |
| 同一人多标签页 / 重复点击 | 前端按钮未禁用或网络重试 | ✅ 顺带覆盖 |
| 定时/后台任务 | 目前无定时任务，`increaseUseNum` 是原子 SQL | ❌ 不用管 |
| 对话运行时 | 长事务，**绝不能持锁**；用快照 + 缓存失效处理 | ✅ 但不能用锁 |

---

## 3. 场景清单（按资源）

### 3.1 Agent

| 编号 | 并发对 | 关键代码 | 结果 |
|---|---|---|---|
| A1 | `update` × `update` | `AgentService.java:94-107` | 丢更新（last-write-wins，且无基线校验） |
| A2 | `update` × `update`（关联） | `AgentService.java:121-133` | 技能/工具关联被"合并"成非预期集合 |
| A3 | `update` × `publish` | `publish` 直接改状态 `:138-148` | 发布出去的可能是别人正在改的中间态 |
| A4 | `publish` × `takeOff` | 两个方法各自 `updateById` | 终态取决于最后一条 UPDATE，与操作顺序无关 |
| A5 | `delete` × `publish` | `:167-176` | TOCTOU：检查时未发布 → 删除时已发布 |
| A6 | `create` × `create`（同 code+version） | `AgentInfoRepositoryImpl#saveAgent` count → save | 落库撞 `uk_agent_code_version`，抛 `DuplicateKeyException` → HTTP 500 |
| A7 | `delete` × 对话启动 | `AgentFactory` 2 小时本地缓存 | 已删 Agent 的历史 cache 仍可对话；`update` 改 version 后旧 key 仍可对话 |
| A8 | `update` × 绑定目标被下架 | 见 3.2 T3 | Agent 绑到未发布/已删工具 |
| A9 | `update` × `update`（模型配置 / 提示词） | `saveOrUpdateConfig`、`updatePrompt` 均"查再插" | 依赖 `uk_agent_id` 兜底，重复插入会 500 |

> A1 的历史特殊点：原先 `ThrowExceptionFunction.isTrue(Objects.equals(old.getVersion(), req.getVersion()))` 只要求"提交的版本号 ≠ 库里版本号"，两个人都从 v1 提交 v2 时**双双通过**——这不是乐观锁，只是强迫用户改版本号。该强制校验已按需求移除（见 §12.6），编辑不再要求 bump version，并发保护改为"锁串行化 + 版本 CAS 兜底"。

### 3.2 工具

| 编号 | 并发对 | 关键代码 | 结果 |
|---|---|---|---|
| T1 | `takeOff` × `bindToolToAgent` | `ToolService.java:124-139`；`AgentToolRelRepositoryImpl#bindToolToAgent` | 工具下架成功 + 绑定成功，形成"下架但被引用"的非法态 |
| T2 | `delete` × `bindToolToAgent` | `ToolService.java:141-165` | 删除成功 + 绑定成功，rel 指向已物理删除的工具，运行时 `TOOL_NOT_FOUND` |
| T3 | `update` × `bind` | `ToolService.java:79-101` | 绑定的是 toolId，工具字段（schema/数据源/子 Agent）被并发改写，运行行为漂移 |
| T4 | `create` × `create` | `AgentToolRepositoryImpl#createTool` count → save | 撞 `uk_tool_code`，异常穿透成 500 |
| T5 | `takeOff` × `takeOff` | 第二次应当报"已下架" | 两次都按内存里的旧状态判断，可能都成功 |
| T6 | `delete` × `AgentService.deleteAgent`（级联解绑） | 两边都 `removeBatchByIds` | 幂等，无实际危害（可接受） |

### 3.3 技能

| 编号 | 并发对 | 关键代码 | 结果 |
|---|---|---|---|
| S1 | `takeOff` × `bindSkillToAgent` | `SkillService.java:99-112`；`AgentSkillRelRepositoryImpl#bindSkillToAgent` | 同 T1 |
| S2 | `delete` × `bind` | `SkillService.java:114-128` | 同 T2 |
| S3 | `update`（条目全量替换）× `bind` | `AgentSkillRepositoryImpl#updateSkill` 删旧条目→插新条目 | Agent 绑定成功，但运行时读到条目为空 → `SKILL_NOT_FOUND`（1004），对话直接失败 |
| S4 | `update` × `delete` | `updateSkill` 先 `getById` 再 `updateById` | 删除后 `updateById` 影响 0 行但**不校验行数** → 静默成功，随后 `saveItems` 把条目插回一个已不存在的 `skill_id`，形成孤儿条目 |
| S5 | `update`（条目）× `delete`（条目） | `deleteSkill` 删技能 + 物理删条目 | 并发下可能残留孤儿条目（skill 已删，item 还在） |
| S6 | `create` × `create`（同 skillCode） | `validateSkillCode` count → save | 撞 `uk_skill_code` → 500 |

### 3.4 数据源

| 编号 | 并发对 | 关键代码 | 结果 |
|---|---|---|---|
| D1 | `delete` × `ToolService.create/update` | `DataSourceConfigService.java:84-98`；`ToolService#assertRefsInSpace` | 工具引用了刚被物理删除的数据源，运行期取不到连接信息 |
| D2 | `update` × 正在执行的 DB 工具 | 每次执行新建连接 | 密码/URL 热切换，运行中的会话可能连接失败（可接受，提示即可） |
| D3 | `delete` × `delete` | 第二次 `getById` 拿不到 → 报 NOT_FOUND | 无误伤，可接受 |

### 3.5 空间

| 编号 | 并发对 | 关键代码 | 结果 |
|---|---|---|---|
| W1 | `dissolve` × 资源 `create` | `SpaceService.java:212-219` | 资源写进已删空间（成员关系已删，等价于数据丢失） |
| W2 | `removeMember` × 该成员的编辑请求 | `SpaceGuard#assertMember` 只在方法开头校验 | 被移除后仍能完成本次写入（窗口极短，建议接受） |
| W3 | `ensurePersonalSpace` × 并发首登 | 唯一索引 + `DuplicateKeyException` 兜底 | **已处理，是正面样例** |

### 3.6 跨资源不变量（当前都不成立）

- **I1**：`PUBLISHED` 的 Agent，其 rel 只能指向 `PUBLISHED` 且未删除的工具/技能。
- **I2**：`PUBLISHED` 的资源不可被删除。
- **I3**：同一资源的连续编辑必须基于同一基线（后提交者要知道自己覆盖了别人）。
- **I4**：空间解散后，空间内资源数恒为 0 且不再增长。
- **I5**：被工具引用的数据源不可删除。
- **I6**：`takeOff`/`delete` 与 `bind` 不能交错产生"下架但仍被引用"。

---

## 4. 为什么不能只靠数据库

先明确哪些**可以**不用锁（这一层必须先做，锁只兜剩下那部分）：

| 手段 | 适用 | 现状 |
|---|---|---|
| 唯一索引 | `agent_code+version`、`tool_code`、`skill_code`、`agent_id`、`(agent_id, tool_id/skill_id)` | 已有 ✅，但异常没转成业务码 ❌ |
| 条件更新（CAS） | 状态流转：`UPDATE ... SET status=? WHERE id=? AND status=?` | 未使用 ❌ |
| 原子表达式 | `increaseUseNum` | 已使用 ✅ |
| 行数校验 | `updateById` 影响行数 = 0 时抛"已被他人修改" | 未使用 ❌ |

**为什么条件更新还不足够？** 以 T1 为例，如果 `takeOff` 用 CAS 改状态、再查 rel 表：

```sql
-- 事务隔离级别默认 REPEATABLE READ
UPDATE agent_tool SET status='UNPUBLISHED' WHERE id=? AND status='PUBLISHED';
SELECT COUNT(*) FROM agent_tool_rel WHERE tool_id=?;   -- 快照读，看不到并发事务刚提交的 rel
```

`bind` 侧在同一时刻插入 rel 并提交，`takeOff` 的 `COUNT(*)` 由于快照读看不到这一行，仍然判定"无关联"并提交 → I6 依然被破坏。要修就得改成 `SELECT ... FOR UPDATE` 或把隔离级别降到 READ COMMITTED，这会把复杂度转移到 DB 语义上，且跨表场景仍然做不到（`Agent.update` 要同时动 5 张表）。

**所以分工是**：单表、单行的状态流转用 CAS；**跨表 / 跨行的复合写用分布式锁**。

---

## 5. Redisson 方案

### 5.1 依赖与连接（无需新增）

`pom.xml` 已引入 `redisson-spring-boot-starter`（4.7.0），`CheckPointSaverConfig` 已注入 `RedissonClient`，`application.yml` 已有 `spring.data.redis.*`。锁直接用现成 Bean，不新增中间件。

### 5.2 Key 规范

```
faust:lock:agent:{agentId}
faust:lock:tool:{toolId}
faust:lock:skill:{skillId}
faust:lock:datasource:{dataSourceId}
faust:lock:space:{spaceId}
```

- 全部带前缀 `faust:lock:`，便于 `SCAN` 排查与 Redis 监控面板分组；
- **不加 workspaceId**：资源 id 全局唯一，加空间维度只会让同一资源出现两把锁；
- 不用 hash tag：单实例/哨兵无 slot 概念；即便将来上 Cluster，`RedissonMultiLock` 也是逐个加锁，不要求同 slot。

### 5.3 加锁形态（落地时调整为编程式）

> ⚠️ **落地差异**：本节最初设计为「注解 + AOP」，实际实现改成了编程式
> `DistributedLockTemplate`，原因与等价性见 **§12.1**。下面的注解方案保留作为设计参考。

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DistributedLock {

    /** key 规范："前缀:#SpEL"，如 "agent:#req.agentId"；表达式返回集合时展开成多把锁 */
    String[] keys();

    /** 最长等待时间，超时直接失败（不排队死等） */
    long waitMillis() default 2000;

    /** 租约时间。-1 表示启用看门狗自动续期（推荐） */
    long leaseMillis() default -1;
}
```

```java
@Slf4j
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)   // 关键：必须比 @Transactional 更外层
@RequiredArgsConstructor
public class DistributedLockAspect {

    private static final String PREFIX = "faust:lock:";
    private final RedissonClient redisson;
    private final SpelExpressionParser parser = new SpelExpressionParser();
    private final DefaultParameterNameDiscoverer discoverer = new DefaultParameterNameDiscoverer();

    @Around("@annotation(lock)")
    public Object around(ProceedingJoinPoint pjp, DistributedLock lock) throws Throwable {
        List<RLock> locks = resolveKeys(pjp, lock).stream()
            .distinct()
            .sorted()                               // 固定加锁顺序，避免 A/B 与 B/A 死锁
            .map(k -> redisson.getLock(PREFIX + k))
            .toList();
        if (locks.isEmpty()) {
            return pjp.proceed();
        }
        RLock multi = locks.size() == 1
            ? locks.get(0)
            : new RedissonMultiLock(locks.toArray(new RLock[0]));

        long start = System.nanoTime();
        boolean acquired = false;
        try {
            acquired = multi.tryLock(lock.waitMillis(), lock.leaseMillis(), TimeUnit.MILLISECONDS);
            if (!acquired) {
                throw new AgentBusinessException(ErrorMessageEnum.RESOURCE_LOCKED.getErrorCode(),
                    ErrorMessageEnum.RESOURCE_LOCKED.getErrorMsg());
            }
            return pjp.proceed();                   // 事务在这里面提交
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AgentBusinessException(ErrorMessageEnum.RESOURCE_LOCKED.getErrorCode(), "锁等待被中断");
        } finally {
            if (acquired) {
                releaseAfterCommit(multi);          // 见 5.4
                LOGGER.info("lock {} held {}ms", keys, (System.nanoTime() - start) / 1_000_000);
            }
        }
    }
}
```

**为什么要支持集合 key**：Agent 编辑时 `skillIdList` / `toolIdList` 是一组 id，需要同时持有这些资源锁，才能和它们的下架/删除互斥。

Key 解析（支持单值、集合、数组）：

```java
private List<String> resolveKeys(ProceedingJoinPoint pjp, DistributedLock lock) {
    MethodSignature signature = (MethodSignature) pjp.getSignature();
    EvaluationContext ctx = new StandardEvaluationContext();
    String[] names = discoverer.getParameterNames(signature.getMethod());
    Object[] args = pjp.getArgs();
    for (int i = 0; i < args.length; i++) {
        ctx.setVariable(names[i], args[i]);
    }
    List<String> keys = new ArrayList<>();
    for (String spec : lock.keys()) {
        // spec 形如 "tool:#req.toolIdList"：冒号前是 key 前缀，冒号后是 SpEL
        int idx = spec.indexOf(':');
        String prefix = idx > 0 ? spec.substring(0, idx + 1) : "";
        String expr = idx > 0 ? spec.substring(idx + 1) : spec;
        Object value = parser.parseExpression(expr).getValue(ctx);
        if (value instanceof Collection<?> collection) {
            collection.forEach(v -> addKey(keys, prefix, v));
        } else {
            addKey(keys, prefix, value);
        }
    }
    return keys;
}

private void addKey(List<String> keys, String prefix, Object value) {
    if (value != null && !String.valueOf(value).isBlank()) {
        keys.add(prefix + value);
    }
}
```

### 5.4 锁与事务的顺序（最容易踩的坑）

```
正确：Lock ──> [BEGIN ──> 业务写 ──> COMMIT] ──> Unlock
错误：BEGIN ──> Lock ──> 业务写 ──> Unlock ──> COMMIT     ← 提交前就放锁，等于没锁
```

- 切面 `@Order(HIGHEST_PRECEDENCE)`，Spring 事务默认 `LOWEST_PRECEDENCE`，所以切面在事务外层，`pjp.proceed()` 返回时事务已提交，`finally` 里释放是安全的；
- 更稳妥的写法是**注册事务同步**，兼容"切面恰好位于事务内部"的情况：

```java
private void releaseAfterCommit(RLock lock) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        });
        return;
    }
    if (lock.isHeldByCurrentThread()) {
        lock.unlock();
    }
}
```

- 必须用 `isHeldByCurrentThread()` 再 `unlock()`：看门狗续期失败或 lease 过期后，`unlock` 会抛 `IllegalMonitorStateException`；
- **自调用陷阱**：`this.update()` 不走代理，注解失效。绑定侧要在 `AgentService` 里通过注入的代理对象调用，或直接写编程式锁。

### 5.5 参数取值

| 操作 | waitMillis | leaseMillis | 说明 |
|---|---|---|---|
| 编辑（Agent/工具/技能/数据源） | 2000 | -1（看门狗） | 技能条目可能批量插入，时长不可控 |
| 发布 / 下架 / 删除 | 800 | -1 | 用户预期"点一下就有反馈"，宁可快速失败 |
| 绑定 / 解绑（集合锁） | 500 | -1 | 涉及多把锁，等待放大的代价高 |
| 空间解散 / 资源创建 | 2000 | -1 | 低频 |

- `leaseMillis = -1` 交给看门狗（默认 30s 续期到 10s），避免"DB 慢 → 锁提前过期 → 双写"；
- 显式 leaseTime 会关闭看门狗，只在确定耗时极短的场景使用；
- 等待超时**不要重试**：重试会把"并发"放大成"排队风暴"，且用户已经看到失败提示，重试应由用户决定。

### 5.6 失败语义与前端配合

新增错误码（承接现有 1032~1042）：

```java
RESOURCE_LOCKED(1043, "该资源正在被其他成员操作，请稍后重试"),
LOCK_UNAVAILABLE(1044, "并发控制服务暂不可用，请稍后重试"),
CONCURRENT_MODIFIED(1045, "内容已被他人修改，请刷新后重试");
```

- 统一走现有 `ApiResponse`，HTTP 200 + 业务码，前端沿用现有 toast；
- 前端在收到 1043/1045 时：提示 + 自动 `reload()` 详情/列表，避免用户重复提交同一份旧数据；
- 提交按钮 `loading` 期间禁用（部分页面已有），降低同人重复点击。

### 5.7 Redis 不可用时怎么办

Redis 已是既有强依赖（会话 checkpoint），挂掉时对话本身就不可用。策略：

- **写操作 fail-closed**：拿不到锁 → `LOCK_UNAVAILABLE`，绝不"降级为无锁直写"；
- **读操作完全不受影响**：列表/详情/对话读取路径不加锁；
- 提供开关 `agent.center.lock.enabled`（默认 `true`），本地调试/单测可关，避免本地无 Redis 时全部接口 500。

### 5.8 集群与主从切换的现实风险

Redisson 的 `RLock` 基于 Redis 主从，**master 挂掉且锁尚未同步到 slave 时，可能出现两个实例同时持有锁**。因此：

- 锁只用来"把窗口从秒级压到毫秒级"，不作为唯一正确性保证；
- 关键状态流转仍然用 CAS 兜底（`WHERE status = 'PUBLISHED'`、`WHERE version = ?`），
  这样即使锁失效，最坏结果是"操作失败"，不会产生脏数据。这就是第 4 节的分层价值。

---

## 6. 场景 → 手段 映射（落地清单）

| 场景 | 首选手段 | 兜底 | 备注 |
|---|---|---|---|
| A1 双人编辑 Agent | `agent:{id}` 锁 | `WHERE version=?` CAS | 锁解决"同时"，CAS 解决"先后" |
| A2 关联全量替换 | `agent:{id}` + `tool/skill:{id}` 集合锁 | `uk_agent_tool/skill` | 集合锁上限建议 50，超出改为"只锁 agent" |
| A3 update × publish | `agent:{id}` 锁 | CAS 状态 | 发布/下架都走同一把锁 |
| A4 publish × takeOff | `agent:{id}` 锁 | CAS | 同上 |
| A5 delete × publish | `agent:{id}` 锁 | `DELETE ... WHERE status<>'PUBLISHED'` | 双保险 |
| A6 同 code 建 Agent | 唯一索引 | 捕获 `DuplicateKeyException` → 1007 | 不需要锁 |
| A7 删/改 vs 对话 | 无锁；**发布/下架后失效 `AgentFactory` 缓存** | 运行时校验 + 友好报错 | 对话绝不能持锁 |
| T1 takeOff × bind | `tool:{id}` 锁（两侧都加） | 绑定用 `INSERT ... SELECT WHERE status='PUBLISHED'` | 见 7.2 |
| T2 delete × bind | `tool:{id}` 锁 | 同上 | |
| T3 update × bind | `tool:{id}` 锁 | — | |
| T4 同 code 建工具 | 唯一索引 | 捕获 DupKey → 1009 | |
| S1~S2 | `skill:{id}` 锁 | 同 T1 | |
| S3 update(条目) × bind | `skill:{id}` 锁 | 运行时校验条目非空 | 顺带修 I1 |
| S4 update × delete | `skill:{id}` 锁 | `updateById` 校验行数 | |
| D1 ds delete × tool create | `datasource:{id}` 锁（两侧） | 建工具时 `SELECT ... FOR UPDATE` 校验存在 | |
| W1 解散 × 建资源 | `space:{id}` 锁（`create` 也加共享/互斥锁） | 解散后写入前二次校验空间存在 | |
| W2 移除成员 × 编辑 | 不加锁 | 接受短窗口 | 成本远大于收益 |
| 计数 | `increaseUseNum` 原子 SQL | — | 已 OK |

---

## 7. 关键代码改造点

### 7.1 Agent 编辑：锁 + 版本 CAS

```java
@DistributedLock(keys = {"agent:#req.agentId", "tool:#req.toolIdList", "skill:#req.skillIdList"},
                 waitMillis = 2000)
@Transactional(rollbackFor = Exception.class)
public boolean updateAgent(AgentUpdateReq req) { ... }
```

```java
// AgentInfoRepositoryImpl#updateAgent 增加行数校验
LambdaUpdateWrapper<AgentInfo> wrapper = Wrappers.lambdaUpdate();
wrapper.eq(AgentInfo::getId, agentInfo.getId())
       .eq(AgentInfo::getVersion, agentInfo.getVersion());   // 传旧版本号进来
if (baseMapper.update(agentInfo, wrapper) == 0) {
    throw new AgentBusinessException(ErrorMessageEnum.CONCURRENT_MODIFIED...);
}
```

### 7.2 绑定：把 check-then-insert 变成原子插入

```xml
<!-- AgentToolRelMapper.xml -->
<insert id="bindIfPublished">
    INSERT INTO agent_tool_rel (agent_id, tool_id)
    SELECT #{agentId}, #{toolId}
    FROM agent_tool
    WHERE id = #{toolId}
      AND status = 'PUBLISHED'
      AND workspace_id IN (#{spaceId}, 0)
</insert>
```

影响行数 0 → 抛 `TOOL_NOT_PUBLISHED` / `RESOURCE_SPACE_MISMATCH`。这样即使锁失效，也不会产生"绑定未发布工具"的脏数据。

### 7.3 下架：先 CAS 改状态，再看引用

```java
@DistributedLock(keys = {"tool:#req.toolId"}, waitMillis = 800)
@Transactional(rollbackFor = Exception.class)
public boolean takeOff(ToolPublishOrTaskOffReq req) {
    int rows = agentToolService.casStatus(req.getToolId(), PUBLISHED, UNPUBLISHED);
    ThrowExceptionFunction.isTrue(rows == 0)
        .throwMessage(ErrorMessageEnum.TOOL_ALREADY_TAKEOFF);   // 需新增：1046「工具已下架或状态已变更」
    ThrowExceptionFunction.isNotEmpty(agentToolRelService.listByToolId(req.getToolId()))
        .throwMessage(ErrorMessageEnum.REL_TOOL_AGENT_NOT_OPERATE);   // 抛异常 → 事务回滚，状态复原
    return true;
}
```

### 7.4 缓存失效（A7）

`AgentFactory` 的 `LoadingCache` 目前是私有字段、无失效入口，导致"下架后仍可对话"。建议暴露：

```java
public void evict(String agentCode, String version) {
    cache.invalidate(String.join("@", agentCode, version));
    cache.invalidate(String.join("@", agentCode, version, "subAgent"));
}
```

在 `publish` / `takeOff` / `delete` / `update` 事务提交后调用（同样用 `afterCommit` 时机），避免"提交前失效 → 并发请求又把旧配置读回缓存"。

### 7.5 数据库侧需要补的唯一键 / 索引

| 表 | 建议 | 原因 |
|---|---|---|
| `agent_info` | 已有 `uk(agent_code, version)` | ✅ |
| `agent_tool` / `agent_skill` | 已有 `uk(tool_code)` / `uk(skill_code)` | ✅ 跨空间全局唯一，保持 |
| `agent_tool_rel` / `agent_skill_rel` | 已有 `uk(agent_id, tool_id/skill_id)` | ✅ |
| `agent_model_config` / `agent_system_prompt` | 已有 `uk(agent_id)` | ✅ |
| `data_source_config` | 建议 `uk(workspace_id, db_name)` | 防同空间重名数据源，顺带解决并发重复创建 |

---

## 8. 分阶段实施

> **状态：P0 ~ P4 已全部落地**（2026-11-11），实现细节与偏差见 §12。

| 阶段 | 内容 | 产出 | 预估 | 状态 |
|---|---|---|---|---|
| P0 | 引入 `@DistributedLock` + 切面 + `LockKeys` + 错误码 + 开关；`DuplicateKeyException` 统一转业务码 | 基础设施 | 0.5d | ✅（形态改为编程式锁模板） |
| P1 | Agent 四个写操作（update/publish/takeOff/delete）加锁 + 版本 CAS | 消灭 A1~A5 | 0.5d | ✅ |
| P2 | 工具/技能/数据源的 update/publish/takeOff/delete 加锁；绑定改原子插入 | 消灭 T1~T3、S1~S4、D1 | 1d | ✅ |
| P3 | 空间解散加锁；资源 create 加空间锁 | 消灭 W1 | 0.5d | ✅ |
| P4 | `AgentFactory.evict` + 事务提交后失效；前端 1043/1045 提示与自动刷新 | 消灭 A7 与体验问题 | 0.5d | ✅ 后端已完成；前端提示沿用现有 toast，未做自动 reload |

**总计约 3 人日**（不含联调）。

---

## 9. 验收：并发测试

用 JUnit + 双线程 + `CountDownLatch` 对齐起跑线，断言最终态而不是返回值：

| 用例 | 断言 |
|---|---|
| 两线程同时 `updateAgent`（都保持原 version） | 锁串行化：两个请求都成功，最终态 = 后提交者的完整内容，无交叉写入；**不再返回 1045**（版本号不再作为变更令牌） |
| 两线程同时 `updateAgent` 且期间版本被第三方改动 | 后到者 CAS 命中 0 行 → 1045 |
| `takeOff` 与 `bindToolToAgent` 同时发起（各 50 轮） | 每一轮结束后：`status=UNPUBLISHED` 时 rel 必须为空；rel 存在时 `status=PUBLISHED` |
| `deleteAgent` 与 `publish` 同时发起 | 不允许出现"行已删除且曾为 PUBLISHED"的组合 |
| `deleteDataSource` 与 `createTool`（引用它） | 不允许工具存活而数据源已删 |
| `dissolveSpace` 与 `createAgent` | 不允许 Agent 的 workspace_id 指向不存在的空间 |
| Redis 停机 | 写接口返回 1044，读接口正常，DB 无脏数据 |

补充压测：同一 agent 100 QPS 反复 update，观察锁等待 P99 与失败率（预期 P99 < 50ms，失败率 0 在一个请求内完成）。

---

## 10. 明确不做的事

- **不给读接口加锁**：列表/详情/对话读取全部无锁，否则会把并发压力搬到 Redis。
- **不在对话执行期间持锁**：一次对话几十秒到几分钟，持锁等于把资源锁死。
- **不为"移除成员 vs 编辑"加锁**：窗口只有一次请求时长，成本远大于收益。
- **不做全局大锁（按 spaceId 串行化所有写）**：会牺牲空间内并行性，只在"解散 vs 创建"这一对冲突上用空间锁。
- **不引入 Redis 之外的协调组件**（ZooKeeper/etcd）：Redis 已是既有依赖，多一套组件只增加运维负担。

---

## 11. 顺带发现的相邻问题（不属于并发，但会放大并发后果）

> 本节 2026-11-11 重写：按「怎么触发 → 最坏后果 → 现状 → 怎么改」重新组织。
> 其中第 5 条原判断（物理删除留下唯一索引"墓碑"）经核对**不成立**，已替换为复核后确认的真问题。

### 11.0 一览

| # | 问题 | 触发条件 | 最坏后果 | 本次锁改造后 |
|---|---|---|---|---|
| 1 | `getAgentByCode` 用 `getOne()` 只按 code 过滤 | 同 code 存在 2 行（创建时手填已有 code + 新 version） | `TooManyResultsException` → 500，且创建事务整体回滚 | 未动，**用户可触发** |
| 2 | 多处 `updateById` / `removeById` 不校验影响行数 | 并发删除后再更新 | 影响 0 行仍返回成功，前端提示成功但数据没变 | Agent 四条写路径已修，其余 6 处未动 |
| 3 | "count 再 insert"，唯一索引只在最后兜底 | 并发创建同编码 / 并发邀请同一人 | `DuplicateKeyException` 被兜成 code=1「系统内部异常」而非业务码 | 未动 |
| 4 | `dissolve` 内 `countResources` 是快照读 | 与创建资源并发 | 把刚有资源的空间删掉，资源变成孤儿 | 已用 `space:{id}` 锁 + 锁内二次统计封堵，缺 DB 层兜底 |
| 5 | 物理删除 + 编码全局唯一 | 删除后用同编码重建 | 会话按 `agent_code` 归属，被新 Agent 继承 → 跨空间串号 | 未动 |

### 11.1 `getAgentByCode` 的多行语义（P0，线上可触发）

- **位置**：`AgentInfoRepositoryImpl#getAgentByCode`（`eq(agent_code)` + `getOne()`，无 version、无 limit）。
- **为什么会有多行**：`AgentCreateReq` 带 `agentCode` + `version`；前端创建态 Code 输入框可编辑；`saveAgent` 只校验 `(code, version)` 组合，**没有拦截"code 已存在"**。用户录入一个已存在的 Code、换个 version 提交，就落成第 2 行。
- **两条爆炸路径**：
  1. `AgentService#createAgent` 最后一行用 `getAgentByCode` 回查返回值 → 抛 `TooManyResultsException` → `@Transactional` 整体回滚。用户看到"创建失败"，但 Code 已被占用，本次填的模型配置、提示词、技能/工具绑定全部丢失。
  2. 详情 / 编辑 / 对话入口都按 `agentCode` 查详情（`AgentService` L281、L294），同样 500，该 Code 从此不可用。
- **顺带一处**：编辑时 `version` 也可改，改成同 Code 另一行的 version 会直接撞 `uk_agent_code_version`（属第 3 条）。
- **语义矛盾**：`uk(agent_code, version)` 的设计意图是"一版一行"，但 `updateAgent` 是在**同一行**上改 version（`BeanUtils.copyProperties(req, agentInfo)` 把新 version 写回同一 id）。结果：永远只有一行、版本历史丢失、删掉就再也回不到旧版本。
- **两个方案**：
  - **A（推荐，单行语义）**：唯一键收紧为 `uk_agent_code(agent_code)`，创建时同 Code 直接报「Agent 编码已存在」；编辑仍改同一行改 version；`getAgentByCode` 保持 `getOne()`。改动小、语义自洽，代价是没有多版本并存。成本 ~0.5h（含 DDL + 存量数据查重）。
  - **B（多版本语义）**：创建允许同 Code 新 version；编辑改为"插入新版本行 + 复制 config/prompt/rel"；`getAgentByCode` 改为 `orderByDesc(create_time).last(limit 1)` 或取 PUBLISHED 版本。功能强但影响编辑链路、缓存 key、会话续聊三处。成本 ~2d。

### 11.2 通用 `updateById` 不校验影响行数（P1）

本工程没有 `@TableLogic`（`agent_info` 等表也没有 `is_deleted` 列），`removeById` 是真 DELETE，所以"更新一个已经被删掉的行"会静默影响 0 行并返回 true。本次只修了 Agent 的四条写路径（版本 CAS / 状态条件更新 / 带条件的删除），下面这些**仍是裸 `updateById`**：

| 位置 | 语句 | 静默成功的场景 |
|---|---|---|
| `ToolService#doUpdate` → `agentToolService.updateById` | 更新工具 | 工具被并发删除后保存，返回成功 |
| `AgentToolRepositoryImpl#publishTool` | 发布工具（NATIVE 注册路径 `NativeToolRegister` 调用） | 工具刚被删，发布"成功" |
| `AgentSkillRepositoryImpl#updateSkill` | 更新技能 | 技能被并发删除后保存 |
| `DataSourceConfigService#update` / `#delete` | 数据源更新 / 删除 | 已被删除，仍返回成功 |
| `AgentSystemPromptRepositoryImpl#updatePrompt` | 提示词 | Agent 被删后写 0 行（不重建孤儿，但用户以为保存成功） |
| `AgentModelConfigRepositoryImpl#saveOrUpdateConfig` | 先 `getOne` 再 update/insert | 并发首次保存撞 `uk_agent_id` → 500 |
| `SpaceService#rename` / `#removeMember` / `#leave` | 空间改名、移除成员 | 成员已被另一请求移除，仍返回成功 |

- **后果**：用户侧表现为"点了保存、提示成功、刷新后没变"，比直接报错更难排查；写入端则可能把子表（prompt / config）写得与主表脱节。
- **建议**：在 Repository 层统一收口成 `mustUpdate(rows)` / `mustRemove(rows)`，0 行抛 `CONCURRENT_MODIFIED(1045)`；`saveOrUpdateConfig` 改 `INSERT ... ON DUPLICATE KEY UPDATE` 消除先查后写。成本 ~0.5d（逐个补 + 回归）。

### 11.3 count-then-insert 的异常类型（P1）

- **站点**：`saveAgent`（`uk_agent_code_version`）、`createTool`（`uk_tool_code`）、`saveSkillWithItems`→`validateSkillCode`（`uk_skill_code`）、`addMember`（`uk_space_uc`）。
- **现状**：`GlobalExceptionHandler` 只处理 `AgentBusinessException` 和参数校验异常，`DuplicateKeyException` 落到最后的 `Exception` 分支 → `code=1 / 系统内部异常`，前端 toast 只能说"失败了"，分不清是"编码重复"还是"服务挂了"。
- **已经做对的参照**：`SpaceService#ensurePersonalSpace` 用 `try save catch (DuplicateKeyException)` 后回查复用，是幂等正确写法。
- **建议**：在 `GlobalExceptionHandler` 增加 `DuplicateKeyException` 分支，按异常信息里的索引名映射业务码，一次覆盖所有现存与未来新增的唯一索引：

| 索引名 | 返回码 |
|---|---|
| `uk_agent_code_version` | 1007 `AGENT_VERSION_DUPLICATE` |
| `uk_tool_code` | 1009 `TOOL_CODE_DUPLICATE` |
| `uk_skill_code` | 1027 `SKILL_CODE_DUPLICATE` |
| `uk_space_uc` | 1036 `SPACE_MEMBER_EXISTS` |
| `uk_personal_owner` | 1032（个人空间已存在，可复用） |
| 其他 | 1007 兜底「记录已存在」 |

成本 ~0.5h。注意解析索引名依赖 MySQL 的 `Duplicate entry ... for key 'xxx.uk_yyy'` 文案，取不到索引名时走兜底码。

### 11.4 `dissolve` 的快照读（P2，已封堵、留残余）

- **已做**：`dissolve` 持 `space:{id}`（wait 2000ms），锁内 `countResources`；四类资源 `create` 持同一把锁，并在锁内 `assertSpaceExists` 二次校验。只要创建入口都加锁，W1 不会发生。
- **残余三点**：
  1. **靠纪律不靠机制**——将来新加一个创建入口（比如"从模板导入 Agent"）忘加 space 锁，问题立刻复活；
  2. Redis 主从切换可能丢锁（§5.8），锁失效时快照读问题原样复现；
  3. `countResources` 仍是 `SELECT COUNT(*)`，本身没有锁语义。
- **建议加固（可选，1h）**：`doDissolve` 先 `SELECT id FROM work_space WHERE id = ? FOR UPDATE` 锁住空间行，创建侧在 `assertSpaceExists` 时用 `FOR SHARE` 读同一行。这样"解散空空间"与"往空间里放资源"在数据库层互斥，不依赖调用方记得加锁。

### 11.5 物理删除 + 编码全局唯一（P1，原判断已更正）

- **更正**：原文档写"删掉 Agent 后同 code + 同 version 永远无法重建（墓碑）"——**不成立**。本工程 `removeById` 是物理 DELETE，行和唯一索引项一起消失，删除后同 `(code, version)` 可以正常重建。
- **真问题 A：编码复用导致会话串号（严重）**。`conversation_record` 只记 `uc_id / agent_code / version`（无 `workspace_id`、无 `agent_id`，`schema.sql` L222-238），会话列表按 `(uc_id, agent_code)` 查（`idx_uc_agent`）。所以：
  1. 我在空间 A 建了 `x` 并聊了几轮 → 删掉 → 在空间 B 又建 `x` → 空间 B 的会话列表里能看到空间 A 的历史；
  2. 更糟的是**别人**在另一个空间建了同 Code：我这边点开旧会话续聊，`agentCode` 解析到的是对方的 Agent（对方的提示词、模型地址与 Key），历史消息却是我的。
- **真问题 B：删除不可逆**。误删无恢复、无审计；`conversation_record` / `message_record_item` 保留但指向不存在的 Agent，详情页与续聊都是死路。
- **建议（按成本升序，可叠加）**：
  1. `conversation_record` 增加 `workspace_id`、`agent_id`；会话列表按 `(uc_id, workspace_id, agent_id)` 过滤，续聊校验 `agent_id` 一致。这是**安全修复**，建议优先。~0.5d。
  2. Agent 改逻辑删除：加 `is_deleted BIGINT NOT NULL DEFAULT 0`（删除时写时间戳或 id），唯一键改 `(agent_code, version, is_deleted)`，`getAgentByCode` 显式带 `is_deleted = 0`。这样既有软删，又不会被唯一索引挡住重建。~0.5d。
  3. 删除前校验"是否还有会话"，或删除时把会话标记归档。

### 11.6 建议处理顺序

| 顺序 | 事项 | 理由 | 成本 |
|---|---|---|---|
| 1 | 11.1 方案 A：`uk_agent_code` 收紧 + 创建查重 | 用户现在就能踩到，且失败会吞掉整次创建 | 0.5h |
| 2 | 11.3 `DuplicateKeyException` 转业务码 | 一次改动覆盖所有创建接口的错误文案 | 0.5h |
| 3 | 11.5-A `conversation_record` 补 `workspace_id/agent_id` | 跨空间串号属安全口径问题 | 0.5d |
| 4 | 11.2 六处 `updateById` 补行数校验 | 消灭"假成功"，排障成本最高 | 0.5d |
| 5 | 11.4 `FOR UPDATE` 加固 | 结构性兜底，防未来新入口漏锁 | 1h |
| 6 | 11.5-B Agent 逻辑删除 | 与 11.5-A 配套，解决可恢复性 | 0.5d |

---

## 12. 落地实现（2026-11-11）

### 12.1 形态：编程式锁模板，而不是注解 + AOP

§5.3 的注解方案需要 `@Aspect` + `ProceedingJoinPoint`，即 **aspectjweaver**；本工程 `pom.xml` 没有
`spring-boot-starter-aop`，离线构建（`mvn -o`）也无法解析新依赖。因此改成编程式：

```java
return lockTemplate.call(LockKeys.merge(LockKeys.agent(id), LockKeys.tools(toolIds)), 2000, () -> doUpdate(req));
```

与原方案的**语义完全等价**，并且：

- 没有代理顺序问题（注解方案要靠 `@Order(HIGHEST_PRECEDENCE)` 猜事务与切面的相对位置）；
- 没有自调用陷阱（`this.update()` 不走代理导致注解失效）；
- 加锁点就在业务方法首行，一眼可见；后续若引入 AOP，只需把模板调用替换成注解，key 语义不变。

代价：业务方法多一层 `doXxx()` 私有方法（`@Transactional` 留在外层方法上）。

### 12.2 新增文件

| 文件 | 职责 |
|---|---|
| `support/lock/LockKeys.java` | key 规范 `faust:lock:{type}:{id}`，按资源类型提供 `agent/tool/skill/dataSource/space`，`merge()` 合并多组 key |
| `support/lock/DistributedLockTemplate.java` | 加锁执行体：去重 + 字典序排序 + `RedissonMultiLock`、看门狗、事务提交后释放、fail-closed |
| `support/AfterCommit.java` | 事务提交后执行副作用（缓存失效） |
| `config/property/LockProperties.java` | `agent.center.lock.enabled` 开关（默认 `true`） |

### 12.3 改造点对照

| 方法 | 锁 key | 数据库兜底 |
|---|---|---|
| `AgentService#createAgent` | `space:{spaceId}` + 传入的 tool/skill | 锁内 `assertSpaceExists` |
| `AgentService#updateAgent` | `agent:{id}` + 提交/原有 tool/skill | `WHERE id=? AND version=?`（expected 取锁内读到的行版本，0 行 → 1045） |
| `AgentService#publish` / `#takeOff` | `agent:{id}` | `WHERE status <> 目标态`（0 行 → 1047 / 1046） |
| `AgentService#deleteAgent` | `agent:{id}` | `DELETE ... WHERE status <> 'PUBLISHED'` |
| `AgentService`（编辑/发布/下架/删除） | — | 提交后 `AgentCacheInvalidator#invalidate(agentCode)`（代际号 `INCR` + 广播） |
| `ToolService#create` / `#update` | `space:` / `tool:{id}` + `datasource:{id}` | 锁内 `assertSpaceExists` / 引用校验 |
| `ToolService#publish` / `#takeOff` / `#delete` | `tool:{id}` | 状态条件更新 + 删除条件更新 |
| `SkillService#create` / `#update` | `space:` / `skill:{id}` | 锁内 `assertSpaceExists` |
| `SkillService#publish` / `#takeOff` / `#delete` | `skill:{id}` | 状态条件更新 + 删除条件更新（条目删除随之搬到服务层） |
| `DataSourceConfigService#create` / `#update` / `#delete` | `space:` / `datasource:{id}` | — |
| `AgentToolRelRepositoryImpl#bindToolToAgent` | 由 `updateAgent` 持有 `tool:{id}` | `INSERT ... SELECT WHERE status='PUBLISHED' AND workspace 匹配` |
| `AgentSkillRelRepositoryImpl#bindSkillToAgent` | 由 `updateAgent` 持有 `skill:{id}` | 同上（技能无系统空间例外） |
| `SpaceService#dissolve` | `space:{id}` | 锁内重新统计资源数 |

新增错误码：`1043 RESOURCE_LOCKED`、`1044 LOCK_UNAVAILABLE`、`1045 CONCURRENT_MODIFIED`、
`1046 RESOURCE_ALREADY_TAKEOFF`、`1047 STATUS_CHANGED`。

### 12.4 几个实现细节

1. **加锁顺序**：`DistributedLockTemplate` 对 key 做 `distinct().sorted()`，
   所有调用点都走同一套排序，因此「空间锁 + 工具锁」不会与「工具锁 + 空间锁」互相等待。
2. **锁与事务**：业务方法保留 `@Transactional`，锁在方法体内、事务已开启的位置获取，
   释放统一走 `TransactionSynchronization#afterCompletion`，即 **提交/回滚之后才解锁**。
   比「事务在锁内」多占一点锁时间，但绝不会出现「先放锁后提交」。
3. **等待时间**：编辑 2000ms，发布/下架/删除 800ms，空间解散 2000ms；超时立即 1043，不重试。
4. **关联数量上限**：`updateAgent` 关联资源超过 50 个时只锁 Agent 本身，避免一次拿几十把锁。
5. **绑定幂等**：`INSERT ... SELECT` 撞 `uk_agent_tool / uk_agent_skill` 时捕获 `DuplicateKeyException`
   视为「已绑定」直接返回；插入 0 行才回查状态给出 1005/1006/1041 的准确文案。
6. **开关**：`agent.center.lock.enabled=false` 时退化为直接执行（本地无 Redis 调试用），生产必须为 `true`。

### 12.5 本次明确未做（对应 §11，按需求暂缓）

§11 的 5 个相邻问题全部按原样保留，但描述已在 2026-11-11 重写并修正（第 5 条原「物理删除墓碑」判断不成立，已替换为
「编码复用导致会话跨空间串号」）。其中与并发直接相关的部分已被锁 + 条件更新覆盖，但根因仍在：

- 11.1 `getAgentByCode` 多行 → `TooManyResultsException`，**优先级最高，用户可触发**；
- 11.2 六处 `updateById` / `removeById` 未校验行数 → 静默"假成功"；
- 11.3 `DuplicateKeyException` 仍被兜成 `code=1`，不是 1007/1009/1027/1036；
- 11.4 `dissolve` 已加锁，但缺 DB 层 `FOR UPDATE` 兜底，新入口漏锁即复活；
- 11.5 会话表缺 `workspace_id` / `agent_id`，删除后同编码重建会串号。

### 12.6 变更：移除「编辑 Agent 必须升级版本」

2026-11-11 按需求去掉强制升版本校验（原 `AgentService#doUpdateAgent` 中的
`ThrowExceptionFunction.isTrue(Objects.equals(old.getVersion(), req.getVersion()))`），
错误码 `1024 UPDATE_AGENT_NEED_UPDATE_VERSION` 同步删除，**1024 号段留空不复用**。

| 维度 | 变化 |
|---|---|
| `version` 定位 | 从「编辑必改的变更令牌」退回纯描述字段，编辑可保持原值 |
| 请求校验 | `AgentUpdateReq.version` 仍 `@NotBlank`；前端仍校验 `x.y.z` 格式 |
| 并发语义 | 同一 Agent 的编辑由 `agent:{id}` 锁串行化：两个并发编辑**都成功**，后提交覆盖，不再返回 1045 |
| 版本 CAS | 保留为结构性兜底：expected 取锁内读到的行版本，仅在「行被删除 / 版本被绕过锁改写」时命中 0 行 → 1045 |
| 缓存 | key 仍含 version；同版本编辑 key 不变，但失效走代际号 `INCR` + 广播，仍会立即重建 |

> 如果以后要恢复「两人同时编辑，后到者被拦下」的体验，需要引入独立变更令牌
> （`update_time` 参与 CAS，或加 `revision` 列），不能复用 `version`——它已经不再随编辑变化。



