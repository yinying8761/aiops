package com.aiops.agent.tool.impl;

import com.aiops.agent.client.KnowledgeClient;
import com.aiops.agent.tool.AgentTool;
import com.aiops.common.api.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具四：检索处置经验知识模板。
 * <p>对应文档 4.2「knowledge-service：管理处置经验模板，提供关键词 + 向量检索，供处置 Agent 复用」。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueryKnowledgeTool implements AgentTool {

    private final KnowledgeClient knowledgeClient;

    @Override
    public String name() {
        return "query_knowledge";
    }

    @Override
    public String description() {
        return "检索历史沉淀的处置经验模板（症状—根因—解法）。"
                + "在给出处置建议前应优先调用，以复用团队既有经验，避免重复摸索。";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("keyword", Map.of(
                "type", "string",
                "description", "检索关键词，建议使用告警类型或症状词，如『连接池耗尽』『CPU 过高』"));
        props.put("category", Map.of(
                "type", "string",
                "description", "分类过滤，可选：CPU/MEMORY/NETWORK/DISK/APPLICATION/DATABASE/MIDDLEWARE"));
        props.put("limit", Map.of(
                "type", "integer",
                "description", "返回模板条数，默认 3"));
        return Map.of("type", "object", "properties", props,
                "required", List.of("keyword"));
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object execute(Map<String, Object> args) {
        String keyword = AgentTool.argString(args, "keyword", "");
        String category = AgentTool.argString(args, "category", null);
        int limit = AgentTool.argLong(args, "limit", 3L).intValue();
        limit = Math.min(Math.max(limit, 1), 10);

        try {
            Result<List<Map<String, Object>>> result =
                    knowledgeClient.search(keyword, category, limit);
            List<Map<String, Object>> templates = result.getData();
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("success", true);
            out.put("keyword", keyword);
            out.put("matched_count", templates == null ? 0 : templates.size());
            out.put("templates", templates == null ? List.of() : templates);
            if (templates == null || templates.isEmpty()) {
                out.put("message", "知识库中暂无匹配模板，请依据通用运维经验给出处置方案");
            }
            return out;
        } catch (Exception e) {
            log.warn("query_knowledge 调用失败: {}", e.getMessage());
            return Map.of("success", false,
                    "error", "知识库服务暂不可用：" + e.getMessage(),
                    "hint", "可基于通用运维最佳实践给出处置建议");
        }
    }
}
