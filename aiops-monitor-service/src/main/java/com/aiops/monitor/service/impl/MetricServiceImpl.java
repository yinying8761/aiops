package com.aiops.monitor.service.impl;

import com.aiops.monitor.entity.Metric;
import com.aiops.monitor.mapper.MetricMapper;
import com.aiops.monitor.mapper.ServiceLogMapper;
import com.aiops.common.constant.CommonConstants;
import com.aiops.monitor.service.MetricService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * 监控指标服务实现。
 *
 * <p>采集策略：课程环境没有真实 APM 探针，这里用「基线 + 正弦波动 + 随机扰动」
 * 生成贴近真实的指标曲线；同时支持通过 {@link #writePoint} 手动注入异常点，
 * 便于演示「制造连接池耗尽告警」等场景（TC-05）。
 *
 * <p>最新值同时写入 Redis，供 Agent 工具低延迟读取。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetricServiceImpl implements MetricService {

    /** 各实例的采集指标基线：instanceId -> (metricName -> [基线, 单位]) */
    private static final Map<Long, Map<String, Object[]>> BASELINES = Map.of(
            1L, Map.of(
                    "cpu_usage", new Object[]{42.0, "%"},
                    "mem_usage", new Object[]{58.0, "%"},
                    "conn_pool_usage", new Object[]{35.0, "%"},
                    "thread_pool_usage", new Object[]{30.0, "%"},
                    "rt_p95", new Object[]{120.0, "ms"},
                    "qps", new Object[]{850.0, "req/s"}),
            2L, Map.of(
                    "cpu_usage", new Object[]{25.0, "%"},
                    "mem_usage", new Object[]{45.0, "%"},
                    "rt_p95", new Object[]{80.0, "ms"}),
            3L, Map.of(
                    "cpu_usage", new Object[]{38.0, "%"},
                    "mem_usage", new Object[]{52.0, "%"},
                    "conn_pool_usage", new Object[]{40.0, "%"}),
            4L, Map.of(
                    "cpu_usage", new Object[]{30.0, "%"},
                    "mem_usage", new Object[]{48.0, "%"}),
            5L, Map.of(
                    "cpu_usage", new Object[]{35.0, "%"},
                    "mem_usage", new Object[]{50.0, "%"},
                    "rt_p95", new Object[]{150.0, "ms"})
    );

    private final MetricMapper metricMapper;
    private final ServiceLogMapper logMapper;
    private final StringRedisTemplate redis;
    private final Random random = new Random();

    @Override
    public int collect(Long instanceId) {
        Map<String, Object[]> baseline = BASELINES.get(instanceId);
        if (baseline == null) {
            log.debug("实例 {} 未配置采集基线，跳过", instanceId);
            return 0;
        }
        LocalDateTime now = LocalDateTime.now();
        int count = 0;
        for (Map.Entry<String, Object[]> e : baseline.entrySet()) {
            String name = e.getKey();
            double base = ((Number) e.getValue()[0]).doubleValue();
            String unit = (String) e.getValue()[1];
            double value = simulate(name, base, now);

            Metric m = new Metric();
            m.setInstanceId(instanceId);
            m.setMetricName(name);
            m.setValue(BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP));
            m.setUnit(unit);
            m.setTs(now);
            metricMapper.insert(m);

            // 最新值写 Redis，供 Agent 工具快读
            redis.opsForValue().set(
                    CommonConstants.REDIS_METRIC_LATEST + instanceId + ":" + name,
                    String.format("%.2f", value), 10, TimeUnit.MINUTES);
            count++;
        }
        return count;
    }

    /**
     * 生成贴近真实的指标值：
     * 使用正弦波模拟业务周期性，加随机噪声，并对百分比类指标做上下界裁剪。
     */
    private double simulate(String metricName, double base, LocalDateTime now) {
        // 以 5 分钟为一个周期相位
        double phase = (now.getMinute() % 60) / 60.0 * 2 * Math.PI;
        double wave = Math.sin(phase) * base * 0.12;

        // 指标本身的噪声幅度不同
        double noiseRatio = switch (metricName) {
            case "rt_p95" -> 0.18;
            case "qps" -> 0.15;
            default -> 0.06;
        };
        double noise = (random.nextDouble() - 0.5) * base * noiseRatio * 2;

        double v = base + wave + noise;

        // 百分比类裁剪到 [0, 100]
        if (metricName.endsWith("_usage")) {
            return Math.max(0.5, Math.min(99.9, v));
        }
        return Math.max(0.0, v);
    }

    @Override
    public int collectAll() {
        int total = 0;
        for (Long instanceId : BASELINES.keySet()) {
            total += collect(instanceId);
        }
        log.debug("全量采集完成，共写入 {} 个指标点", total);
        return total;
    }

    @Override
    public List<Map<String, Object>> series(Long instanceId, String metricName, String range) {
        LocalDateTime from = parseRange(range);
        List<Map<String, Object>> rows = metricMapper.selectSeries(instanceId, metricName, from);
        // 规范化字段名，前端与 Agent 都按 time/value 读取
        List<Map<String, Object>> out = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("time", formatTs(row.get("ts")));
            m.put("value", row.get("value"));
            out.add(m);
        }
        return out;
    }

    /**
     * 统一格式化时间字段。
     * <p>MyBatis 返回的时间类型可能是 LocalDateTime、Timestamp 或 Date，
     * 取决于 JDBC 驱动与配置，这里逐一兼容，避免硬转换引发 ClassCastException。
     */
    private String formatTs(Object ts) {
        if (ts == null) {
            return null;
        }
        LocalDateTime ldt;
        if (ts instanceof LocalDateTime v) {
            ldt = v;
        } else if (ts instanceof java.sql.Timestamp v) {
            ldt = v.toLocalDateTime();
        } else if (ts instanceof java.util.Date v) {
            ldt = v.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime();
        } else if (ts instanceof java.time.OffsetDateTime v) {
            ldt = v.toLocalDateTime();
        } else {
            return String.valueOf(ts);
        }
        return ldt.format(DateTimeFormatter.ofPattern("HH:mm:ss"));
    }

    @Override
    public Map<String, Object> latest(Long instanceId) {
        List<Map<String, Object>> rows = metricMapper.selectLatest(instanceId);
        Map<String, Object> metrics = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            metrics.put(String.valueOf(row.get("metric_name")), row.get("value"));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("instanceId", instanceId);
        out.put("metrics", metrics);
        out.put("time", LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        return out;
    }

    @Override
    public List<Map<String, Object>> logs(Long instanceId, String range, int limit) {
        LocalDateTime from = parseRange(range);
        return logMapper.selectRecent(instanceId, from, Math.min(limit, 200));
    }

    @Override
    public void writePoint(Long instanceId, String metricName, double value, String unit) {
        Metric m = new Metric();
        m.setInstanceId(instanceId);
        m.setMetricName(metricName);
        m.setValue(BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP));
        m.setUnit(unit);
        m.setTs(LocalDateTime.now());
        metricMapper.insert(m);
        redis.opsForValue().set(
                CommonConstants.REDIS_METRIC_LATEST + instanceId + ":" + metricName,
                String.format("%.2f", value), 10, TimeUnit.MINUTES);
        log.info("手动写入指标 instanceId={} metric={} value={}", instanceId, metricName, value);
    }

    /**
     * 解析 30m / 2h / 1d 这类范围表达式。
     */
    private LocalDateTime parseRange(String range) {
        if (range == null || range.isBlank()) {
            return LocalDateTime.now().minusMinutes(30);
        }
        String r = range.trim().toLowerCase();
        try {
            if (r.endsWith("m")) {
                return LocalDateTime.now().minusMinutes(Long.parseLong(r.substring(0, r.length() - 1)));
            }
            if (r.endsWith("h")) {
                return LocalDateTime.now().minusHours(Long.parseLong(r.substring(0, r.length() - 1)));
            }
            if (r.endsWith("d")) {
                return LocalDateTime.now().minusDays(Long.parseLong(r.substring(0, r.length() - 1)));
            }
        } catch (NumberFormatException e) {
            log.warn("无法解析时间范围 {}，回退到 30 分钟", range);
        }
        return LocalDateTime.now().minusMinutes(30);
    }
}
