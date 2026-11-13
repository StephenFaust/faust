# 前端代码审查报告（CR）

| 项 | 内容 |
| --- | --- |
| 审查对象 | `faust-front-end`（React 18 + TypeScript + Vite + Tailwind + Radix UI） |
| 审查日期 | 2026-11-11 |
| 审查范围 | `src/` 全部 73 个 `.ts/.tsx` 文件（pages / components / services / lib / 入口与路由） |
| 审查维度 | 正确性与缺陷、安全与权限、可维护性与规范 |
| 审查方式 | 分模块静态审查，逐条代码复核，并与后端 DTO/Service 交叉核对契约；**仅读，未修改任何文件** |
| 结论 | **P0 0 项、P1 10 项、P2 18 项** |

---

## 1. 结论摘要

| 级别 | 数量 | 说明 |
| --- | --- | --- |
| P0 | 0 | 未发现崩溃、越权或 XSS 级缺陷（XSS 面已被 `html:false` + `validateLink` 关闭） |
| P1 | 10 | 集中在 SSE 流式收尾、会话状态与后端语义不一致、表单/加载失败态缺失 |
| P2 | 18 | 交互一致性、可访问性、契约与后端不一致、竞态与竞态兜底 |

**优先处理（Top 5）**

1. 新会话环境变量未就绪就发送，会以"空 Map"覆盖会话配置，Agent 拿不到密钥（§2.1）
2. 环境变量弹窗加载失败仍可保存，一键清空该 Agent 的默认变量（§2.2）
3. SSE 末帧未冲刷 + 收尾 `clear()` 丢弃缓冲，回复尾部会被静默截断（§2.5、§2.8）
4. 累积快照 + 打字机缓冲导致正文重复（§2.6、§2.7）
5. 一次多选上传只保留最后一个文件（§2.9）

---

## 2. P1 高风险项

### 2.1 会话环境变量未就绪就发送，会显式清空会话配置

- 位置：`src/pages/chat/index.tsx:812`、`:822`
- 代码：

```ts
const envMap = envListToMap(envVars);
...
envVar: envMap
```

- 问题：前端**始终**传 `envVar`；而后端只在 `req.getEnvVar() == null` 时才继承「Agent × 用户」的默认变量（`ChatService.java:175`）。当 `envVars` 尚未初始化（`undefined`）时，`envListToMap` 返回 `{}`，语义从"未表态"变成"用户主动清空"。
- 触发条件：
  1. 打开新对话后立刻输入并发送，`prefillGlobalEnv()` 的请求还没返回；
  2. 历史会话的 `fetchChatEnvVar` 失败（`loadHistory` 的 `.catch(() => null)` 会让 `envVars` 保持 `undefined`），此时发送会把该会话原有的变量覆盖成空。
- 影响：新会话落库 `env_var` 为空，Agent 运行缺少密钥/连接参数；已有会话走 `updateConversation` 时传空 Map 会**覆盖原值且不可恢复**。
- 建议：

```ts
// 未初始化 → 不带字段，交给后端继承；用户显式清空才传 {}
const envMap = currentSession?.envVars === undefined ? undefined : envListToMap(envVars);
...
envVar: envMap
```

同时在 `envVars === undefined` 时禁用发送或给出"配置加载中"提示。

- 复核：已核对前端调用点与后端 `ChatService#updateOrSaveConversation` 分支，确认语义冲突。

### 2.2 环境变量弹窗加载失败后仍可保存，等于一键清空

- 位置：`src/pages/agents/global-env-dialog.tsx:43`
- 代码：

```ts
.catch((error) => {
  if (cancelled) return;
  setRows([createEnvRow()]);
  toast.error(`加载环境变量失败：${...}`);
})
```

- 问题：失败分支与"本来就没有配置"在界面上完全一致，都是一行空输入框；`submit` 走全量保存，后端 `saveEnvVar` 收到空 Map 就是清空。
- 触发：`fetchGlobalEnvVar` 失败后用户点「保存」。
- 影响：该 Agent 的默认环境变量被整份抹掉，之后新建的会话全部缺变量，无二次确认。
- 建议：增加 `loadFailed` 状态，失败时禁用保存并提供「重试」；或保存前对"全量覆盖"做二次确认。

