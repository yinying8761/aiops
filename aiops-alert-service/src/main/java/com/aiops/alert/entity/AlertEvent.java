package com.aiops.alert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 聚合告警事件，对应 alert_event 表。
 * <p>一条持续刷新的同类告警只会聚合出一个事件，从而显著降低下游 Agent 与大模型的调用量。
 */
@Data
@TableName("alert_event")
public class AlertEvent {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String fingerprint;

    private Long instanceId;

    private String alertType;

    /** Agent 分类结果 */
    private String category;

    private String level;

    private String title;

    private String summary;

    /** 聚合的告警条数 */
    private Integer count;

    private String status;

    private LocalDateTime firstTime;

    private LocalDateTime lastTime;

    /** PENDING / RUNNING / DONE / FAILED */
    private String analyzeStatus;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
