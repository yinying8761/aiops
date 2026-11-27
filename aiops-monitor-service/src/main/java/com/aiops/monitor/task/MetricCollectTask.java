package com.aiops.monitor.task;

import com.aiops.monitor.mapper.MetricMapper;
import com.aiops.monitor.service.MetricService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 指标采集定时任务。
 * <p>对应文档 4.2「monitor-service：定时采集模拟业务系统的 CPU/内存/连接数等指标」。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MetricCollectTask {

    private final MetricService metricService;
    private final MetricMapper metricMapper;

    /**
     * 每 30 秒采集一次全部实例指标。
     * 采集间隔与 5 分钟聚合窗口配合，保证每个窗口内有足够样本点供 Agent 分析。
     */
    @Scheduled(fixedRate = 30_000, initialDelay = 5_000)
    public void collect() {
        try {
            int n = metricService.collectAll();
            log.debug("定时采集完成，写入 {} 个指标点", n);
        } catch (Exception e) {
            log.error("定时采集失败", e);
        }
    }

    /**
     * 每天凌晨 3 点清理 7 天前的指标，防止表无限膨胀。
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanup() {
        try {
            int n = metricMapper.deleteBefore(LocalDateTime.now().minusDays(7));
            log.info("清理 7 天前指标 {} 条", n);
        } catch (Exception e) {
            log.error("指标清理失败", e);
        }
    }
}
