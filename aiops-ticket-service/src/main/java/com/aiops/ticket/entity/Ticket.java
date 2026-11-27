package com.aiops.ticket.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工单，对应 ticket 表。
 */
@Data
@TableName("ticket")
public class Ticket {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String ticketNo;

    private Long eventId;

    private String title;

    private String assignee;

    private Long assigneeId;

    /** PENDING / PROCESSING / RESOLVED / CLOSED */
    private String status;

    private String priority;

    private String suggestion;

    private String solution;

    private String createBy;

    private LocalDateTime createTime;

    private LocalDateTime acceptTime;

    private LocalDateTime closeTime;

    /** 乐观锁：并发处置同一工单时防止覆盖 */
    @Version
    private Integer version;
}