### 2.3 编辑页详情加载失败 → 永久骨架屏

- 位置：`src/pages/agents/form-view.tsx:332`（同类：`skill-admin/form-view.tsx:229`、`tool-admin/form-view.tsx:217`、`datasource-admin/form-view.tsx:179`）
- 代码：

```tsx
if (!populated) {
```

- 问题：`populated` 只在 `detail.data` 成功时置 `true`，`detail.error` 从未参与渲染分支。
- 触发：进入编辑页时详情接口 4xx/5xx/超时，或资源已被他人删除。
- 影响：永远停在骨架屏，没有错误文案、没有重试、也没有保存按钮，用户只能刷新猜原因。
- 建议：骨架屏条件改为 `detail.loading`，补 `detail.error` 分支渲染 `EmptyState` + 重试按钮。

### 2.4 数据源编辑只改连接地址/用户名时，可跳过连接测试直接保存

- 位置：`src/pages/datasource-admin/form-view.tsx:87`
- 代码：

```tsx
// 编辑态留空密码 = 保留原值，后端不会重新连接，因此无需再次测试
const needsTest = !(isEdit && !trimmedPwd);
```

- 问题：是否测试只看"密码是否留空"，与 `url/user/dbType` 是否变更无关；后端 `doUpdate` 也不做连通性校验。
- 触发：编辑数据源 → 改连接地址或用户名 → 密码留空 → 保存按钮直接可用。
- 影响：写入一个连不通的数据源，引用它的 DB 工具在运行时才失败，且没有"改过连接信息"的记录可循。
- 建议：以 `detail.data` 为基线比较 `dbType/url/user`，任一变化即 `needsTest = true`。

### 2.5 SSE 流末尾未冲刷 buffer，最后一帧丢失

- 位置：`src/lib/sse.ts:143`、`:174`
- 代码：

```ts
const { value, done } = await reader.read();
if (done) break;
buffer += decoder.decode(value, { stream: true });
```

- 问题：`break` 之后只处理了 `leftoverLine`，**滞留在 `buffer` 里、没有以换行结尾的最后一段**永远不会被解析；`decoder` 也没有做收尾 `decode()` 冲刷。
- 触发：后端写完最后一条 `data:` 后直接关闭连接、缺少结尾 `\n\n`（代理提前断流、连接被截断时常见）。
- 影响：最后一个 `final-assistant` 帧或最后一段正文静默丢失，用户看到答案被截断且无任何错误提示。
- 建议：

```ts
if (done) {
  buffer += decoder.decode();                // 冲刷 UTF-8 残留
  if (buffer.trim()) leftoverLine = leftoverLine ? `${leftoverLine}\n${buffer}` : buffer;
  buffer = '';
}
```

随后再做 `leftoverLine` 的收尾解析。

### 2.6 累积快照 + 打字机缓冲导致正文重复

- 位置：`src/pages/chat/index.tsx:614`
- 代码：

```ts
const committed = committedRef.current.process || '';
const delta = resolveDelta(message as never, data, committed);
```

- 问题：`delta` 用「已渲染的前缀」计算，但结果进入打字机**待输出缓冲**；缓冲中尚未吐出的字符没有被计入基数。
- 触发：后端不发 `chunk`、只发累积的 `message.content`，且 token 到达速度高于打字机输出速度。
- 影响：同一段文本被重复追加，长回答出现大段重复。
- 建议：维护 `receivedRef`（累计收到量）作为 `resolveDelta` 的 `committed` 入参，只有真正 commit 时才更新 `committedRef`；该模式下也可直接 `commitReplace` 快照。

### 2.7 快照比已提交内容短时按 append 处理，产生重复

- 位置：`src/lib/streamReducer.ts:166`
- 代码：

```ts
if (content.length < base.length) return { mode: 'append', text: content };
```

