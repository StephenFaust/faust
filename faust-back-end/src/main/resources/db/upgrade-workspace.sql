-- -------------------------------------------------------
-- 工作空间改造：升级脚本（存量库执行）
--
-- 执行顺序：1 → 2 → 3 → 4 → 5 → 6 → 7（校验）→ 8（确认后再删白名单）
-- 仅执行一次；重复执行第 3 步依赖 uk_personal_owner 幂等，第 4~6 步为幂等更新。
-- -------------------------------------------------------
USE `faust_agent_center`;

-- -------------------------------------------------------
-- 1. 新建空间两表
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
    `personal_owner` BIGINT GENERATED ALWAYS AS (IF(`space_type` = 'PERSONAL', `owner_uc_id`, NULL)) STORED,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_personal_owner` (`personal_owner`),
    KEY `idx_owner_type` (`owner_uc_id`, `space_type`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='工作空间';

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

-- -------------------------------------------------------
-- 2. 业务表加 workspace_id（0 = 系统空间，迁移前的临时归属）
-- -------------------------------------------------------
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

-- -------------------------------------------------------
-- 3. 系统空间 + 每个历史创建者一个个人空间 + 成员行
-- -------------------------------------------------------
INSERT INTO `work_space` (`id`, `name`, `space_type`, `owner_uc_id`, `owner_name`, `description`)
VALUES (0, '系统空间', 'TEAM', 0, 'SYSTEM', '内置 NATIVE 工具的归属空间，全局共享、只读')
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);

INSERT INTO `work_space` (`name`, `space_type`, `owner_uc_id`, `owner_name`, `create_by`, `update_by`)
SELECT CONCAT('个人空间-', u.uc_id), 'PERSONAL', u.uc_id, NULL, u.uc_id, u.uc_id
FROM (SELECT DISTINCT `create_by` AS uc_id FROM `agent_info` WHERE `create_by` IS NOT NULL
      UNION
      SELECT DISTINCT `create_by` FROM `agent_tool` WHERE `create_by` IS NOT NULL
      UNION
      SELECT DISTINCT `create_by` FROM `agent_skill` WHERE `create_by` IS NOT NULL
      UNION
      SELECT DISTINCT `create_by` FROM `data_source_config` WHERE `create_by` IS NOT NULL) u
ON DUPLICATE KEY UPDATE `work_space`.`name` = `work_space`.`name`;

INSERT IGNORE INTO `work_space_user` (`space_id`, `uc_id`, `uc_name`, `role`, `create_by`)
SELECT w.`id`, w.`owner_uc_id`, w.`owner_name`, 'OWNER', w.`owner_uc_id`
FROM `work_space` w
WHERE w.`space_type` = 'PERSONAL';

-- -------------------------------------------------------
-- 4. NATIVE 工具归入系统空间（0）
-- -------------------------------------------------------
UPDATE `agent_tool` SET `workspace_id` = 0 WHERE UPPER(`type`) = 'NATIVE';

-- -------------------------------------------------------
-- 5. 其余资源按创建人回填到其个人空间
-- -------------------------------------------------------
UPDATE `agent_info` a
    JOIN `work_space` w ON w.`owner_uc_id` = a.`create_by` AND w.`space_type` = 'PERSONAL'
SET a.`workspace_id` = w.`id`
WHERE a.`workspace_id` = 0;

UPDATE `agent_tool` t
    JOIN `work_space` w ON w.`owner_uc_id` = t.`create_by` AND w.`space_type` = 'PERSONAL'
SET t.`workspace_id` = w.`id`
WHERE t.`workspace_id` = 0 AND UPPER(t.`type`) <> 'NATIVE';

UPDATE `agent_skill` s
    JOIN `work_space` w ON w.`owner_uc_id` = s.`create_by` AND w.`space_type` = 'PERSONAL'
SET s.`workspace_id` = w.`id`
WHERE s.`workspace_id` = 0;

UPDATE `data_source_config` d
    JOIN `work_space` w ON w.`owner_uc_id` = d.`create_by` AND w.`space_type` = 'PERSONAL'
SET d.`workspace_id` = w.`id`
WHERE d.`workspace_id` = 0;

-- -------------------------------------------------------
-- 6. 校验：预期 agent_info / agent_skill / data_source_config 均为 0（create_by 为空的历史脏数据才会残留）
-- -------------------------------------------------------
SELECT 'agent_info' AS tbl, COUNT(*) AS cnt FROM `agent_info` WHERE `workspace_id` = 0
UNION ALL
SELECT 'agent_skill', COUNT(*) FROM `agent_skill` WHERE `workspace_id` = 0
UNION ALL
SELECT 'data_source_config', COUNT(*) FROM `data_source_config` WHERE `workspace_id` = 0
UNION ALL
SELECT 'agent_tool(非NATIVE)', COUNT(*) FROM `agent_tool` WHERE `workspace_id` = 0 AND UPPER(`type`) <> 'NATIVE';

-- 若上面结果非 0，需要人工把对应行分配到目标空间：
-- UPDATE agent_info SET workspace_id = <spaceId> WHERE id IN (...);

-- -------------------------------------------------------
-- 7. 白名单下线（确认业务回归通过后再执行）
-- -------------------------------------------------------
-- DROP TABLE IF EXISTS `agent_whitelist`;
