package com.aiops.demo.controller;

import com.aiops.demo.domain.FaultModeHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 被监控的模拟订单系统。
 *
 * <p>作用（对应文档 2.1「被监控系统：demo-app-service，模拟订单系统，制造告警，作为被运维对象」）：
 * 对外提供订单接口，同时可通过 {@code /demo/fault} 注入故障模式，
 * 让 monitor-service 采集到异常指标，进而触发告警 → 聚合 → Agent 分析 → 工单 的完整闭环。
 */
@Slf4j
@RestController
@RequestMapping("/demo")
public class OrderController {

    private final AtomicLong orderSeq = new AtomicLong(10000);

    /**
     * 下单接口。正常时快速返回；注入故障后模拟变慢/失败。
     */
    @PostMapping("/orders")
    public Map<String, Object> createOrder(@RequestParam(defaultValue = "1") int quantity) {
        long orderId = orderSeq.incrementAndGet();
        String mode = FaultModeHolder.get();

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("orderId", "ORD" + orderId);
        resp.put("quantity", quantity);
        resp.put("faultMode", mode);

        if ("connection_pool".equals(mode)) {
            log.error("无法获取数据库连接：CannotGetJdbcConnectionException，连接池已耗尽");
            resp.put("status", "FAILED");
            resp.put("message", "系统繁忙，请稍后重试（数据库连接池耗尽）");
            return resp;
        }
        if ("thread_pool".equals(mode)) {
            log.warn("线程池队列已满，请求排队等待");
            sleep(300);
            resp.put("status", "SLOW");
            resp.put("message", "处理较慢（线程池排队）");
            return resp;
        }
        if ("cpu".equals(mode) || "memory".equals(mode)) {
            // 模拟高负载时的额外计算耗时
            sleep(120);
            resp.put("status", "SUCCESS");
            resp.put("message", "下单成功（响应偏慢）");
            return resp;
        }
        resp.put("status", "SUCCESS");
        resp.put("message", "下单成功");
        return resp;
    }

    /**
     * 查询订单。
     */
    @GetMapping("/orders/{orderId}")
    public Map<String, Object> getOrder(@PathVariable String orderId) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("orderId", orderId);
        resp.put("status", "SUCCESS");
        resp.put("faultMode", FaultModeHolder.get());
        return resp;
    }

    /**
     * 注入故障。演示「制造告警」的入口。
     *
     * @param mode      none / cpu / memory / connection_pool / network / thread_pool
     * @param intensity 0.0 ~ 1.0
     */
    @PostMapping("/fault")
    public Map<String, Object> injectFault(
            @RequestParam(defaultValue = "none") String mode,
            @RequestParam(defaultValue = "0.9") double intensity) {
        FaultModeHolder.set(mode, intensity);
        log.warn("故障模式已切换为 {}，强度 {}", mode, intensity);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("faultMode", FaultModeHolder.get());
        resp.put("intensity", FaultModeHolder.intensity());
        resp.put("message", FaultModeHolder.isHealthy() ? "已恢复正常" : "故障已注入，等待告警产生");
        return resp;
    }

    /**
     * 当前故障状态。
     */
    @GetMapping("/fault")
    public Map<String, Object> faultState() {
        return Map.of("faultMode", FaultModeHolder.get(),
                "intensity", FaultModeHolder.intensity());
    }

    /**
     * 健康检查。
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("status", FaultModeHolder.isHealthy() ? "UP" : "DEGRADED");
        resp.put("faultMode", FaultModeHolder.get());
        return resp;
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
