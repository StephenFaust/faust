/**
 * Agent Center 服务层：只做「请求 + 解包」，不做字段重命名 / 类型转换。
 *
 * 路径对齐 faust-back-end 的 Controller：
 *   /api/agent/*        Agent、对话
 *   /api/skill/*        技能
 *   /api/tool/*         工具
 *   /api/datasource/*   数据源
 *   /api/dict/{type}    字典
 *   /file/upload        文件上传
 */
import { get, post } from '@/lib/http';
import { readUser, USER_ID_HEADER } from '@/lib/session';
import { readSpaceId, SPACE_ID_HEADER } from '@/lib/space';

// ---------------- 通用分页 ----------------
export interface PageResult<T> {
  records: T[];
  total: number;
  current: number;
  size: number;
  pages?: number;
}

export interface PageQuery {
  current?: number;
  size?: number;
  [key: string]: unknown;
}

// ---------------- Agent ----------------
export interface AgentCard {
  agentId: number;
  agentCode: string;
  name: string;
  description?: string;
  tags?: string[];
  classification?: string;
  version?: string;
  useNum?: number;
  createTime?: string;
}

export interface AgentRow extends AgentCard {
  status?: string;
  statusDesc?: string;
  canAsSubAgent?: boolean;
  author?: string;
  updateTime?: string;
}

export interface SkillItem {
  itemType: string;
  content: string;
  fileName?: string;
  sortOrder?: number;
}

/** Agent 详情里回显的已绑定技能（后端 SkillSimpleResp，不含文件） */
export interface SkillSimple {
  skillId: number;
  skillCode: string;
  name: string;
  description?: string;
}

/** 技能分页列表项（后端 SkillPageInfoResp） */
export interface SkillRow extends SkillSimple {
  keyWords?: string;
  status?: string;
  itemCount?: number;
  createTime?: string;
}

/** 技能详情（后端 SkillDetailResp，含全部文件） */
export interface SkillDetail extends SkillRow {
  updateTime?: string;
  skillItemList?: SkillItem[];
}

export interface AgentModelConfig {
  baseUrl?: string;
  modelName?: string;
  maxTokens?: number;
  temperature?: number;
  topP?: number;
  timeout?: number;
}

export interface AgentDetail extends Omit<AgentRow, 'useNum'> {
  /** 详情接口把 useNum 以字符串下发（列表接口是数字），此处兼容两者 */
  useNum?: string | number;
  systemPrompt?: string;
  modelConfig?: AgentModelConfig;
  /** 只返回技能基础信息，文件需走 /api/skill/detail/{skillId} */
  skillList?: SkillSimple[];
  toolList?: Array<{ toolId: number; toolCode?: string; name?: string; type?: string }>;
}

export function fetchAgentCards(params?: PageQuery) {
  return get<PageResult<AgentCard>>('/api/agent/card/list', params);
}

export function fetchSubAgentList(params?: PageQuery) {
  return get<PageResult<AgentCard>>('/api/agent/sub/agent/list', params);
}

export function fetchAgentList(params?: PageQuery) {
  return get<PageResult<AgentRow>>('/api/agent/list', params);
}

export function fetchAgentDetail(agentCode: string) {
  return get<AgentDetail>(`/api/agent/detail/${encodeURIComponent(agentCode)}`);
}

export function fetchAgentBase(agentCode: string) {
  return get<AgentRow>(`/api/agent/base/${encodeURIComponent(agentCode)}`);
}

export function createAgent(body: Record<string, unknown>) {
  return post<AgentDetail>('/api/agent/create', body);
}

export function updateAgent(body: Record<string, unknown>) {
  return post<boolean>('/api/agent/update', body);
}

export function publishAgent(agentId: number) {
  return post<boolean>('/api/agent/publish', { agentId });
}

export function takeOffAgent(agentId: number) {
  return post<boolean>('/api/agent/takeOff', { agentId });
}

export function deleteAgent(agentId: number) {
  return post<boolean>('/api/agent/delete', { agentId });
}

// ---------------- 对话 ----------------
export interface ChatSession {
  chatId: string;
  title: string;
  chatTime?: string;
}

export interface ChatHistoryMessage {
  id: number;
  role: string;
  message: string;
  fileInfoRespList?: Array<{ fileName: string; fileType?: string; url: string }>;
}

export interface ChatHistory {
  chatId: string;
  ucId: number;
  chatMessageList: ChatHistoryMessage[];
}

export function fetchChatList(params: { agentCode: string; current?: number; pageSize?: number }) {
  return get<PageResult<ChatSession>>('/api/agent/chat/list', params);
}

export function fetchChatHistory(params: {
  chatId: string;
  agentCode: string;
  lastMessageId?: number;
  pageSize?: number;
}) {
  return get<ChatHistory>('/api/agent/chat/history', params);
}

export function fetchChatEnvVar(params: { chatId: string }) {
  return get<Record<string, string>>('/api/agent/chat/env/var', params);
}

// ---------------- 全局环境变量（Agent + 用户维度） ----------------
/** 某 Agent 在当前用户下的默认环境变量：新建会话时带入 conversation_record.env_var */
export function fetchGlobalEnvVar(agentCode: string) {
  return get<Record<string, string>>('/api/agent/global/env', { agentCode });
}

/** 全量保存某 Agent 在当前用户下的环境变量，传空对象表示清空 */
export function saveGlobalEnvVar(agentCode: string, envVar: Record<string, string>) {
  return post<boolean>('/api/agent/global/env/save', { agentCode, envVar });
}

export function startChat(body: Record<string, unknown>) {
  return post<{ chatId: string }>('/api/agent/chat/start', body);
}

