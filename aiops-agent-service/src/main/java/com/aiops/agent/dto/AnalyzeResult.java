package com.aiops.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 一次完整 Agent 分析的汇总结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyzeResult {

    private Long eventId;

    /** 使用的模型实现：mock / deepseek */
    private String llmProvider;

    private AgentOutputs.Classification classification;
    private AgentOutputs.Rca rca;
    private AgentOutputs.Remediation remediation;
    private AgentOutputs.Report report;

    /** 自动创建的工单ID */
    private Long ticketId;

    /** 工单号 */
    private String ticketNo;

    private Long costMs;

    private LocalDateTime startTime;

    /** 完整分析过程（供前端回放 SSE 流） */
    private List<AnalyzeStep> steps;
}
