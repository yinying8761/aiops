package com.aiops.alert.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 原始告警上报请求。监控系统通过 HTTP 推送该结构（文档 2.6 POST /alerts）。
 */
@Data
public class AlertIngestRequest {

    @NotNull(message = "服务实例ID不能为空")
    private Long instanceId;

    @NotBlank(message = "告警类型不能为空")
    private String alertType;

    /** 可选的初始级别；不传则按类型做默认分级 */
    private String level;

    private String title;

    @NotBlank(message = "告警内容不能为空")
    private String content;

    /** 告警发生时间；不传则取服务端当前时间 */
    private LocalDateTime ts;

    /** 指标值，便于分级时参考（如 cpu_usage=95.2） */
    private Double metricValue;
}
