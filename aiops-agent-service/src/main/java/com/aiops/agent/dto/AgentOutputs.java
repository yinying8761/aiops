package com.aiops.agent.dto;

import lombok.Data;

import java.util.List;

/**
 * 各 Agent 阶段的结构化输出。字段与文档 2.5 算法（2）的契约一致。
 */
public final class AgentOutputs {

    private AgentOutputs() {
    }

    /** 分类 Agent 输出：{category, level, summary} */
    @Data
    public static class Classification {
        private String category;
        private String level;
        private String summary;
        /** 模型给出的置信度，便于前端展示可信程度 */
        private Double confidence;
    }

    /** 根因 Agent 输出：{root_cause, evidence} */
    @Data
    public static class Rca {
        private String rootCause;
        private List<String> evidence;
        private Double confidence;
        private String impact;
        private String nextAction;
        /** 真实执行过的工具调用记录，证明「智能体调用工具干活」 */
        private List<String> toolTrace;
    }

    /** 处置 Agent 输出：{steps[], suggestion} */
    @Data
    public static class Remediation {
        private List<String> steps;
        private String suggestion;
        private String risk;
        /** 命中的知识模板标题 */
        private List<String> matchedTemplates;
    }

    /** 报告 Agent 输出：故障分析报告正文 */
    @Data
    public static class Report {
        private String title;
        private String content;
    }
}
