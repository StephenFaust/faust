/**
 * 环境变量（会话级 / 全局）通用工具：
 * 行编辑模型与「key-value」互转、校验规则。
 *
 * 校验口径与后端 GlobalEnvConfigService 保持一致：
 *   key 以字母开头，可含字母 / 数字 / 下划线 / 短横线，最长 100；
 *   value 最长 300；一组最多 50 个。
 */

export interface EnvRow {
  id: string;
  key: string;
  value: string;
}

export const ENV_KEY_MAX_LEN = 100;
export const ENV_VALUE_MAX_LEN = 300;
export const ENV_MAX_SIZE = 50;
export const ENV_KEY_PATTERN = /^[A-Za-z](?:[A-Za-z0-9_-]*[A-Za-z0-9])?$/;

let rowSeq = 0;

export function createEnvRow(key = '', value = ''): EnvRow {
  rowSeq += 1;
  return { id: `env-${Date.now()}-${rowSeq}`, key, value };
}

/** 行列表 → key-value（丢弃空 key，后者覆盖前者） */
export function envListToMap(list: EnvRow[]) {
  const map: Record<string, string> = {};
  list.forEach((item) => {
    const key = String(item.key || '').trim();
    if (!key) return;
    map[key] = item.value ?? '';
  });
  return map;
}

/** key-value → 行列表 */
export function envMapToList(map?: Record<string, string> | null): EnvRow[] {
  if (!map || typeof map !== 'object') return [];
  return Object.keys(map).map((key) => createEnvRow(key, map[key] == null ? '' : String(map[key])));
}

export interface EnvValidation {
  /** rowId → 错误文案 */
  errors: Record<string, string>;
  /** 校验通过的变量（已 trim key、去掉空行） */
  list: EnvRow[];
}

/**
 * 校验行列表，返回逐行错误与可提交的数据
 */
export function validateEnvRows(rows: EnvRow[]): EnvValidation {
  const errors: Record<string, string> = {};
  const filled = rows.filter((row) => row.key.trim());

  if (filled.length > ENV_MAX_SIZE) {
    const last = filled[ENV_MAX_SIZE];
    errors[last.id] = `最多 ${ENV_MAX_SIZE} 个环境变量`;
    return { errors, list: [] };
  }

  filled.forEach((row) => {
    const key = row.key.trim();
    if (key.length > ENV_KEY_MAX_LEN) errors[row.id] = `key 最多 ${ENV_KEY_MAX_LEN} 个字符`;
    else if (!ENV_KEY_PATTERN.test(key)) errors[row.id] = 'key 需以字母开头，可含字母数字下划线短横线';
    else if (row.value.length > ENV_VALUE_MAX_LEN) errors[row.id] = `value 最多 ${ENV_VALUE_MAX_LEN} 个字符`;
  });
  if (Object.keys(errors).length) {
    return { errors, list: [] };
  }

  const keys = filled.map((row) => row.key.trim());
  if (keys.length !== new Set(keys).size) {
    const duplicate = keys.find((key, index) => keys.indexOf(key) !== index) ?? keys[keys.length - 1];
    const target = filled.find((row) => row.key.trim() === duplicate) ?? filled[filled.length - 1];
    errors[target.id] = '环境变量 key 不能重复';
    return { errors, list: [] };
  }

  return { errors, list: filled.map((row) => ({ ...row, key: row.key.trim() })) };
}
