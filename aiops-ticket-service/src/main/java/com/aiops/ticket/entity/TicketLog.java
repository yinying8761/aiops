package com.aiops.ticket.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工单流转日志，对应 ticket_log 表。
 * <p>记录每一次状态变更，是状态机正确性的可审计证据（TC-07）。
 */
@Data
@TableName("ticket_log")
public class TicketLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long ticketId;

    private String fromStatus;

    private String toStatus;

    private String operator;

    private String remark;

    private LocalDateTime createTime;
}
