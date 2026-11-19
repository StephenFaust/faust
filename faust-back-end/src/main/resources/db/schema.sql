-- -------------------------------------------------------
-- Faust Agent Center - MySQL 建表脚本
-- 适用版本：MySQL 8.0+
-- 说明：实体的 create_time / update_time 由数据库默认值填充，
--       请勿去掉 DEFAULT CURRENT_TIMESTAMP，否则插入后时间为空。
-- -------------------------------------------------------

CREATE DATABASE IF NOT EXISTS `faust`
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_general_ci;

USE `faust`;

create table agent_dict
(
    id          bigint auto_increment comment '主键ID'
        primary key,
    dict_type   varchar(50)  default ''                not null comment '字典类型：agent_status/tool_status/skill_item_type',
    dict_code   varchar(50)  default ''                not null comment '字典编码',
    dict_label  varchar(100) default ''                not null comment '字典标签（中文描述）',
    sort_order  int          default 0                 not null comment '排序号',
    create_time datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    update_time datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    create_by   bigint       default 0                 not null comment '创建人',
    update_by   bigint       default 0                 not null comment '更新人',
    constraint uk_type_code
        unique (dict_type, dict_code)
)
    comment '数据字典表';

create index idx_dict_type
    on agent_dict (dict_type);

create table agent_global_env_config
(
    id          bigint auto_increment comment '主键'
        primary key,
    uc_id       bigint                                 not null comment '所属用户 UCID（用户维度，一人一行）',
    agent_code  varchar(100) default ''                not null comment '所属 Agent 编码',
    env_var     json                                   null comment '全局环境变量（key-value）',
    create_time datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    update_time datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    constraint uk_uc_agent_code_id
        unique (uc_id, agent_code)
)
    comment 'Agent 全局环境变量（用户维度）';

create table agent_info
(
    id               bigint auto_increment comment '主键ID'
        primary key,
    workspace_id     bigint       default 0                 not null comment '所属工作空间',
    agent_code       varchar(64)  default ''                not null comment 'Agent编码，同一Agent各版本共享',
    name             varchar(100) default ''                not null comment 'Agent名称',
    description      varchar(100) default ''                null comment 'Agent描述，最长100字符',
    tags             varchar(500) default ''                null comment '标签，逗号分隔，如troubleshooting,fast',
    classification   varchar(50)  default ''                not null comment '分类',
    version          varchar(32)  default ''                not null comment '用户指定版本号，如1.0.0、2.1.0',
    can_as_sub_agent tinyint(1)   default 0                 not null comment '是否可以作为子智能体',
    status           varchar(20)  default 'UNPUBLISHED'     not null comment '状态：PUBLISHED/UNPUBLISHED',
    use_num          bigint       default 0                 not null comment '调用次数',
    create_time      datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    update_time      datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    create_by        bigint       default 0                 not null comment '创建人',
    update_by        bigint       default 0                 not null comment '更新人',
    constraint uk_code_version
        unique (agent_code, version)
)
    comment 'Agent主表';

create index idx_agent_code
    on agent_info (agent_code);

create index idx_workspace_status
    on agent_info (workspace_id, status);

create table agent_model_config
(
    id          bigint auto_increment comment '主键ID'
        primary key,
    agent_id    bigint        default -1                not null comment '关联agent_info.id',
    base_url    varchar(500)  default ''                not null comment '模型API基础URL',
    api_key     varchar(500)  default ''                not null comment 'API密钥',
    model_name  varchar(100)  default ''                not null comment '模型名称，如gpt-4、qwen-max',
    max_tokens  int           default 4096              null comment '最大Token数',
    temperature decimal(3, 2) default 0.70              null comment '温度参数，0.00-1.00',
    top_p       decimal(3, 2) default 1.00              null comment 'Top-P采样参数，0.00-1.00',
    timeout     int           default 60                null comment '请求超时时间（秒）',
    create_time datetime      default CURRENT_TIMESTAMP not null comment '创建时间',
    update_time datetime      default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    create_by   bigint        default 0                 not null comment '创建人',
    update_by   bigint        default 0                 not null comment '更新人'
)
    comment 'Agent模型配置表';

