package com.aiops.report.service;

import com.aiops.common.api.PageResult;
import com.aiops.report.entity.FaultReport;

import java.util.Map;

/**
 * 统计报表服务。
 */
public interface ReportService {

    /**
     * 综合统计：趋势、分类、级别、Top 实例、MTTR、降噪率。对应 GET /reports/stats。
     */
    Map<String, Object> stats(int days);

    /**
     * 报表概览卡片（大屏顶部指标）。
     */
    Map<String, Object> overview();

    /**
     * 故障报告分页。
     */
    PageResult<FaultReport> pageReports(long pageNum, long pageSize, String keyword);

    /**
     * 报告详情。
     */
    FaultReport getReport(Long id);

    /**
     * 按事件ID查询最近一份报告，供分析页回显。无报告返回 null。
     */
    FaultReport findByEventId(Long eventId);

    /**
     * 保存 Agent 生成的报告。
     */
    Long saveReport(FaultReport report);

    /**
     * 导出报告为 Markdown 文本。
     */
    String exportMarkdown(Long id);
}
