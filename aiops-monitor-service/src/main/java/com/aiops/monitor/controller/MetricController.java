package com.aiops.monitor.controller;

import com.aiops.common.api.Result;
import com.aiops.monitor.service.MetricService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 监控指标接口。对应文档 2.6：GET /metrics/{instanceId}。
 */
@RestController
@RequestMapping("/metrics")
@RequiredArgsConstructor
public class MetricController {

    private final MetricService metricService;

    /**
     * 查询某项指标的时序数据（Agent 工具 query_metrics 的数据源）。
     */
    @GetMapping("/{instanceId}/series")
    public Result<List<Map<String, Object>>> series(
            @PathVariable Long instanceId,
            @RequestParam String metricName,
            @RequestParam(defaultValue = "30m") String range) {
        return Result.ok(metricService.series(instanceId, metricName, range));
    }

    /**
     * 查询实例最新指标快照。
     */
    @GetMapping("/{instanceId}/latest")
    public Result<Map<String, Object>> latest(@PathVariable Long instanceId) {
        return Result.ok(metricService.latest(instanceId));
    }

    /**
     * 查询实例最近日志。
     */
    @GetMapping("/{instanceId}/logs")
    public Result<List<Map<String, Object>>> logs(
            @PathVariable Long instanceId,
            @RequestParam(defaultValue = "30m") String range,
            @RequestParam(defaultValue = "50") int limit) {
        return Result.ok(metricService.logs(instanceId, range, limit));
    }

    /**
     * 手动触发一次采集（演示用）。
     */
    @PostMapping("/collect")
    public Result<Map<String, Object>> collect(@RequestParam(required = false) Long instanceId) {
        int n = instanceId == null ? metricService.collectAll() : metricService.collect(instanceId);
        return Result.ok("采集完成", Map.of("written", n));
    }

    /**
     * 手动注入指标点，用于构造特定故障场景（演示 TC-05 连接池耗尽）。
     */
    @PostMapping("/{instanceId}/point")
    public Result<Void> writePoint(
            @PathVariable Long instanceId,
            @RequestParam String metricName,
            @RequestParam double value,
            @RequestParam(defaultValue = "") String unit) {
        metricService.writePoint(instanceId, metricName, value, unit);
        return Result.ok("已写入", null);
    }
}
