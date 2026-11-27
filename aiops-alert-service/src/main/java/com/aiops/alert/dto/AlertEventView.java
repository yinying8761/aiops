package com.aiops.alert.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 聚合告警事件列表/详情视图。
 */
@Data
public class AlertEventView {

    private Long id;
    private String fingerprint;
    private Long instanceId;
    private String serviceName;
    private String alertType;
    private String category;
    private String level;
    private String levelLabel;
    private String title;
    private String summary;
    private Integer count;
    private String status;
    private String analyzeStatus;
    private LocalDateTime firstTime;
    private LocalDateTime lastTime;

    /** 持续时间（秒），用于大屏展示 */
    private Long durationSeconds;
}