/**
 * SSE 流式对话：直接返回原始 Response，由 `lib/sse.ts` 逐帧解析。
 * 不做 JSON 解包，也不能走 `post()` 的解包逻辑，因此这里单独注入身份头。
 */
export function startChatSSE(body: Record<string, unknown>, signal?: AbortSignal) {
  const spaceId = readSpaceId();
  return fetch('/api/agent/chat/start/sse', {
    method: 'POST',
    credentials: 'include',
    headers: {
      Accept: 'text/event-stream',
      'Content-Type': 'application/json; charset=utf-8',
      'x-requested-with': 'XMLHttpRequest',
      [USER_ID_HEADER]: String(readUser().ucId),
      ...(spaceId ? { [SPACE_ID_HEADER]: String(spaceId) } : {})
    },
    body: JSON.stringify(body ?? {}),
    signal
  });
}

// ---------------- 技能 ----------------
export function fetchSkillList(params?: PageQuery) {
  return get<PageResult<SkillRow>>('/api/skill/list', params);
}

export function fetchSkillPublishedList(params?: PageQuery) {
  return get<PageResult<SkillRow>>('/api/skill/published-list', params);
}

export function fetchSkillDetail(skillId: number) {
  return get<SkillDetail>(`/api/skill/detail/${skillId}`);
}

export function createSkill(body: Record<string, unknown>) {
  return post<{ skillId: number; skillCode?: string; name?: string }>('/api/skill/create', body);
}

export function updateSkill(body: Record<string, unknown>) {
  return post<boolean>('/api/skill/update', body);
}

export function publishSkill(skillId: number) {
  return post<boolean>('/api/skill/publish', { skillId });
}

export function takeOffSkill(skillId: number) {
  return post<boolean>('/api/skill/takeOff', { skillId });
}

export function deleteSkill(skillId: number) {
  return post<boolean>('/api/skill/delete', { skillId });
}

// ---------------- 工具 ----------------
export interface ToolDataSourceInfo {
  configId: number;
  dbName: string;
  dbType: string;
  url: string;
}

export interface ToolSubAgentInfo {
  agentId: number;
  name: string;
  agentCode: string;
  version?: string;
  desc?: string;
}

export interface ToolRow {
  toolId: number;
  toolCode: string;
  name: string;
  type: string;
  description?: string;
  tags?: string[];
  version?: string;
  status?: string;
  statusDesc?: string;
  dataSourceConfigId?: number;
  subAgentId?: number;
  subAgentInfoResp?: ToolSubAgentInfo;
  createTime?: string;
  updateTime?: string;
}

export interface ToolDetail extends ToolRow {
  isLocalTool?: boolean;
  schemaJson?: string;
  /** 详情返回的是关联数据源对象（列表接口不带该字段） */
  dataSourceConfig?: ToolDataSourceInfo;
  nativeToolsListResp?: Array<{ toolName: string; toolDesc: string }>;
}

export function fetchToolList(params?: PageQuery) {
  return get<PageResult<ToolRow>>('/api/tool/list', params);
}

export function fetchToolPublishedList(params?: PageQuery) {
  return get<PageResult<ToolRow>>('/api/tool/published-list', params);
}

export function fetchToolDetail(toolId: number) {
  return get<ToolDetail>(`/api/tool/detail/${toolId}`);
}

export function createTool(body: Record<string, unknown>) {
  return post<ToolRow>('/api/tool/create', body);
}

export function updateTool(body: Record<string, unknown>) {
  return post<boolean>('/api/tool/update', body);
}

export function publishTool(toolId: number) {
  return post<boolean>('/api/tool/publish', { toolId });
}

export function takeOffTool(toolId: number) {
  return post<boolean>('/api/tool/takeOff', { toolId });
}

export function deleteTool(toolId: number) {
  return post<boolean>('/api/tool/delete', { toolId });
}

// ---------------- 数据源 ----------------
export interface DataSourceRow {
  id: number;
  dbType: string;
  dbName: string;
  url: string;
  user: string;
  createTime?: string;
  updateTime?: string;
}

export interface DataSourceDetail extends DataSourceRow {
  createBy?: number;
  updateBy?: number;
  /** 详情接口不下发密码（编辑时留空表示保持原值） */
}

export function fetchDataSourceList(params?: PageQuery) {
  return get<PageResult<DataSourceRow>>('/api/datasource/list', params);
}

export function fetchDataSourceDetail(id: number) {
  return get<DataSourceDetail>(`/api/datasource/detail/${id}`);
}

export function createDataSource(body: Record<string, unknown>) {
  return post<{ id: number }>('/api/datasource/create', body);
}

export function updateDataSource(body: Record<string, unknown>) {
  return post<boolean>('/api/datasource/update', body);
}

export function deleteDataSource(id: number) {
  return post<boolean>('/api/datasource/delete', { id });
}

export async function testDataSource(body: Record<string, unknown>) {
  try {
    await post<boolean>('/api/datasource/connection/test', body);
    return { ok: true as const };
  } catch (error) {
    const reason = error instanceof Error ? error.message : '连接测试失败';
    return { ok: false as const, reason };
  }
}

// ---------------- 字典 ----------------
export interface DictItem {
  id?: number;
  dictType?: string;
  dictCode: string;
  dictLabel: string;
  sortOrder?: number;
}

export async function fetchDict(dictType: string) {
  const data = await get<DictItem[]>(`/api/dict/${dictType}`);
  return Array.isArray(data) ? data : [];
}

// ---------------- 文件上传 ----------------
export async function uploadFile(file: File) {
  const form = new FormData();
  form.append('file', file);
  return post<{ fileName: string; url: string }>('/file/upload', form);
}
