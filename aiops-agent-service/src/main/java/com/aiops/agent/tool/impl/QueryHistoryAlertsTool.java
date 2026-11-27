package com.aiops.agent.tool.impl;

import com.aiops.agent.client.AlertClient;
import com.aiops.agent.tool.AgentTool;
import com.aiops.common.api.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具二：查询历史告警。
 * <p>对应文档 2.5「query_history_alerts(instance_id, time_range)：查询历史告警」。
 * 用于判断「是首次发生还是反复发生」——反复发生往往说明根因未被真正解决。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueryHistoryAlertsTool implements AgentTool {

    private final AlertClient alertClient;

    @Override
    public String name() {
        return "query_history_alerts";
    }

    @Override
    public String description() {
        return "查询与当前告警类型相关的历史告警事件，用于判断该故障是首次发生、偶发还是反复发生。"
                + "若同类告警在近期多次出现，说明根因可能未被彻底解决，应在结论中提示复发风险。";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("instance_id", Map.of(
                "type", "integer",
                "description", "服务实例ID"));
        props.put("alert_type", Map.of(
                "type", "string",
                "description", "告警类型关键字，如 cpu / connection_pool，用于匹配历史事件"));
        props.put("limit", Map.of(
                "type", "integer",
                "description", "返回条数，默认 5，最大 20"));
        return Map.of("type", "object", "properties", props);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object execute(Map<String, Object> args) {
        Long instanceId = AgentTool.argLong(args, "instance_id", null);
        String alertType = AgentTool.argString(args, "alert_type", null);
        int limit = AgentTool.argLong(args, "limit", 5L).intValue();
        limit = Math.min(Math.max(limit, 1), 20);

        try {
            Result<Map<String, Object>> result =
                    alertClient.queryEvents(1, limit, alertType);
            Map<String, Object> data = result.getData();
            List<Map<String, Object>> records = data == null ? List.of()
                    : (List<Map<String, Object>>) data.getOrDefault("records", List.of());

            // 统计复发情况（排除当前正在进行的事件由调用方处理）
            long recurrence = records.size();
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("success", true);
            out.put("matched_count", recurrence);
            out.put("lookback_limit", limit);
            out.put("recurrence_hint", recurrence >= 3
                    ? "同类告警近期已多次出现，属复发型故障，根因可能未彻底解决"
                    : recurrence == 0
                    ? "未查到同类历史告警，可能是首次发生"
                    : "历史上有少量同类告警");
            out.put("recent_events", records.stream().limit(limit).map(r -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", r.get("id"));
                m.put("level", r.get("level"));
                m.put("count", r.get("count"));
                m.put("status", r.get("status"));
                m.put("firstTime", r.get("firstTime"));
                m.put("title", r.get("title"));
                return m;
            }).toList());
            return out;
        } catch (Exception e) {
            log.warn("query_history_alerts 调用失败: {}", e.getMessage());
            return Map.of("success", false,
                    "error", "告警服务暂不可用：" + e.getMessage());
        }
    }
}
