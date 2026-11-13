-- -------------------------------------------------------
-- Agent 全局环境变量：升级脚本（存量库执行，可重复执行）
--
-- 背景：会话级环境变量仍存放在 conversation_record.env_var；
--       agent_global_env_config 存放「某个 Agent 在某用户下」的默认变量，
--       新建该 Agent 的会话时带入，之后可在会话内单独修改。
--
-- 变更：维度由 user 改为 agent + user
--   1) 新增 agent_code 列；
--   2) 唯一键 uk_uc_id -> uk_agent_code_uc_id(agent_code, uc_id)。
-- -------------------------------------------------------
USE `faust_agent_center`;

-- 表不存在则直接按新结构创建
CREATE TABLE IF NOT EXISTS `agent_global_env_config`
(
    `id`          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `agent_code`  VARCHAR(100) NOT NULL COMMENT '所属 Agent 编码',
    `uc_id`       BIGINT   NOT NULL COMMENT '所属用户 UCID',
    `env_var`     JSON              DEFAULT NULL COMMENT '全局环境变量（key-value）',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_agent_code_uc_id` (`agent_code`, `uc_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='Agent 全局环境变量（Agent + 用户维度）';

-- 1) 补 agent_code 列（已存在则跳过）
SET @has_column := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'faust_agent_center'
      AND TABLE_NAME = 'agent_global_env_config'
      AND COLUMN_NAME = 'agent_code'
);
SET @ddl := IF(@has_column = 0,
    'ALTER TABLE `agent_global_env_config` ADD COLUMN `agent_code` VARCHAR(100) NOT NULL DEFAULT '''' COMMENT ''所属 Agent 编码'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 去掉临时默认值，与 schema.sql 对齐
ALTER TABLE `agent_global_env_config`
    MODIFY COLUMN `agent_code` VARCHAR(100) NOT NULL COMMENT '所属 Agent 编码';

-- 2) 删除旧的用户级唯一键 uk_uc_id（存在则删）
SET @has_old_index := (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = 'faust_agent_center'
      AND TABLE_NAME = 'agent_global_env_config'
      AND INDEX_NAME = 'uk_uc_id'
);
SET @ddl := IF(@has_old_index > 0,
    'ALTER TABLE `agent_global_env_config` DROP INDEX `uk_uc_id`',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3) 建立新唯一键 (agent_code, uc_id)（存在则跳过）
SET @has_new_index := (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = 'faust_agent_center'
      AND TABLE_NAME = 'agent_global_env_config'
      AND INDEX_NAME = 'uk_agent_code_uc_id'
);
SET @ddl := IF(@has_new_index = 0,
    'ALTER TABLE `agent_global_env_config` ADD UNIQUE KEY `uk_agent_code_uc_id` (`agent_code`, `uc_id`)',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 说明：升级前已有的行 agent_code 为空串，不会命中任何具体 Agent，等同失效。
-- 如需清理旧数据，取消下面一行注释后执行：
-- DELETE FROM `agent_global_env_config` WHERE `agent_code` = '';
