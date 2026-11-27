-- ============================================================================
--  AIOps 平台初始化种子数据（公开版）
--  说明：本文件用于代码仓库，已将真实姓名/联系方式脱敏为示例账号。
--        真实环境请自行维护用户数据；密码统一为 123456 / admin123。
-- ============================================================================

USE aiops;
SET NAMES utf8mb4;

-- 清空主数据，使本脚本可重复执行（不动业务数据表：alert/metric/ticket 等）
SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE sys_user_role;
TRUNCATE TABLE sys_user;
TRUNCATE TABLE sys_role;
TRUNCATE TABLE service_instance;
DELETE FROM knowledge_template;
SET FOREIGN_KEY_CHECKS = 1;

-- ---------------------------------------------------------------------------
-- 角色
-- ---------------------------------------------------------------------------
INSERT INTO sys_role (id, role_name, role_code, description, status) VALUES
(1, '系统管理员', 'ADMIN',   '拥有全部权限，管理用户与角色', 1),
(2, '运维工程师', 'OPS',     '处理告警、工单处置、查询知识模板', 1),
(3, '值班负责人', 'MANAGER', '接收工单并确认处置结果', 1);

-- ---------------------------------------------------------------------------
-- 用户（示例账号，非真实人员）
--   admin / admin123        系统管理员
--   其余账号 / 123456        运维相关人员
-- ---------------------------------------------------------------------------
INSERT INTO sys_user (id, username, password, nickname, email, phone, status, remark) VALUES
(1, 'admin',      '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '系统管理员', 'admin@example.com',      '13800000001', 1, '内置管理员'),
(2, 'ops_duty',   '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '值班负责人', 'ops_duty@example.com',   '13800000002', 1, '值班/工单响应'),
(3, 'ops_db',     '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '数据库运维', 'ops_db@example.com',     '13800000003', 1, '数据库与报表'),
(4, 'ops_backend','$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '后端运维',   'ops_backend@example.com','13800000004', 1, '后端服务与部署');

INSERT INTO sys_user_role (user_id, role_id) VALUES
(1, 1),
(2, 1),
(2, 2),
(3, 2),
(4, 2),
(3, 3);

-- ---------------------------------------------------------------------------
-- 被监控服务实例
-- ---------------------------------------------------------------------------
INSERT INTO service_instance (id, service_name, host, port, env, owner, status) VALUES
(1, 'demo-order-service',  '127.0.0.1', 8090, 'PROD', 'ops_backend', 1),
(2, 'aiops-auth-service',  '127.0.0.1', 8081, 'PROD', 'ops_backend', 1),
(3, 'aiops-alert-service', '127.0.0.1', 8082, 'PROD', 'ops_backend', 1),
(4, 'aiops-report-service','127.0.0.1', 8087, 'PROD', 'ops_db',      1),
(5, 'demo-payment-service','127.0.0.1', 8091, 'PROD', 'ops_backend', 1);

-- ---------------------------------------------------------------------------
-- 处置经验模板（症状 — 根因 — 解法，供处置建议 Agent 复用）
-- ---------------------------------------------------------------------------
INSERT INTO knowledge_template (title, symptom, root_cause, solution, tags, category, use_count, create_by) VALUES
('CPU 使用率持续过高',
 '服务实例 CPU 使用率连续 5 分钟超过 90%，接口响应变慢，P95 延迟显著上升',
 '常见于死循环、正则回溯、大对象频繁 GC、突发流量未限流',
 '1. 查看 top 定位高占用线程；\n2. jstack 导出线程栈，排查死循环或锁竞争；\n3. 检查是否突发流量，必要时网关限流；\n4. 若为 GC 引起，检查堆内存与 GC 日志，调整 JVM 参数；\n5. 临时扩容实例分摊压力。',
 'cpu,高负载,性能', 'CPU', 12, 'system'),

('内存使用率过高 / 内存泄漏',
 '服务内存使用率持续攀升且 GC 后无法回落，偶发 OutOfMemoryError',
 '长生命周期集合持续增长、缓存未设上限、连接/流未关闭',
 '1. jmap -histo 查看对象分布；\n2. dump 堆文件用 MAT 分析支配树；\n3. 定位泄漏点并修复；\n4. 为本地缓存设置容量上限与过期策略；\n5. 临时重启实例缓解。',
 'memory,内存泄漏,oom', 'MEMORY', 8, 'system'),

('数据库连接池耗尽',
 '应用抛出 CannotGetJdbcConnectionException / 连接等待超时，接口大面积 500',
 '慢 SQL 占满连接、事务过大、连接未归还、数据库连接数上限偏低',
 '1. 查看连接池活跃连接与等待队列；\n2. 查询 MySQL processlist 定位慢 SQL；\n3. 优化慢查询并补索引；\n4. 缩小事务范围，避免事务中调用远程服务；\n5. 适当调大连接池 maxActive 与数据库 max_connections。',
 'database,连接池,慢SQL', 'DATABASE', 15, 'system'),

('网络延迟与丢包',
 '服务间调用超时率上升，Feign 报 Read timed out，跨机房延迟抖动',
 '网络抖动、带宽打满、DNS 解析异常、目标服务过载',
 '1. ping / mtr 确认链路丢包节点；\n2. 检查网卡与带宽使用率；\n3. 排查 DNS 解析耗时；\n4. 若为下游过载，启用熔断降级；\n5. 必要时切换备用线路或扩容带宽。',
 'network,超时,丢包', 'NETWORK', 6, 'system'),

('磁盘 IO 打满 / 空间不足',
 '磁盘使用率超过 85%，写日志报 No space left on device，IO wait 升高',
 '日志未轮转、临时文件堆积、大文件写入、磁盘规格不足',
 '1. du -sh 定位大目录；\n2. 清理过期日志与临时文件；\n3. 配置 logback 滚动策略与保留天数；\n4. 检查是否有异常大文件写入；\n5. 扩容磁盘或挂载新分区。',
 'disk,磁盘,IO', 'DISK', 4, 'system'),

('线程池耗尽 / 请求排队',
 '接口大量超时，线程池活跃线程数达到上限，拒绝策略触发',
 '同步阻塞调用过多、下游响应慢、线程池参数不合理',
 '1. 查看线程池活跃数、队列长度、拒绝次数；\n2. 定位阻塞调用点，改为异步或增加超时；\n3. 调整核心线程数与队列容量；\n4. 对下游增加熔断与降级；\n5. 压测验证调整效果。',
 'thread,线程池,超时', 'APPLICATION', 9, 'system'),

('MQ 消息堆积',
 'RabbitMQ 队列消息数持续增长，消费端 TPS 低于生产端',
 '消费逻辑慢、消费者实例不足、消息重复消费、异常导致重试',
 '1. 查看队列 ready/unacked 数量；\n2. 检查消费者日志是否有异常重试；\n3. 优化消费逻辑，批量处理；\n4. 增加消费者实例并行度；\n5. 对失败消息转入死信队列单独处理。',
 'mq,消息堆积,rabbitmq', 'MIDDLEWARE', 5, 'system'),

('服务实例健康检查失败',
 'Nacos 中实例被标记为不健康，网关路由不到该实例，流量倾斜到其他节点',
 '应用假死、健康检查接口阻塞、心跳线程被占满、网络分区',
 '1. 检查应用进程与端口是否存活；\n2. 查看健康检查接口响应耗时；\n3. 检查是否有线程池/连接池耗尽导致心跳阻塞；\n4. 重启异常实例；\n5. 调整心跳超时与剔除策略。',
 'nacos,健康检查,服务治理', 'APPLICATION', 7, 'system');
