package com.aiops.alert.service;

import com.aiops.alert.dto.AlertEventView;
import com.aiops.alert.dto.AlertIngestRequest;
import com.aiops.alert.dto.AlertIngestResult;
import com.aiops.common.api.PageResult;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 告警接入与聚合服务。
 */
public interface AlertService {

    /**
     * 接收原始告警，执行「指纹聚合去重 + 分级」。
     * <p>这是文档 2.5 算法（1）的入口，也是 TC-03 / TC-04 的被测对象。
     */
    AlertIngestResult ingest(AlertIngestRequest request);

    /**
     * 批量接入（压测、演示脚本用）。
     */
    Map<String, Object> ingestBatch(List<AlertIngestRequest> requests);

    /**
     * 聚合事件分页查询。
     */
    PageResult<AlertEventView> pageEvents(long pageNum, long pageSize, String level,
                                          String status, String keyword,
                                          LocalDateTime from, LocalDateTime to);

    /**
     * 事件详情。
     */
    AlertEventView getEvent(Long id);

    /**
     * 查询事件关联的原始告警。
     */
    List<Map<String, Object>> listAlertsOfEvent(Long eventId);

    /**
     * 更新事件状态。
     */
    void updateEventStatus(Long eventId, String status);

    /**
     * 回写 Agent 分析结论。参数为 null 的字段不更新。
     */
    void updateAnalysis(Long eventId, String category, String level,
                        String summary, String analyzeStatus);

    /**
     * 大屏概览：今日告警数、事件数、各级别分布、待处理数等。
     */
    Map<String, Object> dashboard();

    /**
     * 补偿扫描：把仍处于 PENDING 的事件重新投递给 Agent。
     */
    int rescanPending(int limit);
}
