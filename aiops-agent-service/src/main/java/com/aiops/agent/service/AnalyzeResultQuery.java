package com.aiops.agent.service;

import com.aiops.agent.dto.AnalyzeResult;

/**
 * 分析结果查询：用于进入分析页时回显上一次的结论，避免用户每次都要重新跑一遍。
 */
public interface AnalyzeResultQuery {

    /**
     * 查询某事件最近一次分析结果。
     * <p>数据来源：fault_report（结论与报告）+ ticket（工单）+ agent_trace（过程）。
     *
     * @return 无历史结果时返回 null
     */
    AnalyzeResult loadLastResult(Long eventId);
}
