package com.aiops.ticket.service;

import com.aiops.common.api.PageResult;
import com.aiops.ticket.dto.AutoTicketRequest;
import com.aiops.ticket.entity.Ticket;
import com.aiops.ticket.entity.TicketLog;

import java.util.List;
import java.util.Map;

/**
 * 工单服务。
 */
public interface TicketService {

    /**
     * 按 Agent 分析结果自动建单并派发。
     */
    Ticket autoCreate(AutoTicketRequest request);

    /**
     * 手动建单。
     */
    Ticket create(Ticket ticket);

    /**
     * 分页查询。
     */
    PageResult<Ticket> page(long pageNum, long pageSize, String status,
                            String priority, String keyword, String assignee);

    /**
     * 工单详情。
     */
    Ticket get(Long id);

    /**
     * 状态流转（TC-07 的被测方法）。
     *
     * @param id      工单ID
     * @param target  目标状态
     * @param operator 操作人
     * @param remark  处置备注/解决方案
     */
    Ticket transit(Long id, String target, String operator, String remark);

    /**
     * 工单流转历史。
     */
    List<TicketLog> logs(Long id);

    /**
     * 工单看板统计。
     */
    Map<String, Object> board();

    /**
     * 按事件ID查工单（分析结果页展示用）。
     */
    Ticket findByEventId(Long eventId);
}
