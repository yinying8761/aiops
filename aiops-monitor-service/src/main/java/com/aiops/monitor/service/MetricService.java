package com.aiops.monitor.service;

import java.util.List;
import java.util.Map;

/**
 * 监控指标服务。
 */
public interface MetricService {

    /**
     * 采集一次指标并入库。定时任务与手动触发共用。
     *
     * @param instanceId 实例ID
     */
    int collect(Long instanceId);

    /**
     * 采集全部实例。
     */
    int collectAll();

    /**
     * 查询指标时序。
     *
     * @param range 时间范围，如 30m / 2h / 1d
     */
    List<Map<String, Object>> series(Long instanceId, String metricName, String range);

    /**
     * 查实例最新指标快照。
     */
    Map<String, Object> latest(Long instanceId);

    /**
     * 最近日志。
     */
    List<Map<String, Object>> logs(Long instanceId, String range, int limit);

    /**
     * 手动写入一个指标点（演示/测试用，便于制造特定场景）。
     */
    void writePoint(Long instanceId, String metricName, double value, String unit);
}
