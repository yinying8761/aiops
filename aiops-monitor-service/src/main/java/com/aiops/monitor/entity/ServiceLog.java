package com.aiops.monitor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 服务日志，对应 service_log 表。
 */
@Data
@TableName("service_log")
public class ServiceLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long instanceId;

    private String level;

    private String logger;

    private String message;

    private LocalDateTime ts;
}
