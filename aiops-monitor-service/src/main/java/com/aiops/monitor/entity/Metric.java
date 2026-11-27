package com.aiops.monitor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 监控指标，对应 metric 表。
 */
@Data
@TableName("metric")
public class Metric {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long instanceId;

    private String metricName;

    private BigDecimal value;

    private String unit;

    private LocalDateTime ts;

    private LocalDateTime createTime;
}