- 问题：走到这里说明 `content` 不以 `base` 为前缀且更短，属于"新快照覆盖"（工具调用后模型重写），却被当成增量整段追加。
- 触发：`chunk` 为空且 `content.length < committed.length`。
- 影响：旧文本 + 新全文叠加，回复彻底错乱且无法自愈。
- 建议：该分支返回 `{ mode: 'replace', text: content }`。**需要对后端确认 content 的语义**（累积快照 or 纯增量）；若确为"截断后继续追加"才保留 append。

### 2.8 收尾时 `clear()` 丢弃未吐出的文本，回退合并会丢字

- 位置：`src/pages/chat/index.tsx:788`
- 代码：

```ts
typewriter.clear();
let merged = finalPartsRef.current.join('\n\n');
```

- 问题：`clear()` 直接丢弃缓冲中尚未 commit 的文本；随后回退读 `sessionsRef.current`，而 ref 只在 render 时赋值，本轮刚 commit 的内容可能还没进去。
- 触发：后端只发增量 `assistant` 帧、没有 `final-assistant` 帧（或 final 帧为空）。
- 影响：答复尾部若干百字符被吞，甚至误判为"本次未返回内容"。
- 建议：收尾用 `typewriter.flush()` 先落盘再清空，并用同步累加的 `receivedProcessRef` 作为合并源。

### 2.9 一次多选上传只保留最后一个文件

- 位置：`src/pages/chat/chat-input.tsx:170`
- 代码：

```ts
accepted.forEach((file) => {
  onFilesChange([...filesRef.current, { id, fileName: file.name, fileType, url: '', uploading: true }]);
```

- 问题：`filesRef.current` 只在 render 时同步，`forEach` 内连续调用读到的都是同一份旧数组，后一次覆盖前一次。
- 触发：一次选择 ≥2 个文件（`multiple` 已开启）。
- 影响：只显示最后一个文件，其余静默丢弃，`MAX_FILES` 计数也随之失真。
- 建议：先算好 `next = [...files, ...accepted.map(...)]` 一次性 `onFilesChange(next)` 再并发上传，回调里按 `id` 合并。

### 2.10 `unwrap` 对空响应体返回 `undefined`，调用方直接解引用会白屏

- 位置：`src/lib/http.ts:78`
- 代码：

```ts
const text = await response.text();
if (!text) return undefined as T;
```

- 问题：类型上承诺 `Promise<T>`，运行时可能给 `undefined`；`space-provider` 直接把结果当数组用。
- 触发：后端返回 200 + 空 body（`ResponseEntity.ok()` 无 body、204/304 变体）。
- 影响：`space-provider.tsx:38` 先 `setSpaces(undefined)`，随后 `list.find` 抛错，`useMemo` 里 `spaces.find` 触发白屏。
- 建议：空 body 抛 `ApiError('空响应')`，或在 `fetchSpaceList/fetchSpaceMembers` 层做 `Array.isArray(x) ? x : []` 兜底（`fetchDict` 已有此写法，可对齐）。

---

## 3. 前后端契约不一致（跨端问题）

这几条单看两端都不算错，但对在一起会出问题，建议优先对齐。

| # | 前端行为 | 后端语义 | 后果 | 建议 |
| --- | --- | --- | --- | --- |
| 1 | 发送时始终传 `envVar` | `null` 才继承默认值，`{}` 视为主动清空 | §2.1 环境变量被清空 | 前端未初始化时不传该字段 |
| 2 | API Key「留空=不修改」，用户输入后清空会退回 false | `updateById` 忽略 `null`，无法真正清空密钥 | 用户以为已轮换/已删除，实际仍是旧值 | 加显式「清空密钥」动作 + 后端支持空串语义 |
| 3 | 工具下架文案写"已关联的 Agent 也会失去该工具" | 仍被引用时后端**拒绝**下架 | 文案承诺了不会发生的结果，失败后像系统异常 | 按实际语义改写；菜单里展示引用数 |
| 4 | 已发布 Agent/工具仍显示「删除」 | 后端必抛 `IS_PUBLISHED_TO_DELETED` | 用户点完只得到失败 toast | `disabled={published}` + "请先下架"提示，与技能页对齐 |
| 5 | 编辑态关联下拉只加载前 50/200 条 | 资源数无上限 | 已绑定但不在候选里的值显示为空 | 改搜索式远程加载；缺失值补临时 option |
| 6 | 表单无长度校验 | DTO 无 `@Size`，唯一约束是 DB 列宽 | 超长 → SQL 异常 → 500，无法定位字段 | 按 `schema.sql` 列宽补 `maxLength` 与校验文案 |

