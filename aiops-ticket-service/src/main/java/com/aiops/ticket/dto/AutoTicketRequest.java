package com.aiops.ticket.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Agent 分析结果自动建单的请求体，由 agent-service 提交。
 */
@Data
public class AutoTicketRequest {

    private Long eventId;

    private String title;

    /** 优先级，取告警级别 P0~P3 */
    private String priority;

    private String category;

    private String rootCause;

    private String suggestion;

    /** 处置步骤 */
    private List<String> steps;

    private String summary;

    private Long instanceId;

    /** 可选：显式指定处理人；不传则按优先级自动派单 */
    private String assignee;

    /** 附加信息 */
    private Map<String, Object> extra;
}
