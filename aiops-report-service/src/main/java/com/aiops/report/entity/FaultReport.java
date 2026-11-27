package com.aiops.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 故障分析报告，对应 fault_report 表。
 */
@Data
@TableName("fault_report")
public class FaultReport {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long eventId;

    private String title;

    private String category;

    private String rootCause;

    /** 证据链（JSON 数组字符串） */
    private String evidence;

    /** 处置步骤（JSON 数组字符串），回显分析页时用于还原分步方案 */
    private String steps;

    private String suggestion;

    /** 报告正文（Markdown） */
    private String reportContent;

    private Integer costSeconds;

    private LocalDateTime createTime;
}
