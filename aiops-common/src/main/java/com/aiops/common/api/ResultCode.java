package com.aiops.common.api;

import lombok.Getter;

/**
 * 统一业务返回码。
 * 约定：0 成功；1xxxx 通用；2xxxx 认证鉴权；3xxxx 告警；4xxxx Agent；5xxxx 工单；6xxxx 知识库；7xxxx 报表。
 */
@Getter
public enum ResultCode {

    SUCCESS(0, "操作成功"),

    FAIL(10000, "操作失败"),
    PARAM_ERROR(10001, "参数校验未通过"),
    NOT_FOUND(10002, "资源不存在"),
    METHOD_NOT_ALLOWED(10003, "请求方法不支持"),
    RATE_LIMITED(10004, "请求过于频繁，请稍后再试"),
    SYSTEM_ERROR(10005, "系统内部错误"),
    SERVICE_UNAVAILABLE(10006, "依赖服务不可用，已降级处理"),

    UNAUTHORIZED(20001, "未认证或登录已过期"),
    FORBIDDEN(20002, "无权限访问该资源"),
    LOGIN_FAILED(20003, "用户名或密码错误"),
    ACCOUNT_DISABLED(20004, "账号已被禁用"),
    TOKEN_INVALID(20005, "Token 无效"),
    TOKEN_EXPIRED(20006, "Token 已过期"),

    ALERT_NOT_FOUND(30001, "告警不存在"),
    ALERT_EVENT_NOT_FOUND(30002, "告警事件不存在"),
    ALERT_INGEST_FAILED(30003, "告警接入失败"),

    AGENT_ANALYZE_FAILED(40001, "Agent 分析失败"),
    LLM_CALL_FAILED(40002, "大模型调用失败"),
    LLM_OUTPUT_INVALID(40003, "大模型输出格式不合法"),

    TICKET_NOT_FOUND(50001, "工单不存在"),
    TICKET_STATUS_ILLEGAL(50002, "工单状态流转非法"),

    TEMPLATE_NOT_FOUND(60001, "知识模板不存在"),

    REPORT_GENERATE_FAILED(70001, "报表生成失败");

    private final Integer code;
    private final String message;

    ResultCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
