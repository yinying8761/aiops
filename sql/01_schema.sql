-- ============================================================================
--  AI 智能运维告警分析与处置平台（AIOps）
--  《软件工程综合实践R》SJ003303 —— 第 11 组「不想动项目组」
--  建表脚本：11 张核心表（对应技术文档 2.4 数据库设计）
--  MySQL 8.0+ / utf8mb4
-- ============================================================================

DROP DATABASE IF EXISTS aiops;
CREATE DATABASE aiops DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE aiops;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ---------------------------------------------------------------------------
-- 1. 用户与权限
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS sys_user;
CREATE TABLE sys_user (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    username    VARCHAR(50)  NOT NULL COMMENT '登录名',
    password    VARCHAR(100) NOT NULL COMMENT 'BCrypt 密文',
    nickname    VARCHAR(50)           DEFAULT NULL COMMENT '昵称',
    email       VARCHAR(100)          DEFAULT NULL COMMENT '邮箱',
    phone       VARCHAR(20)           DEFAULT NULL COMMENT '手机号',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1启用 0禁用',
    remark      VARCHAR(255)          DEFAULT NULL COMMENT '备注',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常 1删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username),
    KEY idx_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='用户表';

DROP TABLE IF EXISTS sys_role;
CREATE TABLE sys_role (
    id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    role_name   VARCHAR(50) NOT NULL COMMENT '角色名称',
    role_code   VARCHAR(50) NOT NULL COMMENT '角色编码，如 ADMIN/OPS/MANAGER',
    description VARCHAR(255)         DEFAULT NULL COMMENT '描述',
    status      TINYINT     NOT NULL DEFAULT 1 COMMENT '状态：1启用 0禁用',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_code (role_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='角色表';

DROP TABLE IF EXISTS sys_user_role;
CREATE TABLE sys_user_role (
    id      BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_role (user_id, role_id),
    KEY idx_role_id (role_id),
    CONSTRAINT fk_ur_user FOREIGN KEY (user_id) REFERENCES sys_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_ur_role FOREIGN KEY (role_id) REFERENCES sys_role (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='用户-角色关联表';

-- ---------------------------------------------------------------------------
-- 2. 被监控服务实例
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS service_instance;
CREATE TABLE service_instance (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    service_name VARCHAR(100) NOT NULL COMMENT '服务名，如 demo-order-service',
    host         VARCHAR(100) NOT NULL COMMENT '主机地址',
    port         INT          NOT NULL COMMENT '端口',
    env          VARCHAR(20)  NOT NULL DEFAULT 'PROD' COMMENT '环境：PROD/UAT/DEV',
    owner        VARCHAR(50)           DEFAULT NULL COMMENT '负责人',
    status       TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1运行 0停止',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_service_host_port (service_name, host, port),
    KEY idx_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='被监控服务实例表';

-- ---------------------------------------------------------------------------
-- 3. 告警
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS alert;
CREATE TABLE alert (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    instance_id BIGINT       NOT NULL COMMENT '服务实例ID',
    alert_type  VARCHAR(50)  NOT NULL COMMENT '告警类型：cpu/memory/network/...',
    level       VARCHAR(10)  NOT NULL DEFAULT 'P2' COMMENT '级别：P0/P1/P2/P3',
    title       VARCHAR(200)          DEFAULT NULL COMMENT '告警标题',
    content     VARCHAR(1000)         DEFAULT NULL COMMENT '告警内容',
    fingerprint VARCHAR(64)           DEFAULT NULL COMMENT '指纹（聚合去重用）',
    status      VARCHAR(20)  NOT NULL DEFAULT 'NEW' COMMENT '状态：NEW/ACK/ANALYZING/HANDLING/RESOLVED/CLOSED/IGNORED',
    event_id    BIGINT                DEFAULT NULL COMMENT '所属聚合告警事件ID',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_instance_type (instance_id, alert_type),
    KEY idx_fingerprint (fingerprint),
    KEY idx_event_id (event_id),
    KEY idx_create_time (create_time),
    KEY idx_status_level (status, level)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='告警记录表';

DROP TABLE IF EXISTS alert_event;
CREATE TABLE alert_event (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    fingerprint   VARCHAR(64)  NOT NULL COMMENT '指纹',
    instance_id   BIGINT       NOT NULL COMMENT '服务实例ID',
    alert_type    VARCHAR(50)  NOT NULL COMMENT '告警类型',
    category      VARCHAR(30)           DEFAULT NULL COMMENT 'Agent 分类结果：CPU/MEMORY/...',
    level         VARCHAR(10)  NOT NULL DEFAULT 'P2' COMMENT '级别：P0/P1/P2/P3',
    title         VARCHAR(200)          DEFAULT NULL COMMENT '事件标题',
    summary       VARCHAR(1000)         DEFAULT NULL COMMENT '事件摘要',
    count         INT          NOT NULL DEFAULT 1 COMMENT '聚合告警条数',
    status        VARCHAR(20)  NOT NULL DEFAULT 'NEW' COMMENT '状态',
    first_time    DATETIME     NOT NULL COMMENT '首次发生时间',
    last_time     DATETIME     NOT NULL COMMENT '最近发生时间',
    analyze_status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '分析状态：PENDING/RUNNING/DONE/FAILED',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_fingerprint (fingerprint),
    KEY idx_instance (instance_id),
    KEY idx_level_status (level, status),
    KEY idx_first_time (first_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='聚合告警事件表';

-- ---------------------------------------------------------------------------
-- 4. 监控指标与日志
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS metric;
CREATE TABLE metric (
    id          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    instance_id BIGINT        NOT NULL COMMENT '服务实例ID',
    metric_name VARCHAR(50)   NOT NULL COMMENT '指标名：cpu_usage/mem_usage/...',
    value       DECIMAL(18, 4) NOT NULL COMMENT '指标值',
    unit        VARCHAR(20)            DEFAULT NULL COMMENT '单位：%/ms/count',
    ts          DATETIME      NOT NULL COMMENT '采集时间',
    create_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '入库时间',
    PRIMARY KEY (id),
    KEY idx_instance_metric_ts (instance_id, metric_name, ts),
    KEY idx_ts (ts)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='监控指标表';

DROP TABLE IF EXISTS service_log;
CREATE TABLE service_log (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    instance_id BIGINT       NOT NULL COMMENT '服务实例ID',
    level       VARCHAR(10)  NOT NULL DEFAULT 'INFO' COMMENT '级别：INFO/WARN/ERROR',
    logger      VARCHAR(100)          DEFAULT NULL COMMENT '日志来源',
    message     VARCHAR(2000)         DEFAULT NULL COMMENT '日志内容',
    ts          DATETIME     NOT NULL COMMENT '日志时间',
    PRIMARY KEY (id),
    KEY idx_instance_ts (instance_id, ts),
    KEY idx_level (level)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='服务日志表';

-- ---------------------------------------------------------------------------
-- 5. 工单
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS ticket;
CREATE TABLE ticket (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    ticket_no   VARCHAR(32)  NOT NULL COMMENT '工单号',
    event_id    BIGINT                DEFAULT NULL COMMENT '关联告警事件ID',
    title       VARCHAR(200)          DEFAULT NULL COMMENT '工单标题',
    assignee    VARCHAR(50)           DEFAULT NULL COMMENT '处理人',
    assignee_id BIGINT                DEFAULT NULL COMMENT '处理人ID',
    status      VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/PROCESSING/RESOLVED/CLOSED',
    priority    VARCHAR(10)  NOT NULL DEFAULT 'P2' COMMENT '优先级',
    suggestion  TEXT                  DEFAULT NULL COMMENT 'Agent 生成的处置建议',
    solution    TEXT                  DEFAULT NULL COMMENT '实际处置结果',
    create_by   VARCHAR(50)           DEFAULT NULL COMMENT '创建人',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    accept_time DATETIME              DEFAULT NULL COMMENT '接单时间',
    close_time  DATETIME              DEFAULT NULL COMMENT '关闭时间',
    version     INT          NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ticket_no (ticket_no),
    KEY idx_event_id (event_id),
    KEY idx_status (status),
    KEY idx_assignee (assignee),
    KEY idx_create_time (create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='工单表';

DROP TABLE IF EXISTS ticket_log;
CREATE TABLE ticket_log (
    id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    ticket_id   BIGINT      NOT NULL COMMENT '工单ID',
    from_status VARCHAR(20)          DEFAULT NULL COMMENT '原状态',
    to_status   VARCHAR(20) NOT NULL COMMENT '新状态',
    operator    VARCHAR(50)          DEFAULT NULL COMMENT '操作人',
    remark      VARCHAR(500)         DEFAULT NULL COMMENT '备注',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (id),
    KEY idx_ticket_id (ticket_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='工单流转日志表';

-- ---------------------------------------------------------------------------
-- 6. 处置经验知识库
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS knowledge_template;
CREATE TABLE knowledge_template (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    title       VARCHAR(200) NOT NULL COMMENT '模板标题',
    symptom     VARCHAR(500) NOT NULL COMMENT '症状描述',
    root_cause  VARCHAR(1000)         DEFAULT NULL COMMENT '根因',
    solution    TEXT                  DEFAULT NULL COMMENT '处置步骤',
    tags        VARCHAR(255)          DEFAULT NULL COMMENT '标签，逗号分隔',
    category    VARCHAR(30)           DEFAULT NULL COMMENT '分类',
    use_count   INT          NOT NULL DEFAULT 0 COMMENT '复用次数',
    create_by   VARCHAR(50)           DEFAULT NULL COMMENT '创建人',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    KEY idx_category (category),
    KEY idx_title (title),
    FULLTEXT KEY ft_symptom (symptom, root_cause) WITH PARSER ngram
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='处置经验模板表';

-- ---------------------------------------------------------------------------
-- 7. 故障报告
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS fault_report;
CREATE TABLE fault_report (
    id             BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    event_id       BIGINT               DEFAULT NULL COMMENT '关联告警事件ID',
    title          VARCHAR(200)         DEFAULT NULL COMMENT '报告标题',
    category       VARCHAR(30)          DEFAULT NULL COMMENT '分类',
    root_cause     VARCHAR(1000)        DEFAULT NULL COMMENT '根因结论',
    evidence       TEXT                 DEFAULT NULL COMMENT '证据链（JSON）',
    steps          TEXT                 DEFAULT NULL COMMENT '处置步骤（JSON 数组）',
    suggestion     TEXT                 DEFAULT NULL COMMENT '处置建议',
    report_content MEDIUMTEXT           DEFAULT NULL COMMENT '报告正文（Markdown）',
    cost_seconds   INT                  DEFAULT NULL COMMENT '分析耗时（秒）',
    create_time    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '生成时间',
    PRIMARY KEY (id),
    KEY idx_event_id (event_id),
    KEY idx_create_time (create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='故障分析报告表';

-- ---------------------------------------------------------------------------
-- 8. 操作日志
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS operation_log;
CREATE TABLE operation_log (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id     BIGINT                DEFAULT NULL COMMENT '操作人ID',
    username    VARCHAR(50)           DEFAULT NULL COMMENT '操作人',
    module      VARCHAR(50)           DEFAULT NULL COMMENT '模块',
    action      VARCHAR(100)          DEFAULT NULL COMMENT '动作',
    detail      VARCHAR(2000)         DEFAULT NULL COMMENT '详情',
    ip          VARCHAR(50)           DEFAULT NULL COMMENT '来源IP',
    success     TINYINT      NOT NULL DEFAULT 1 COMMENT '是否成功',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (id),
    KEY idx_user_id (user_id),
    KEY idx_create_time (create_time),
    KEY idx_module (module)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='操作日志表';

-- ---------------------------------------------------------------------------
-- 9. Agent 分析过程留痕（支撑 4.3 SSE 与 5.2 TC-09 的可验证性）
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS agent_trace;
CREATE TABLE agent_trace (
    id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    event_id    BIGINT      NOT NULL COMMENT '告警事件ID',
    stage       VARCHAR(30) NOT NULL COMMENT '阶段：classification/rca/remediation/report',
    step_no     INT         NOT NULL DEFAULT 1 COMMENT '阶段内步骤序号',
    step_type   VARCHAR(20) NOT NULL DEFAULT 'THOUGHT' COMMENT '类型：THOUGHT/TOOL_CALL/TOOL_RESULT/OUTPUT',
    tool_name   VARCHAR(50)          DEFAULT NULL COMMENT '工具名',
    content     TEXT                 DEFAULT NULL COMMENT '内容',
    cost_ms     INT                  DEFAULT NULL COMMENT '本步耗时（毫秒）',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间',
    PRIMARY KEY (id),
    KEY idx_event_stage (event_id, stage),
    KEY idx_create_time (create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='Agent 分析过程留痕表';

SET FOREIGN_KEY_CHECKS = 1;
