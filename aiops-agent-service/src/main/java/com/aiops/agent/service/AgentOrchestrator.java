package com.aiops.agent.service;

import com.aiops.agent.dto.AnalyzeResult;
import com.aiops.agent.dto.AnalyzeStep;

import java.util.List;
import java.util.function.Consumer;

/**
 * 多 Agent 编排服务。
 *
 * <p>对应文档 2.5 算法（2）：按「分类 → 根因 → 处置 → 报告」的流水线编排多个专职 Agent，
 * 采用链式顺序编排，每个 Agent 的输出作为下一个 Agent 的输入。
 */
public interface AgentOrchestrator {

    /**
     * 同步执行完整分析链路。
     *
     * @param eventId 聚合告警事件ID
     */
    AnalyzeResult analyze(Long eventId);

    /**
     * 执行分析并以回调方式逐步推送（用于 SSE 流式输出，对应 TC-09）。
     *
     * @param eventId  事件ID
     * @param onStep   每产生一个步骤就回调一次
     */
    AnalyzeResult analyzeStreaming(Long eventId, Consumer<AnalyzeStep> onStep);

    /**
     * 异步执行（由告警服务投递触发），不阻塞调用方。
     */
    void analyzeAsync(Long eventId);

    /**
     * 查询某事件的历史分析轨迹。
     */
    List<AnalyzeStep> loadTrace(Long eventId);
}
