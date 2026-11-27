package com.aiops.demo.task;

import com.aiops.demo.domain.FaultModeHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Random;

/**
 * 故障指标上报任务。
 *
 * <p>当 demo-app 处于故障模式时，主动把恶化的指标写入 monitor-service，
 * 使监控曲线真实反映故障，进而触发告警。这样整条链路里的数据是自洽的，
 * 而不是直接往告警接口塞假数据。
 */
@Slf4j
@Component
public class FaultMetricReporter {

    /** demo-app 在 service_instance 表中的实例ID */
    private static final long INSTANCE_ID = 1L;

    private final RestTemplate restTemplate = new RestTemplate();
    private final Random random = new Random();

    @Value("${aiops.monitor.url:http://127.0.0.1:8083}")
    private String monitorUrl;

    /**
     * 每次采集周期（与 monitor 的 30 秒对齐）上报一次故障指标。
     */
    @Scheduled(fixedRate = 30_000, initialDelay = 8_000)
    public void report() {
        String mode = FaultModeHolder.get();
        if (mode == null || "none".equals(mode)) {
            return;
        }
        try {
            switch (mode) {
                case "cpu" -> writePoint("cpu_usage", 92 + random.nextDouble() * 7, "%");
                case "memory" -> writePoint("mem_usage", 93 + random.nextDouble() * 6, "%");
                case "connection_pool" -> {
                    writePoint("conn_pool_usage", 96 + random.nextDouble() * 3.9, "%");
                    writePoint("rt_p95", 1800 + random.nextDouble() * 900, "ms");
                }
                case "network" -> writePoint("rt_p95", 2000 + random.nextDouble() * 1500, "ms");
                case "thread_pool" -> writePoint("thread_pool_usage", 94 + random.nextDouble() * 5, "%");
                case "disk" -> writePoint("disk_usage", 92 + random.nextDouble() * 6, "%");
                default -> {
                    // 未知模式不上报
                }
            }
            log.info("已上报故障指标，模式={}", mode);
        } catch (Exception e) {
            log.warn("上报故障指标失败：{}", e.getMessage());
        }
    }

    private void writePoint(String metricName, double value, String unit) {
        double rounded = BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
        String url = monitorUrl + "/metrics/" + INSTANCE_ID + "/point"
                + "?metricName=" + metricName
                + "&value=" + rounded
                + "&unit=" + unit;
        restTemplate.postForEntity(url, null, String.class);
    }
}
