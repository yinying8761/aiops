package com.aiops.alert.dto;

import lombok.Data;

/**
 * 告警级别/类型的分级规则。放在配置里，便于不重启调整阈值。
 */
@Data
public class LevelRule {

    /** 告警类型，如 cpu */
    private String alertType;

    /** 指标阈值；metricValue 超过该值时提升级别 */
    private Double threshold;

    /** 超过阈值时的级别 */
    private String levelOver;

    /** 未超过阈值时的默认级别 */
    private String levelDefault;
}