---

## 4. P2 改进项

| # | 位置 | 问题 | 建议 |
| --- | --- | --- | --- |
| 1 | `pages/agents/form-view.tsx:358` | 四步表单无脏数据保护，返回/切空间直接丢弃输入 | 记录初始快照，dirty 时二次确认 |
| 2 | `pages/agents/form-view.tsx:313` | 提交中返回后仍会完成保存并 `setState`，`onSaved` 还会重置列表分页 | 加卸载守卫，保存期间禁用取消/返回 |
| 3 | `pages/skill-admin/form-view.tsx:324` | 动态条目用 `index` 作 key，重排后 DOM 复用错位；`removeItem` 直接 `setErrors({})` 把基础字段错误一并清掉 | 给条目加稳定 id，key 与错误键都用 id |
| 4 | `pages/chat/index.tsx:887` | 乐观消息用 `index` 作 key，前插历史时组件内状态（图片失败态、折叠态）挂错消息 | 创建时生成稳定本地 id |
| 5 | `pages/chat/index.tsx:426` | 历史加载失败把 `messages` 置空，看起来像"对话被清空" | 保留旧消息，内联错误 + 重试 |
| 6 | `pages/chat/index.tsx:770` | 停止生成直接 `pop()` 掉回复气泡，已流出内容消失 | 保留气泡并标注"已停止生成" |
| 7 | `pages/chat/index.tsx:355` | 会话列表 offset 分页 + 本地新增会话 → 「加载更多」跳过边界数据；无 in-flight 守卫，连点重复推进页码 | 后端改游标分页；前端至少加请求锁 |
| 8 | `pages/chat/index.tsx:648` | `final` 分片只与"最后一片"比前缀，混用增量/快照时 push 出重复段 | 任一分片是累积快照则整体覆盖 |
| 9 | `components/space-provider.tsx:101` | 首屏 `spaceId=null` 时先挂载并请求，`load()` 完成后 key 变化整页重挂载 → 每页两次全量请求 + 闪烁 | `loading && !current` 时渲染占位，就绪后再渲染 children |
| 10 | `pages/spaces/member-dialog.tsx:51` | 加载失败沿用上一个空间的成员列表，标题已是新空间 | 打开/切换时先清空，失败给错误态 |
| 11 | `pages/agents/index.tsx:85` | 字典加载失败静默吞掉，分类/标签为必填项却无从选择 | toast + 重试入口，区分"失败"与"无选项" |
| 12 | `pages/skill-admin/index.tsx:190` | 空态不分"筛选无结果"，也没有"清除筛选"出口（agents 页已区分） | 按 `filtersActive` 切换文案，与 agents 页统一 |
| 13 | `pages/datasource-admin/index.tsx:158` | 连接串明文渲染，`jdbc:mysql://user:pass@host` 会连密码一起显示 | 渲染前对 `//user:pass@` 掩码 |
| 14 | `components/descriptions.tsx:49` | 所有字段一律单行 `truncate`，详情页长描述只能悬浮看 | 增加 `multiline` 选项，描述/URL 用 `whitespace-pre-wrap` |
| 15 | `pages/agents/detail-view.tsx:80` | `v${data.version \|\| '—'}` 会拼出 `v—` | 改为 `data.version ? \`v${data.version}\` : '—'` |
| 16 | `pages/spaces/space-form-dialog.tsx:80` | Enter 提交无 in-flight 守卫，连按创建多个同名空间 | `submit` 首行 `if (submitting) return` |
| 17 | `components/ui/field.tsx:25` | label 未与控件关联、错误无 `aria-describedby`，`Select`/`MultiSelect` 出错无描红 | 用 `useId()` 注入 id，错误加 `role="alert"`，描红扩到 `button` |
| 18 | `lib/hooks/use-paged-list.ts:117` | `useAsyncData` 只有序号淘汰，无卸载保护；`deps` 不含 `loader` | effect 内加 `cancelled` 标记，`loader` 用 `useCallback` 纳入依赖 |

