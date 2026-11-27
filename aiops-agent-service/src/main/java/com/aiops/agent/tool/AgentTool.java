package com.aiops.agent.tool;

import java.util.Map;

/**
 * Agent 可调用的工具。
 *
 * <p>对应文档 2.5 算法（3）与 4.3（1）：根因分析 Agent 不依赖「文档问答」，
 * 而是通过 Tool Calling 主动查询监控数据。每个工具都实现本接口，
 * 由 {@code ToolRegistry} 统一注册，模型通过名称选择调用。
 */
public interface AgentTool {

    /** 工具名，模型通过它来选择，需稳定且唯一 */
    String name();

    /**
     * 工具描述。描述质量直接影响模型选对工具与传对参数，
     * 对应 4.4「Agent 工具调用参数不准确」的解决方案——细化描述。
     */
    String description();

    /**
     * 参数 JSON Schema，供模型理解每个参数的含义与约束。
     */
    Map<String, Object> parameterSchema();

    /**
     * 执行工具。
     *
     * @param args 模型给出的参数
     * @return 结构化结果，会被序列化后回灌给模型
     */
    Object execute(Map<String, Object> args);

    /**
     * 参数容错：从模型给的任意类型参数中取 Long。
     * 大模型常把数字写成字符串，这里统一兼容。
     */
    static Long argLong(Map<String, Object> args, String key, Long defaultValue) {
        Object v = args == null ? null : args.get(key);
        if (v == null) {
            return defaultValue;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.valueOf(v.toString().trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    static String argString(Map<String, Object> args, String key, String defaultValue) {
        Object v = args == null ? null : args.get(key);
        return v == null ? defaultValue : v.toString();
    }
}
