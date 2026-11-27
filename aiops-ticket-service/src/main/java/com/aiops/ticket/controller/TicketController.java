package com.aiops.ticket.controller;

import com.aiops.common.api.PageResult;
import com.aiops.common.api.Result;
import com.aiops.ticket.dto.AutoTicketRequest;
import com.aiops.ticket.entity.Ticket;
import com.aiops.ticket.entity.TicketLog;
import com.aiops.ticket.service.TicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 工单接口。对应文档 2.6：POST /tickets、PUT /tickets/{id}/status。
 */
@RestController
@RequestMapping("/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;

    /**
     * Agent 分析完成后自动建单（由 agent-service 内部调用）。
     */
    @PostMapping("/auto")
    public Result<Map<String, Object>> autoCreate(@RequestBody AutoTicketRequest request) {
        Ticket t = ticketService.autoCreate(request);
        return Result.ok("已创建工单", Map.of(
                "id", t.getId(),
                "ticketNo", t.getTicketNo(),
                "assignee", t.getAssignee(),
                "status", t.getStatus(),
                "priority", t.getPriority()));
    }

    /**
     * 创建工单。对应文档 2.6 POST /tickets。
     */
    @PostMapping
    public Result<Ticket> create(@RequestBody Ticket ticket) {
        return Result.ok("创建成功", ticketService.create(ticket));
    }

    /**
     * 工单分页查询。
     */
    @GetMapping
    public Result<PageResult<Ticket>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String assignee) {
        return Result.ok(ticketService.page(pageNum, pageSize, status, priority, keyword, assignee));
    }

    /**
     * 工单详情。
     */
    @GetMapping("/{id}")
    public Result<Ticket> get(@PathVariable Long id) {
        return Result.ok(ticketService.get(id));
    }

    /**
     * 按事件查工单。
     */
    @GetMapping("/by-event/{eventId}")
    public Result<Ticket> byEvent(@PathVariable Long eventId) {
        return Result.ok(ticketService.findByEventId(eventId));
    }

    /**
     * 更新工单状态。对应文档 2.6 PUT /tickets/{id}/status，也是 TC-07 的被测接口。
     */
    @PutMapping("/{id}/status")
    public Result<Ticket> updateStatus(@PathVariable Long id,
                                       @RequestParam String status,
                                       @RequestParam(required = false) String remark,
                                       @RequestParam(required = false) String operator) {
        Ticket t = ticketService.transit(id, status, operator, remark);
        return Result.ok("状态已更新为 " + t.getStatus(), t);
    }

    /**
     * 工单流转历史。
     */
    @GetMapping("/{id}/logs")
    public Result<List<TicketLog>> logs(@PathVariable Long id) {
        return Result.ok(ticketService.logs(id));
    }

    /**
     * 工单看板统计。
     */
    @GetMapping("/board")
    public Result<Map<String, Object>> board() {
        return Result.ok(ticketService.board());
    }
}
