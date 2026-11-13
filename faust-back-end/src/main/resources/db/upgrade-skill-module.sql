-- -------------------------------------------------------
-- 存量数据库升级脚本：技能独立为管理模块
--
-- 适用场景：已按旧结构建过 agent_skill（含 agent_id）的数据库。
-- 全新部署请直接使用 schema.sql，无需执行本脚本。
--
-- 改造内容：
--   1. 技能脱离 Agent 独立存在，去除 agent_skill.agent_id
--   2. 新增 agent_skill_rel 维护 Agent 与技能的多对多关系
--   3. agent_skill 新增 status（DRAFT / PUBLISHED / UNPUBLISHED），技能无版本号
--   4. skill_code 从「Agent 内唯一」升级为「全局唯一」
-- -------------------------------------------------------

-- 0. 预检：确认是否存在全局重复的 skill_code
--    旧结构下 skill_code 只在单个 Agent 内唯一，跨 Agent 可能重复。
--    若下面这条 SQL 有结果，需先人工改名（例如追加 _2 后缀）再继续执行。
-- SELECT skill_code, COUNT(*) AS cnt, GROUP_CONCAT(id) AS ids
-- FROM agent_skill
-- GROUP BY skill_code
-- HAVING cnt > 1;

-- 1. 新建关联表（先建，才能在删列前把旧关系搬过去）
CREATE TABLE IF NOT EXISTS `agent_skill_rel`
(
    `id`          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `agent_id`    BIGINT   NOT NULL COMMENT '关联 agent_info.id',
    `skill_id`    BIGINT   NOT NULL COMMENT '关联 agent_skill.id',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_agent_skill` (`agent_id`, `skill_id`),
    KEY `idx_skill_id` (`skill_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='Agent 与技能关联';

-- 2. 把旧的 agent_id 归属关系迁到关联表（幂等，可重复执行）
INSERT IGNORE INTO `agent_skill_rel` (`agent_id`, `skill_id`)
SELECT `agent_id`, `id`
FROM `agent_skill`
WHERE `agent_id` IS NOT NULL;

-- 3. 旧技能默认置为已发布，保证已绑定的 Agent 仍能正常加载
ALTER TABLE `agent_skill`
    ADD COLUMN `status` VARCHAR(32) NOT NULL DEFAULT 'PUBLISHED'
        COMMENT '状态：DRAFT / PUBLISHED / UNPUBLISHED' AFTER `description`;

-- 4. 去掉 agent_id 及其索引
ALTER TABLE `agent_skill` DROP INDEX `idx_agent_id`;
ALTER TABLE `agent_skill` DROP COLUMN `agent_id`;

-- 5. 技能级排序字段废弃（排序只保留在 agent_skill_item 上）
ALTER TABLE `agent_skill` DROP COLUMN `sort_order`;

-- 6. skill_code 改为全局唯一（预检通过后再执行，否则会因重复值失败）
ALTER TABLE `agent_skill` ADD UNIQUE KEY `uk_skill_code` (`skill_code`);
