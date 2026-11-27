package com.aiops.agent.tool.impl;

import com.aiops.agent.client.MonitorClient;
import com.aiops.agent.tool.AgentTool;
import com.aiops.common.api.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具一：查询监控指标时序。
 * <p>对应文档 2.5「query_metrics(instance_id, metric_name, time_range)：查询监控指标时序」。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueryMetricsTool implements AgentTool {

    private final MonitorClient monitorClient;

    @Override
    public String name() {
        return "query_metrics";
    }

    @Override
    public String description() {
        return "查询指定服务实例某项监控指标在给定时间范围内的时序数据。"
                + "适用于判断资源是否真的异常、异常从何时开始、峰值多少。"
                + "常用 metric_name：cpu_usage（CPU 使用率%）、mem_usage（内存使用率%）、"
                + "disk_usage（磁盘使用率%）、conn_pool_usage（连接池使用率%）、"
                + "thread_pool_usage（线程池使用率%）、rt_p95（P95 响应毫秒）、qps（每秒请求数）。";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("instance_id", Map.of(
                "type", "integer",
                "description", "服务实例ID，例如 1"));
        props.put("metric_name", Map.of(
                "type", "string",
                "description", "指标名，如 cpu_usage"));
        props.put("time_range", Map.of(
                "type", "string",
                "description", "时间范围，如 30m / 2h / 1d，默认 30m"));
        return Map.of("type", "object", "properties", props,
                "required", List.of("instance_id", "metric_name"));
    }

    @Override
    public Object execute(Map<String, Object> args) {
        Long instanceId = AgentTool.argLong(args, "instance_id", null);
        String metricName = AgentTool.argString(args, "metric_name", "cpu_usage");
        String range = AgentTool.argString(args, "time_range", "30m");

        if (instanceId == null) {
            return Map.of("success", false, "error", "缺少 instance_id 参数");
        }

        try {
            Result<List<Map<String, Object>>> result =
                    monitorClient.querySeries(instanceId, metricName, range);
            List<Map<String, Object>> points = result.getData();
            if (points == null || points.isEmpty()) {
                return Map.of("success", true, "instance_id", instanceId,
                        "metric_name", metricName, "time_range", range,
                        "point_count", 0,
                        "message", "该时间范围内无采集数据");
            }

            double max = Double.MIN_VALUE;
            double min = Double.MAX_VALUE;
            double sum = 0;
            for (Map<String, Object> p : points) {
                Object v = p.get("value");
                if (v instanceof Number n) {
                    double d = n.doubleValue();
                    max = Math.max(max, d);
                    min = Math.min(min, d);
                    sum += d;
                }
            }
            double avg = points.isEmpty() ? 0 : sum / points.size();

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("success", true);
            out.put("instance_id", instanceId);
            out.put("metric_name", metricName);
            out.put("time_range", range);
            out.put("point_count", points.size());
            out.put("max", round(max));
            out.put("min", round(min));
            out.put("avg", round(avg));
            // 只回灌尾部若干点，控制 prompt 长度
            out.put("recent_points", points.size() <= 12 ? points
                    : points.subList(points.size() - 12, points.size()));
            return out;
        } catch (Exception e) {
            log.warn("query_metrics 调用失败 instanceId={} metric={}: {}",
                    instanceId, metricName, e.getMessage());
            return Map.of("success", false,
                    "error", "监控服务暂不可用：" + e.getMessage(),
                    "hint", "可依据告警内容与历史经验继续推理");
        }
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