create index idx_agent_id
    on agent_model_config (agent_id);

create table agent_skill
(
    id           bigint auto_increment comment '主键ID'
        primary key,
    workspace_id bigint       default 0                 not null comment '所属工作空间',
    skill_code   varchar(64)  default ''                not null comment '技能编码，agent内唯一',
    name         varchar(100) default ''                not null comment '技能名称',
    description  varchar(500) default ''                not null comment '技能描述',
    status       varchar(32)  default 'UNPUBLISHED'     not null comment '状态：PUBLISHED=已发布 UNPUBLISHED=未发布',
    key_words    varchar(200) default ''                not null comment '关键词',
    create_time  datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    update_time  datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    create_by    bigint       default 0                 not null comment '创建人',
    update_by    bigint       default 0                 not null comment '更新人',
    constraint uk_agent_skill_code
        unique (skill_code)
)
    comment 'Agent技能表';

create index idx_skill_status
    on agent_skill (status);

create index idx_workspace_status
    on agent_skill (workspace_id, status);

create table agent_skill_item
(
    id          bigint auto_increment comment '主键ID'
        primary key,
    skill_id    bigint       default -1                not null comment '关联agent_skill.id',
    file_name   varchar(500) default ''                not null comment '文件名',
    item_type   varchar(20)  default ''                not null comment '数据类型：CONTENT/SCRIPT/REFERENCE',
    content     mediumtext                             null comment '条目内容（Markdown/代码/URL等）',
    sort_order  int          default 0                 not null comment '排序序号',
    create_time datetime     default CURRENT_TIMESTAMP not null comment '创建时间'
)
    comment 'Agent技能明细表';

create index idx_skill_id
    on agent_skill_item (skill_id);

create table agent_skill_rel
(
    id          bigint auto_increment comment '主键'
        primary key,
    agent_id    bigint                             not null comment '关联 agent_info.id',
    skill_id    bigint                             not null comment '关联 agent_skill.id',
    create_time datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    constraint uk_agent_skill
        unique (agent_id, skill_id)
)
    comment 'Agent 与技能关联';

create index idx_skill_id
    on agent_skill_rel (skill_id);

create table agent_system_prompt
(
    id          bigint auto_increment comment '主键ID'
        primary key,
    agent_id    bigint   default -1                not null comment '关联agent_info.id',
    version     int      default -1                not null comment '版本号：-1=当前活跃版本，>=1为历史版本',
    content     mediumtext                         not null comment '提示词内容（支持Markdown）',
    create_time datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    constraint uk_agent_version
        unique (agent_id, version)
)
    comment 'Agent系统提示词表';

create index idx_agent_id
    on agent_system_prompt (agent_id);

create table agent_tool
(
    id                    bigint auto_increment comment '主键ID'
        primary key,
    workspace_id          bigint         default 0                 not null comment '所属工作空间（0=系统内置）',
    tool_code             varchar(64)    default ''                not null comment '工具编码，全局唯一',
    name                  varchar(100)   default ''                not null comment '工具名称',
    bean_name             varchar(50)    default ''                not null comment '原生方法的beanName',
    type                  varchar(50)    default ''                not null comment '类型',
    tags                  varchar(100)   default '[]'              not null comment '标签',
    description           varchar(500)   default ''                null comment '工具描述',
    tool_schema           varchar(10000) default '{}'              not null comment '工具Schema（JSON Schema格式，含name/description/parameters）',
    data_source_config_id bigint         default 0                 not null comment '数据源配置id',
    sub_agent_id          bigint         default 0                 not null comment '子agent',
    status                varchar(20)    default 'UNPUBLISHED'     not null comment '状态：DRAFT=UNPUBLISHED=未发布/PUBLISHED=已发布',
    publish_version       varchar(32)    default ''                not null comment '发布版本号（仅PUBLISHED状态有值）',
    create_time           datetime       default CURRENT_TIMESTAMP not null comment '创建时间',
    update_time           datetime       default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    create_by             bigint         default 0                 not null comment '创建人',
    update_by             bigint         default 0                 not null comment '更新人',
    constraint uniq_tool_code
        unique (tool_code)
)
    comment 'Agent工具表（工具定义，可发布共享）';

