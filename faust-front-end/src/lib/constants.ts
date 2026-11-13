// 智能体中心：常量与枚举（迁移自 bop present/constants/agentCenter.js）

export const DICT_TYPE = {
  CLASSIFICATION: 'classification',
  AGENT_TAGS: 'agent_tags',
  TOOL_TAGS: 'tool_tags'
} as const;

export const SKILL_ITEM_TYPES = [
  { value: 'CONTENT', label: 'CONTENT' },
  { value: 'SCRIPT', label: 'SCRIPT' },
  { value: 'REFERENCE', label: 'REFERENCE' }
];

export const SKILL_ITEM_TYPE = {
  CONTENT: 'CONTENT',
  SCRIPT: 'SCRIPT',
  REFERENCE: 'REFERENCE'
} as const;

export const SKILL_TYPE_LABEL: Record<string, string> = {
  CONTENT: '文档',
  SCRIPT: '脚本/代码',
  REFERENCE: '引用/参考'
};

export const CARD_COLORS = ['#6d5efc', '#0ea5e9', '#10b981', '#f59e0b', '#ef4444'];

export const TOOL_TYPES = [
  { label: 'HTTP', value: 'HTTP' },
  { label: 'DB', value: 'DB' },
  { label: 'SUB_AGENT', value: 'SUB_AGENT' }
];

export const TOOL_TYPE_FILTERS = [
  { label: 'HTTP', value: 'HTTP' },
  { label: 'DB', value: 'DB' },
  { label: 'SUB_AGENT', value: 'SUB_AGENT' },
  { label: 'NATIVE', value: 'NATIVE' }
];

export const TOOL_TYPE = {
  HTTP: 'HTTP',
  DB: 'DB',
  SUB_AGENT: 'SUB_AGENT',
  NATIVE: 'NATIVE'
} as const;

export const DB_TYPES = [
  { label: 'MySQL', value: 'MYSQL' },
  { label: 'PostgreSQL', value: 'POSTGRESQL' },
  { label: 'Oracle', value: 'ORACLE' },
  { label: 'SQL Server', value: 'SQLSERVER' },
  { label: 'ClickHouse', value: 'CLICKHOUSE' },
  { label: '达梦 DM', value: 'DM' },
  { label: '人大金仓 Kingbase', value: 'KINGBASE' },
  { label: '其他', value: 'OTHER' }
];

export const STATUS_OPTIONS = [
  { label: '已发布', value: 'PUBLISHED' },
  { label: '未发布', value: 'UNPUBLISHED' },
  { label: '已下架', value: 'OFFLINE' }
];

/** Agent 状态与后端 AgentStatusEnum 对齐：UNPUBLISHED / PUBLISHED / DRAFT */
export const AGENT_STATUS_OPTIONS = [
  { label: '已发布', value: 'PUBLISHED' },
  { label: '未发布', value: 'UNPUBLISHED' },
  { label: '草稿', value: 'DRAFT' }
];

/** 技能状态与后端 SkillStatusEnum 对齐：只有 DRAFT / PUBLISHED / UNPUBLISHED */
export const SKILL_STATUS_OPTIONS = [
  { label: '草稿', value: 'DRAFT' },
  { label: '已发布', value: 'PUBLISHED' },
  { label: '未发布', value: 'UNPUBLISHED' }
];

export const SKILL_STATUS = {
  DRAFT: 'DRAFT',
  PUBLISHED: 'PUBLISHED',
  UNPUBLISHED: 'UNPUBLISHED'
} as const;

export const STATUS_MAP: Record<string, { text: string; tone: 'success' | 'muted' | 'danger' | 'info' }> = {
  PUBLISHED: { text: '已发布', tone: 'success' },
  UNPUBLISHED: { text: '未发布', tone: 'muted' },
  DRAFT: { text: '草稿', tone: 'info' },
  OFFLINE: { text: '已下架', tone: 'danger' }
};

// 对话页：文件上传限制
export const ALLOWED_EXT = ['jpg', 'jpeg', 'png', 'pdf', 'xls', 'xlsx'];
export const ALLOWED_MIME = [
  'image/jpeg',
  'image/png',
  'application/pdf',
  'application/vnd.ms-excel',
  'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  'application/msexcel',
  'application/x-msexcel',
  'application/x-excel',
  'application/octet-stream'
];
export const MAX_FILES = 4;
export const MAX_FILE_SIZE = 200 * 1024 * 1024;
