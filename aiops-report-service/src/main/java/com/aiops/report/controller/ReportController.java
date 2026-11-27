package com.aiops.report.controller;

import com.aiops.common.api.PageResult;
import com.aiops.common.api.Result;
import com.aiops.report.entity.FaultReport;
import com.aiops.report.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 统计报表接口。对应文档 2.6：GET /reports/stats。
 */
@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    /**
     * 综合统计报表。
     */
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats(@RequestParam(defaultValue = "7") int days) {
        return Result.ok(reportService.stats(days));
    }

    /**
     * 大屏概览指标。
     */
    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        return Result.ok(reportService.overview());
    }

    /**
     * 故障报告列表。
     */
    @GetMapping("/list")
    public Result<PageResult<FaultReport>> list(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String keyword) {
        return Result.ok(reportService.pageReports(pageNum, pageSize, keyword));
    }

    /**
     * 报告详情。
     */
    @GetMapping("/{id}")
    public Result<FaultReport> get(@PathVariable Long id) {
        return Result.ok(reportService.getReport(id));
    }

    /**
     * 按事件查询最近一份报告。无报告时 data 为 null（不报错，便于前端判断）。
     */
    @GetMapping("/by-event/{eventId}")
    public Result<FaultReport> byEvent(@PathVariable Long eventId) {
        return Result.ok(reportService.findByEventId(eventId));
    }

    /**
     * 保存报告（由 agent-service 在分析完成后调用）。
     * 接受 Map 形式，便于跨服务传递且不暴露实体细节。
     */
    @PostMapping
    public Result<Long> save(@RequestBody Map<String, Object> payload) {
        FaultReport report = new FaultReport();
        report.setEventId(asLong(payload.get("eventId")));
        report.setTitle(str(payload.get("title")));
        report.setCategory(str(payload.get("category")));
        report.setRootCause(str(payload.get("rootCause")));
        report.setEvidence(str(payload.get("evidence")));
        report.setSteps(str(payload.get("steps")));
        report.setSuggestion(str(payload.get("suggestion")));
        report.setReportContent(str(payload.get("reportContent")));
        report.setCostSeconds(asInt(payload.get("costSeconds")));
        return Result.ok("已保存", reportService.saveReport(report));
    }

    private Long asLong(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.valueOf(v.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer asInt(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.valueOf(v.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String str(Object v) {
        return v == null ? null : v.toString();
    }

    /**
     * 导出报告为 Markdown 文件（对应文档 1.4「故障分析报告的查看与导出」）。
     */
    @GetMapping("/{id}/export")
    public ResponseEntity<byte[]> export(@PathVariable Long id) {
        FaultReport r = reportService.getReport(id);
        String md = reportService.exportMarkdown(id);
        byte[] bytes = md.getBytes(StandardCharsets.UTF_8);
        String filename = "report-" + id + ".md";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/markdown; charset=UTF-8"))
                .body(bytes);
    }
}
