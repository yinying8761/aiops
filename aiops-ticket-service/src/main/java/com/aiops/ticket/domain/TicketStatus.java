package com.aiops.ticket.domain;

import com.aiops.common.api.ResultCode;
import com.aiops.common.exception.BizException;

import java.util.Map;
import java.util.Set;

/**
 * 工单状态机。
 *
 * <p>对应文档 4.2 与 TC-07：待处理 → 处理中 → 已解决 → 已关闭。
 * 状态流转受显式白名单约束，任何非法跃迁都会被拒绝并返回明确错误，
 * 避免出现「已关闭又被打回处理中」这类数据不一致。
 *
 * <pre>
 * PENDING ──接受──▶ PROCESSING ──解决──▶ RESOLVED ──关闭──▶ CLOSED
 *    │                  │                   │
 *    └──取消──▶ CLOSED  └──挂起──▶ PENDING   └──重开──▶ PROCESSING
 * </pre>
 */
public final class TicketStatus {

    private TicketStatus() {
    }

    public static final String PENDING = "PENDING";
    public static final String PROCESSING = "PROCESSING";
    public static final String RESOLVED = "RESOLVED";
    public static final String CLOSED = "CLOSED";

    private static final Map<String, String> LABELS = Map.of(
            PENDING, "待处理",
            PROCESSING, "处理中",
            RESOLVED, "已解决",
            CLOSED, "已关闭"
    );

    /** 允许的状态跃迁表 */
    private static final Map<String, Set<String>> ALLOWED = Map.of(
            PENDING, Set.of(PROCESSING, CLOSED),
            PROCESSING, Set.of(RESOLVED, PENDING, CLOSED),
            RESOLVED, Set.of(CLOSED, PROCESSING),
            CLOSED, Set.of()
    );

    public static boolean canTransit(String from, String to) {
        if (from == null || to == null) {
            return false;
        }
        Set<String> allowed = ALLOWED.get(from.toUpperCase());
        return allowed != null && allowed.contains(to.toUpperCase());
    }

    /**
     * 校验跃迁合法性，非法则抛出业务异常。
     */
    public static void assertTransit(String from, String to) {
        if (!isValid(to)) {
            throw new BizException(ResultCode.TICKET_STATUS_ILLEGAL,
                    "未知工单状态: " + to);
        }
        if (!canTransit(from, to)) {
            throw new BizException(ResultCode.TICKET_STATUS_ILLEGAL,
                    String.format("不允许从「%s」流转到「%s」", label(from), label(to)));
        }
    }

    public static boolean isValid(String status) {
        return status != null && LABELS.containsKey(status.toUpperCase());
    }

    public static String label(String status) {
        if (status == null) {
            return "未知";
        }
        return LABELS.getOrDefault(status.toUpperCase(), status);
    }

    public static Set<String> allowedNext(String from) {
        return ALLOWED.getOrDefault(from == null ? "" : from.toUpperCase(), Set.of());
    }
}
