package com.aiops.agent.tool;

import com.aiops.common.exception.BizException;
import com.aiops.common.api.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具注册中心。
 *
 * <p>把所有 {@link AgentTool} 实现按名称登记，供编排层查找与执行，
 * 同时生成给大模型看的工具描述列表。
 */
@Slf4j
@Component
public class ToolRegistry {

    private final Map<String, AgentTool> tools = new LinkedHashMap<>();

    public ToolRegistry(List<AgentTool> toolList) {
        for (AgentTool tool : toolList) {
            tools.put(tool.name(), tool);
        }
        log.info("Agent 工具注册完成，共 {} 个：{}", tools.size(), tools.keySet());
    }

    public AgentTool get(String name) {
        AgentTool tool = tools.get(name);
        if (tool == null) {
            throw new BizException(ResultCode.AGENT_ANALYZE_FAILED, "未注册的工具: " + name);
        }
        return tool;
    }

    public boolean contains(String name) {
        return tools.containsKey(name);
    }

    /**
     * 生成供大模型使用的工具描述列表（OpenAI tools 格式）。
     */
    public List<Map<String, Object>> describeAll() {
        return tools.values().stream().map(t -> {
            Map<String, Object> fn = new LinkedHashMap<>();
            fn.put("name", t.name());
            fn.put("description", t.description());
            fn.put("parameters", t.parameterSchema());
            Map<String, Object> wrapper = new LinkedHashMap<>();
            wrapper.put("type", "function");
            wrapper.put("function", fn);
            return wrapper;
        }).toList();
    }

    public java.util.Set<String> names() {
        return tools.keySet();
    }
}
