package com.aiops.common.constant;

/**
 * 跨服务共享的常量。集中定义，避免各服务魔法值不一致。
 */
public final class CommonConstants {

    private CommonConstants() {
    }

    // ---------- HTTP ----------
    public static final String HEADER_AUTHORIZATION = "Authorization";
    public static final String TOKEN_PREFIX = "Bearer ";
    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USERNAME = "X-Username";
    public static final String HEADER_ROLES = "X-Roles";

    // ---------- JWT ----------
    public static final String JWT_CLAIM_USER_ID = "uid";
    public static final String JWT_CLAIM_USERNAME = "username";
    public static final String JWT_CLAIM_NICKNAME = "nickname";
    public static final String JWT_CLAIM_ROLES = "roles";

    // ---------- Redis Key ----------
    /** 告警指纹计数：alert:fp:{fingerprint} */
    public static final String REDIS_ALERT_FP = "alert:fp:";
    /** 告警指纹→事件ID 映射：alert:fp:event:{fingerprint} */
    public static final String REDIS_ALERT_FP_EVENT = "alert:fp:event:";
    /** 指标最新值缓存：metric:latest:{instanceId}:{metricName} */
    public static final String REDIS_METRIC_LATEST = "metric:latest:";
    /** Token 黑名单（注销）：auth:blacklist:{jti} */
    public static final String REDIS_TOKEN_BLACKLIST = "auth:blacklist:";

    // ---------- 告警 ----------
    /** 聚合时间窗口（分钟），文档 2.5 算法规定 5 分钟 */
    public static final int ALERT_AGG_WINDOW_MINUTES = 5;
    /** 指纹计数达到该值触发 Agent 分析（文档：计数 1→2 时触发） */
    public static final long ALERT_TRIGGER_COUNT = 2L;

    // ---------- MQ ----------
    public static final String MQ_EXCHANGE_ALERT = "aiops.alert.exchange";
    public static final String MQ_QUEUE_ALERT_INGEST = "aiops.alert.ingest.queue";
    public static final String MQ_ROUTING_ALERT_INGEST = "aiops.alert.ingest";
    public static final String MQ_QUEUE_AGENT_ANALYZE = "aiops.agent.analyze.queue";
    public static final String MQ_ROUTING_AGENT_ANALYZE = "aiops.agent.analyze";

    // ---------- 业务 ----------
    public static final String SYSTEM_OPERATOR = "system";
    public static final String AGENT_PIPELINE_DONE = "DONE";
}
