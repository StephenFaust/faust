-- -------------------------------------------------------
-- Faust Agent Center - MySQL 建表脚本
-- 适用版本：MySQL 8.0+
-- 说明：实体的 create_time / update_time 由数据库默认值填充，
--       请勿去掉 DEFAULT CURRENT_TIMESTAMP，否则插入后时间为空。
-- -------------------------------------------------------

CREATE DATABASE IF NOT EXISTS `faust_agent_center`
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_general_ci;

USE `faust_agent_center`;

-- -------------------------------------------------------
-- 1. Agent 主表：同一 agent_code 可有多个版本
-- -------------------------------------------------------
CREATE TABLE IF NOT EXISTS `agent_info`
(
    `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `workspace_id`     BIGINT       NOT NULL DEFAULT 0 COMMENT '所属工作空间，见 work_space.id',
    `agent_code`       VARCHAR(64)  NOT NULL COMMENT 'Agent 编码，同一 Agent 各版本共享',
    `name`             VARCHAR(128) NOT NULL COMMENT 'Agent 名称',
    `description`      VARCHAR(512)          DEFAULT NULL COMMENT 'Agent 描述',
    `tags`             JSON                  DEFAULT NULL COMMENT '标签数组',
    `classification`   VARCHAR(64)           DEFAULT NULL COMMENT '分类',
    `use_num`          INT          NOT NULL DEFAULT 0 COMMENT '使用次数',
    `version`          VARCHAR(32)  NOT NULL COMMENT '用户指定版本号，如 1.0.0',
    `can_as_sub_agent` TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否可作为子智能体',
    `status`           VARCHAR(32)  NOT NULL DEFAULT 'UNPUBLISHED' COMMENT '状态：PUBLISHED / UNPUBLISHED',
    `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `create_by`        BIGINT                DEFAULT NULL COMMENT '创建人 UCID',
    `update_by`        BIGINT                DEFAULT NULL COMMENT '更新人 UCID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_agent_code_version` (`agent_code`, `version`),
    KEY `idx_workspace_status` (`workspace_id`, `status`),
    KEY `idx_status` (`status`),
    KEY `idx_classification` (`classification`),
    KEY `idx_create_time` (`create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='Agent 主表';

-- -------------------------------------------------------
-- 2. Agent 模型配置：每个 Agent 版本一条
-- -------------------------------------------------------
CREATE TABLE IF NOT EXISTS `agent_model_config`
(
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `agent_id`    BIGINT       NOT NULL COMMENT '关联 agent_info.id',
    `base_url`    VARCHAR(512)          DEFAULT NULL COMMENT '模型服务地址',
    `api_key`     VARCHAR(512)          DEFAULT NULL COMMENT '模型服务密钥',
    `model_name`  VARCHAR(128)          DEFAULT NULL COMMENT '模型名称',
    `max_tokens`  INT                   DEFAULT 4096 COMMENT '最大输出 token',
    `temperature` DOUBLE                DEFAULT 0.7 COMMENT '采样温度',
    `top_p`       DOUBLE                DEFAULT 1.0 COMMENT 'top_p',
    `timeout`     INT                   DEFAULT 60 COMMENT '超时时间（秒）',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `create_by`   BIGINT                DEFAULT NULL COMMENT '创建人 UCID',
    `update_by`   BIGINT                DEFAULT NULL COMMENT '更新人 UCID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_agent_id` (`agent_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='Agent 模型配置';

-- -------------------------------------------------------
-- 3. Agent 系统提示词
-- -------------------------------------------------------
CREATE TABLE IF NOT EXISTS `agent_system_prompt`
(
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `agent_id`    BIGINT      NOT NULL COMMENT '关联 agent_info.id',
    `content`     MEDIUMTEXT COMMENT '系统提示词',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_agent_id` (`agent_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='Agent 系统提示词';

-- -------------------------------------------------------
-- 4. Skill：独立管理的技能元信息，可发布后供多个 Agent 复用（无版本概念）
-- -------------------------------------------------------
CREATE TABLE IF NOT EXISTS `agent_skill`
(
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `workspace_id` BIGINT      NOT NULL DEFAULT 0 COMMENT '所属工作空间，见 work_space.id',
    `skill_code`  VARCHAR(64)  NOT NULL COMMENT '技能编码，全局唯一',
    `name`        VARCHAR(128) NOT NULL COMMENT '技能名称',
    `key_words`   VARCHAR(512)          DEFAULT NULL COMMENT '触发关键词',
    `description` VARCHAR(1024)         DEFAULT NULL COMMENT '技能描述',
    `status`      VARCHAR(32)  NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT / PUBLISHED / UNPUBLISHED',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `create_by`   BIGINT                DEFAULT NULL COMMENT '创建人 UCID',
    `update_by`   BIGINT                DEFAULT NULL COMMENT '更新人 UCID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_skill_code` (`skill_code`),
    KEY `idx_workspace_status` (`workspace_id`, `status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='技能（独立管理）';

-- -------------------------------------------------------
-- 5. Agent 与技能关联：多对多
-- -------------------------------------------------------
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


-- -------------------------------------------------------
-- 5. Skill 条目：技能下的文档 / 脚本
-- -------------------------------------------------------
CREATE TABLE IF NOT EXISTS `agent_skill_item`
(
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `skill_id`    BIGINT       NOT NULL COMMENT '关联 agent_skill.id',
    `item_type`   VARCHAR(32)  NOT NULL COMMENT '条目类型：DOC / SCRIPT 等',
    `file_name`   VARCHAR(200)          DEFAULT NULL COMMENT '虚拟路径文件名，含格式后缀',
    `content`     MEDIUMTEXT COMMENT '条目内容',
    `sort_order`  INT          NOT NULL DEFAULT 0 COMMENT '排序',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_skill_id` (`skill_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='Agent 技能条目';

-- -------------------------------------------------------
-- 6. 全局技能库（可跨 Agent 复用）
-- -------------------------------------------------------
CREATE TABLE IF NOT EXISTS `agent_skills`
(
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`        VARCHAR(128) NOT NULL COMMENT '技能名称',
    `description` VARCHAR(1024)         DEFAULT NULL COMMENT '技能描述',
    `author`      VARCHAR(64)           DEFAULT NULL COMMENT '作者',
    `tags`        VARCHAR(512)          DEFAULT NULL COMMENT '标签',
    `version`     VARCHAR(32)           DEFAULT NULL COMMENT '版本号',
    `content`     MEDIUMTEXT COMMENT '技能内容',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='全局技能库';

-- -------------------------------------------------------
-- 7. 工具：NATIVE / HTTP / DB / SUB_AGENT
-- -------------------------------------------------------
CREATE TABLE IF NOT EXISTS `agent_tool`
(
    `id`                   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `workspace_id`         BIGINT       NOT NULL DEFAULT 0 COMMENT '所属工作空间（0=系统内置，全局共享）',
    `tool_code`            VARCHAR(64)  NOT NULL COMMENT '工具编码',
    `name`                 VARCHAR(128) NOT NULL COMMENT '工具名称',
    `bean_name`            VARCHAR(128)          DEFAULT NULL COMMENT 'NATIVE 工具对应的 Spring Bean',
    `type`                 VARCHAR(32)  NOT NULL COMMENT '工具类型：NATIVE / HTTP / DB / SUB_AGENT',
    `description`          VARCHAR(1024)         DEFAULT NULL COMMENT '工具描述',
    `tool_schema`          MEDIUMTEXT COMMENT '工具入参 Schema',
    `data_source_config_id` BIGINT              DEFAULT NULL COMMENT 'DB 工具关联 data_source_config.id',
    `sub_agent_id`         BIGINT               DEFAULT NULL COMMENT '子智能体关联 agent_info.id',
    `status`               VARCHAR(32)  NOT NULL DEFAULT 'UNPUBLISHED' COMMENT '状态：PUBLISHED / UNPUBLISHED',
    `publish_version`      VARCHAR(32)           DEFAULT NULL COMMENT '发布版本',
    `tags`                 JSON                  DEFAULT NULL COMMENT '标签数组',
    `create_time`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `create_by`            BIGINT                DEFAULT NULL COMMENT '创建人 UCID',
    `update_by`            BIGINT                DEFAULT NULL COMMENT '更新人 UCID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tool_code` (`tool_code`),
    KEY `idx_workspace_status` (`workspace_id`, `status`),
    KEY `idx_type` (`type`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='工具';

-- -------------------------------------------------------
-- 8. Agent 与工具的关联
-- -------------------------------------------------------
CREATE TABLE IF NOT EXISTS `agent_tool_rel`
(
    `id`          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `agent_id`    BIGINT   NOT NULL COMMENT '关联 agent_info.id',
    `tool_id`     BIGINT   NOT NULL COMMENT '关联 agent_tool.id',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_agent_tool` (`agent_id`, `tool_id`),
    KEY `idx_tool_id` (`tool_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='Agent 与工具关联';

-- -------------------------------------------------------
-- 9. 数据源配置（供 DB 类型工具使用）
--     pwd 存 AES 密文，密钥见 agent.center.encrypt.secret
-- -------------------------------------------------------
CREATE TABLE IF NOT EXISTS `data_source_config`
(
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `workspace_id` BIGINT      NOT NULL DEFAULT 0 COMMENT '所属工作空间，见 work_space.id',
    `db_name`     VARCHAR(128) NOT NULL COMMENT '数据源名称',
    `db_type`     VARCHAR(32)  NOT NULL COMMENT '数据库类型，如 mysql',
    `url`         VARCHAR(1024) NOT NULL COMMENT 'JDBC 连接串',
    `user`        VARCHAR(128) NOT NULL COMMENT '数据库用户名',
    `pwd`         VARCHAR(512)          DEFAULT NULL COMMENT '数据库密码（AES 密文）',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `create_by`   BIGINT                DEFAULT NULL COMMENT '创建人 UCID',
    `update_by`   BIGINT                DEFAULT NULL COMMENT '更新人 UCID',
    PRIMARY KEY (`id`),
    KEY `idx_db_name` (`db_name`),
    KEY `idx_workspace_id` (`workspace_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='数据源配置';

-- -------------------------------------------------------
-- 11. 会话记录
-- -------------------------------------------------------
CREATE TABLE IF NOT EXISTS `conversation_record`
(
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `uc_id`       BIGINT                DEFAULT NULL COMMENT '所属用户 UCID',
    `agent_code`  VARCHAR(64)  NOT NULL COMMENT 'Agent 编码',
    `version`     VARCHAR(32)           DEFAULT NULL COMMENT 'Agent 版本',
    `chat_id`     VARCHAR(64)  NOT NULL COMMENT '会话 ID',
    `title`       VARCHAR(255)          DEFAULT NULL COMMENT '会话标题',
    `env_var`     JSON                  DEFAULT NULL COMMENT '会话级环境变量',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_chat_id` (`chat_id`),
    KEY `idx_uc_agent` (`uc_id`, `agent_code`),
    KEY `idx_update_time` (`update_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='会话记录';

-- -------------------------------------------------------
-- 12. 消息记录
-- -------------------------------------------------------
CREATE TABLE IF NOT EXISTS `message_record_item`
(
    `id`        BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `chat_id`   VARCHAR(64) NOT NULL COMMENT '会话 ID',
    `type`      VARCHAR(32) NOT NULL COMMENT '消息类型：user / assistant / tool_request / tool_response',
    `content`   MEDIUMTEXT COMMENT '消息内容',
    `file_list` JSON                 DEFAULT NULL COMMENT '附件列表',
    `date_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '消息时间',
    PRIMARY KEY (`id`),
    KEY `idx_chat_id_date_time` (`chat_id`, `date_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='消息记录';

-- -------------------------------------------------------
-- 13. 字典
-- -------------------------------------------------------
CREATE TABLE IF NOT EXISTS `agent_dict`
(
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `dict_type`   VARCHAR(64)  NOT NULL COMMENT '字典类型',
    `dict_code`   VARCHAR(64)  NOT NULL COMMENT '字典编码',
    `dict_label`  VARCHAR(128) NOT NULL COMMENT '字典展示名',
    `sort_order`  INT          NOT NULL DEFAULT 0 COMMENT '排序',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `create_by`   VARCHAR(64)           DEFAULT NULL COMMENT '创建人',
    `update_by`   VARCHAR(64)           DEFAULT NULL COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_type_code` (`dict_type`, `dict_code`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='数据字典';

-- -------------------------------------------------------
-- 14. 工作空间：PERSONAL（个人，一人一个）/ TEAM（团队，可多个）
--     id = 0 为"系统空间"，仅存放 NATIVE 工具（全局共享）
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
    -- 仅 PERSONAL 行有值，配合唯一索引保证「一人一个个人空间」
    `personal_owner` BIGINT GENERATED ALWAYS AS (IF(`space_type` = 'PERSONAL', `owner_uc_id`, NULL)) STORED,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_personal_owner` (`personal_owner`),
    KEY `idx_owner_type` (`owner_uc_id`, `space_type`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='工作空间';

-- -------------------------------------------------------
-- 15. 工作空间成员：个人空间同样有一行（role=OWNER）
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

-- -------------------------------------------------------
-- 16. Agent 全局环境变量：Agent + 用户维度一行，新建会话时带入 conversation_record.env_var
-- -------------------------------------------------------
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

-- -------------------------------------------------------
-- 初始化数据：常用字典项
-- -------------------------------------------------------
INSERT INTO `agent_dict` (`dict_type`, `dict_code`, `dict_label`, `sort_order`)
VALUES ('agent_classification', 'operations', '运维排查', 1),
       ('agent_classification', 'data_analysis', '数据分析', 2),
       ('agent_classification', 'general', '通用助手', 3)
ON DUPLICATE KEY UPDATE `dict_label` = VALUES(`dict_label`);

-- -------------------------------------------------------
-- 初始化数据：系统空间（id = 0，存放 NATIVE 工具，全局共享、只读）
-- -------------------------------------------------------
INSERT INTO `work_space` (`id`, `name`, `space_type`, `owner_uc_id`, `owner_name`, `description`)
VALUES (0, '系统空间', 'TEAM', 0, 'SYSTEM', '内置 NATIVE 工具的归属空间，全局共享、只读')
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);
