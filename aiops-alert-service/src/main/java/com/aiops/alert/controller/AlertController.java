package com.aiops.alert.controller;

import com.aiops.alert.dto.AlertEventView;
import com.aiops.alert.dto.AlertIngestRequest;
import com.aiops.alert.dto.AlertIngestResult;
import com.aiops.alert.service.AlertService;
import com.aiops.common.api.PageResult;
import com.aiops.common.api.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 告警接口。对应文档 2.6：
 * POST /alerts（接收原始告警）、GET /alerts/events（查询聚合事件列表）。
 */
@RestController
@RequestMapping("/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;

    /**
     * 接收原始告警（内部上报）。对应 TC-03 告警聚合去重、TC-04 告警分级。
     */
    @PostMapping
    public Result<AlertIngestResult> ingest(@Valid @RequestBody AlertIngestRequest request) {
        return Result.ok("已接收", alertService.ingest(request));
    }

    /**
     * 批量接入：用于「告警风暴」压测与演示（TC-03 一次性上报 100 条）。
     */
    @PostMapping("/batch")
    public Result<Map<String, Object>> ingestBatch(
            @RequestBody @NotEmpty(message = "告警列表不能为空") List<@Valid AlertIngestRequest> requests) {
        return Result.ok("批量接入完成", alertService.ingestBatch(requests));
    }

    /**
     * 查询聚合告警事件列表。
     */
    @GetMapping("/events")
    public Result<PageResult<AlertEventView>> pageEvents(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime to) {
        return Result.ok(alertService.pageEvents(pageNum, pageSize, level, status, keyword, from, to));
    }

    /**
     * 聚合事件详情。
     */
    @GetMapping("/events/{id}")
    public Result<AlertEventView> getEvent(@PathVariable Long id) {
        return Result.ok(alertService.getEvent(id));
    }

    /**
     * 事件下的原始告警明细。
     */
    @GetMapping("/events/{id}/alerts")
    public Result<List<Map<String, Object>>> listAlerts(@PathVariable Long id) {
        return Result.ok(alertService.listAlertsOfEvent(id));
    }

    /**
     * 更新事件状态。
     */
    @PutMapping("/events/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam String status) {
        alertService.updateEventStatus(id, status);
        return Result.ok("状态已更新", null);
    }

    /**
     * 回写 Agent 分析结论（分类、级别、摘要、分析状态）。
     * 由 agent-service 在分析完成后调用，使大屏与报表能展示分类分布。
     */
    @PutMapping("/events/{id}/analysis")
    public Result<Void> updateAnalysis(
            @PathVariable Long id,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String summary,
            @RequestParam(required = false) String analyzeStatus) {
        alertService.updateAnalysis(id, category, level, summary, analyzeStatus);
        return Result.ok("分析结论已回写", null);
    }

    /**
     * 大屏概览数据。
     */
    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard() {
        return Result.ok(alertService.dashboard());
    }

    /**
     * 补偿扫描：重新投递仍待分析的事件（应对 Agent 服务临时不可用）。
     */
    @PostMapping("/events/rescan")
    public Result<Map<String, Object>> rescan(@RequestParam(defaultValue = "50") int limit) {
        int n = alertService.rescanPending(limit);
        return Result.ok("已重新投递 " + n + " 个事件", Map.of("count", n));
    }
}