---

## 5. 分模块问题分布

| 模块 | P1 | P2 | 主要问题类型 |
| --- | --- | --- | --- |
| `lib/`（sse、http、streamReducer、hooks） | 4 | 3 | 流式收尾丢帧、状态机语义、空响应体、卸载保护 |
| `pages/chat/` | 3 | 4 | 环境变量覆盖、打字机重复、上传合并、key 与分页 |
| `pages/agents/` | 2 | 5 | 加载失败态、脏数据保护、API Key 语义、字典吞错 |
| `pages/tool-admin/` | 0 | 3 | 契约文案、候选截断、删除可用性 |
| `pages/datasource-admin/` | 1 | 2 | 跳过连接测试、连接串明文 |
| `pages/skill-admin/` | 0 | 2 | index key、空态 |
| `pages/spaces/` | 0 | 2 | 成员残留、重复提交 |
| `components/` | 1 | 3 | 整卡无键盘可达、label 关联、首屏双请求 |

---

## 6. 建议修复批次

**批次一（本周，影响功能正确性）**
1. §2.1 环境变量"未初始化不传值"
2. §2.2 环境变量弹窗失败禁用保存
3. §2.5 + §2.8 SSE 收尾冲刷与 `flush()`
4. §2.9 多选上传合并
5. §2.3 编辑页错误态

**批次二（下个迭代）**
6. §2.6 + §2.7 流式文本重复（需与后端确认 content 语义后定方案）
7. §2.4 连接测试判定
8. §2.10 空响应体兜底
9. §3 契约表 1~6 逐条对齐（与后端同批发布）

**批次三（体验与技术债）**
10. §4 全部 P2；其中第 5、6 条（会话列表分页、停止生成）建议与后端游标分页一并规划

---

## 7. 已排除的疑似问题（避免重复排查）

| 疑似问题 | 复核结论 |
| --- | --- |
| Markdown 渲染 XSS（`dangerouslySetInnerHTML`） | 不成立：`markdown-it` 配置 `html:false`，`link_open` 对 http(s) 补 `rel="noopener noreferrer"`，`validateLink` 拦截危险协议 |
| SSE/上传漏注入身份头 | 不成立：全仓 `fetch(` 仅出现在 `services/agent-center.ts` 与 `lib/http.ts` 两处，均注入身份头 |
| 历史文件卡片可被 `javascript:` 链接利用 | 不成立：只对 `/^https?:\/\//` 建链 |
| 详情页回显 API Key / 数据库密码 | 不成立：两端 detail 均不回显该字段 |
| 用户身份存 localStorage、可被改写 | 属本地版既定设计（`lib/session.ts` 已注明）；**若部署为多租户需后端从网关取身份**，不作为前端缺陷 |

---

## 附录：审查覆盖清单

- `services/`：`agent-center.ts`（全部接口定义与 SSE 入口）等
- `lib/`：`sse.ts`、`http.ts`、`markdown.ts`、`streamReducer.ts`、`env.ts`、`format.ts`、`utils.ts`、`session.ts`、`hooks/`（`use-typewriter`、`use-infinite-list`、`use-paged-list`）
- `components/`：`markdown.tsx`、`entity-card.tsx`、`descriptions.tsx`、`space-provider.tsx`、`space-switcher.tsx`、`layout/`、`ui/`（button、input、select、multi-select、dialog、field、dropdown-menu、popover、confirm、spotlight-card 等）
- `pages/`：`agents/`（index、form-view、detail-view、global-env-dialog）、`skill-admin/`、`tool-admin/`、`datasource-admin/`、`spaces/`、`chat/`（index、chat-input、process-panel）
- 入口与路由：`App.tsx`、`main.tsx`、路由定义
