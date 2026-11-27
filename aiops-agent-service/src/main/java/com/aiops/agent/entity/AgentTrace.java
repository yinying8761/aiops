package com.aiops.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent 分析过程留痕，对应 agent_trace 表。
 * <p>把每个阶段、每次工具调用都落库，既支撑前端回放，也让「Agent 真的调了工具」
 * 这件事可被审计、可被测试断言（TC-05 的验证依据）。
 */
@Data
@TableName("agent_trace")
public class AgentTrace {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long eventId;

    /** classification / rca / remediation / report */
    private String stage;

    private Integer stepNo;

    /** THOUGHT / TOOL_CALL / TOOL_RESULT / OUTPUT */
    private String stepType;

    private String toolName;

    private String content;

    private Integer costMs;

    private LocalDateTime createTime;
}
