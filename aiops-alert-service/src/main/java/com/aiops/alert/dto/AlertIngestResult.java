package com.aiops.alert.dto;

import lombok.Data;

/**
 * 告警接入结果。前端/监控系统据此判断本次告警是被聚合掉了还是触发了新事件。
 */
@Data
public class AlertIngestResult {

    /** 原始告警ID */
    private Long alertId;

    /** 指纹 */
    private String fingerprint;

    /** 当前指纹在时间窗口内的累计计数 */
    private Long count;

    /** 归属的聚合事件ID（可能为空：计数未达触发阈值前事件尚未创建） */
    private Long eventId;

    /** 本次是否新建了聚合事件 */
    private Boolean eventCreated;

    /** 本次是否触发了 Agent 分析 */
    private Boolean analysisTriggered;

    /** 本次判定的级别 */
    private String level;

    /** 提示信息 */
    private String message;
}