create index idx_tool_code
    on agent_tool (tool_code);

create index idx_workspace_status
    on agent_tool (workspace_id, status);

create table agent_tool_rel
(
    id          bigint auto_increment comment '主键ID'
        primary key,
    agent_id    bigint   default -1                not null comment '关联agent_info.id',
    tool_id     bigint   default -1                not null comment '关联agent_tool.id',
    create_time datetime default CURRENT_TIMESTAMP not null comment '创建时间'
)
    comment 'Agent与工具关联表';

create index idx_agent_id
    on agent_tool_rel (agent_id);

create index idx_tool_id
    on agent_tool_rel (tool_id);

create table conversation_record
(
    id          bigint auto_increment comment 'id'
        primary key,
    uc_id       bigint       default 0                 not null comment 'ucId',
    agent_code  varchar(50)  default ''                not null comment 'agent编码',
    version     varchar(50)  default ''                not null comment 'agent版本',
    chat_id     varchar(100) default ''                not null comment '会话id',
    title       varchar(200) default ''                not null comment '标题',
    env_var     json                                   null comment '环境变量',
    create_time datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    update_time datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '操作时间',
    constraint uniq_chat_id_uc_id
        unique (chat_id, uc_id)
);

create index idx_chat_id_and_agent_code
    on conversation_record (chat_id, agent_code);

create index idx_ucid_agent_code
    on conversation_record (uc_id, agent_code);

create table data_source_config
(
    id           bigint auto_increment comment 'id'
        primary key,
    workspace_id bigint       default 0                 not null comment '所属工作空间',
    db_name      varchar(50)  default ''                not null comment '数据源名称',
    db_type      varchar(50)  default ''                not null comment '数据库类型',
    url          varchar(200) default ''                not null comment '地址',
    user         varchar(100) default ''                not null comment '用户名',
    pwd          varchar(200) default ''                not null comment '密码',
    create_by    bigint       default 0                 not null comment '创建人',
    create_time  datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    update_by    bigint       default 0                 not null comment '操作人',
    update_time  datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '操作时间'
);

create index idx_workspace_id
    on data_source_config (workspace_id);

create table message_record_item
(
    id        bigint auto_increment comment 'id'
        primary key,
    chat_id   varchar(100) default ''                not null comment '会话id',
    type      varchar(10)  default ''                not null comment '消息类型',
    content   longtext                               null comment '内容',
    file_list text                                   null comment '文件列表（json）',
    date_time datetime     default CURRENT_TIMESTAMP not null comment '创建时间'
)
    comment '消息记录';

create index idx_chat_id
    on message_record_item (chat_id);

create table work_space
(
    id             bigint auto_increment comment '主键'
        primary key,
    name           varchar(128)                       not null comment '空间名称',
    space_type     varchar(16)                        not null comment '类型：PERSONAL / TEAM',
    owner_uc_id    bigint                             not null comment '创建者/所有者 UCID',
    owner_name     varchar(128)                       null comment '创建者昵称快照',
    description    varchar(512)                       null comment '空间描述',
    create_time    datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    update_time    datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    create_by      bigint                             null comment '创建人 UCID',
    update_by      bigint                             null comment '更新人 UCID',
    personal_owner bigint as (if((`space_type` = _utf8mb4'PERSONAL'), `owner_uc_id`, NULL)) stored,
    constraint uk_personal_owner
        unique (personal_owner)
)
    comment '工作空间';

create index idx_owner_type
    on work_space (owner_uc_id, space_type);

create table work_space_user
(
    id          bigint auto_increment comment '主键'
        primary key,
    space_id    bigint                                not null comment '关联 work_space.id',
    uc_id       bigint                                not null comment '成员 UCID',
    uc_name     varchar(128)                          null comment '成员昵称快照',
    role        varchar(16) default 'MEMBER'          not null comment '角色：OWNER / MEMBER',
    create_time datetime    default CURRENT_TIMESTAMP not null comment '加入时间',
    create_by   bigint                                null comment '邀请人 UCID',
    constraint uk_space_uc
        unique (space_id, uc_id)
)
    comment '工作空间成员';

create index idx_uc_id
    on work_space_user (uc_id);

