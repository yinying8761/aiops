package com.aiops.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent 执行过程中的单个步骤，用于 SSE 推送与过程留痕。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyzeStep {

    /** 阶段：classification / rca / remediation / report */
    private String stage;

    /** 阶段中文名，前端直接展示 */
    private String stageLabel;

    /** 步骤类型：STAGE_START / THOUGHT / TOOL_CALL / TOOL_RESULT / OUTPUT / STAGE_END / DONE */
    private String type;

    /** 步骤序号（阶段内） */
    private Integer stepNo;

    /** 展示内容 */
    private String content;

    /** 工具名（TOOL_CALL / TOOL_RESULT 时有值） */
    private String toolName;

    /** 耗时（毫秒） */
    private Long costMs;

    /** 时间戳，前端按序渲染 */
    private Long timestamp;
}
