package com.aiops.agent.llm;

import lombok.Data;

import java.util.List;

/**
 * 模型的单步决策：要么调用工具，要么给出最终答案。
 */
@Data
public class LlmToolDecision {

    /** THOUGHT（仅思考） / TOOL_CALL（要调工具） / FINAL（最终答案） */
    private String type;

    /** 思考过程（展示到 SSE 与 trace 中，让分析可解释） */
    private String thought;

    /** 要调用的工具名 */
    private String toolName;

    /** 工具参数 */
    private java.util.Map<String, Object> toolArgs;

    /** 最终答案（JSON 字符串或自然语言） */
    private String finalAnswer;

    /** 本步消耗的 token（真实模型才有） */
    private Integer tokens;

    /** 本次调用的理由，便于审计 */
    private String reason;

    public static LlmToolDecision toolCall(String toolName, java.util.Map<String, Object> args,
                                           String thought) {
        LlmToolDecision d = new LlmToolDecision();
        d.setType("TOOL_CALL");
        d.setToolName(toolName);
        d.setToolArgs(args);
        d.setThought(thought);
        return d;
    }

    public static LlmToolDecision finalAnswer(String answer, String thought) {
        LlmToolDecision d = new LlmToolDecision();
        d.setType("FINAL");
        d.setFinalAnswer(answer);
        d.setThought(thought);
        return d;
    }

    public boolean isFinal() {
        return "FINAL".equals(type);
    }

    /** 供日志展示的工具参数摘要 */
    public String argsSummary() {
        if (toolArgs == null || toolArgs.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        toolArgs.forEach((k, v) -> {
            if (!sb.isEmpty()) {
                sb.append(", ");
            }
            sb.append(k).append("=").append(v);
        });
        return sb.toString();
    }

    /** 保留以便未来扩展多工具并行 */
    private List<String> pendingTools;
}
