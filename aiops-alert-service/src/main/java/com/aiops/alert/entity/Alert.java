package com.aiops.alert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 告警记录，对应 alert 表。
 */
@Data
@TableName("alert")
public class Alert {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long instanceId;

    /** 告警类型，如 cpu / memory / network */
    private String alertType;

    private String level;

    private String title;

    private String content;

    /** 聚合指纹 */
    private String fingerprint;

    private String status;

    /** 所属聚合事件ID */
    private Long eventId;

    private LocalDateTime createTime;
}
