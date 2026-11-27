package com.aiops.alert.service.impl;

import com.aiops.alert.entity.AlertEvent;
import com.aiops.common.constant.CommonConstants;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 分析任务投递器。
 *
 * <p>提供两种可插拔的投递方式，对外接口一致，切换只改配置：
 * <ul>
 *   <li>{@code aiops.mq.enabled=false}（默认）：有界线程池异步调用 agent-service，
 *       效果等价于「不阻塞告警接入」，适用于未安装 MQ 的课程环境；</li>
 *   <li>{@code aiops.mq.enabled=true}：投递到 RabbitMQ {@code aiops.agent.analyze.queue}，
 *       由 agent-service 的消费者拉取处理，实现真正的削峰填谷与失败重投。</li>
 * </ul>
 *
 * <p>MQ 模式的价值在于解耦：告警接入的 TPS 不再受 Agent 分析耗时影响，
 * 突发告警风暴时任务在队列中排队而不是打满线程池。
 */
@Slf4j
@Component
public class AlertEventPublisher {

    private static final AtomicInteger SEQ = new AtomicInteger();

    private final RestTemplate restTemplate = new RestTemplate();
    private final ExecutorService executor;

    /** MQ 模板：仅在 aiops.mq.enabled=true 时容器内才有该 Bean，故用 ObjectProvider 惰性获取。 */
    private final ObjectProvider<RabbitTemplate> rabbitTemplateProvider;

    @Value("${aiops.mq.enabled:false}")
    private boolean mqEnabled;

    @Value("${aiops.agent.url:http://127.0.0.1:8084}")
    private String agentServiceUrl;

    public AlertEventPublisher(ObjectProvider<RabbitTemplate> rabbitTemplateProvider) {
        this.rabbitTemplateProvider = rabbitTemplateProvider;
        // 固定线程池承担削峰：告警风暴时任务排队，不会打满 Agent 服务
        this.executor = Executors.newFixedThreadPool(4, new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "alert-analyze-" + SEQ.incrementAndGet());
                t.setDaemon(true);
                return t;
            }
        });
    }

    /**
     * 投递分析任务。
     */
    public void publishAnalyze(AlertEvent event) {
        if (event == null || event.getId() == null) {
            return;
        }
        if (mqEnabled) {
            publishToMq(event);
            return;
        }
        executor.submit(() -> callAgent(event));
    }

    /**
     * MQ 模式：投递到 RabbitMQ，由 agent-service 消费。
     * <p>投递失败时降级为直接 HTTP 调用，保证分析任务不丢。
     */
    private void publishToMq(AlertEvent event) {
        RabbitTemplate rabbitTemplate = rabbitTemplateProvider.getIfAvailable();
        if (rabbitTemplate == null) {
            log.warn("已开启 MQ 但 RabbitTemplate 不可用，降级为直接调用 eventId={}", event.getId());
            executor.submit(() -> callAgent(event));
            return;
        }
        try {
            Map<String, Object> payload = Map.of(
                    "eventId", event.getId(),
                    "fingerprint", String.valueOf(event.getFingerprint()),
                    "instanceId", String.valueOf(event.getInstanceId()),
                    "alertType", String.valueOf(event.getAlertType()),
                    "level", String.valueOf(event.getLevel()));
            rabbitTemplate.convertAndSend(
                    CommonConstants.MQ_EXCHANGE_ALERT,
                    CommonConstants.MQ_ROUTING_AGENT_ANALYZE,
                    payload);
            log.info("已投递 MQ 分析任务 eventId={} exchange={} routingKey={}",
                    event.getId(), CommonConstants.MQ_EXCHANGE_ALERT,
                    CommonConstants.MQ_ROUTING_AGENT_ANALYZE);
        } catch (Exception e) {
            // MQ 不可用不应阻断告警接入主链路，降级为直连
            log.warn("MQ 投递失败，降级为直接调用 eventId={} msg={}", event.getId(), e.getMessage());
            executor.submit(() -> callAgent(event));
        }
    }

    private void callAgent(AlertEvent event) {
        long start = System.currentTimeMillis();
        try {
            String url = agentServiceUrl + "/agent/analyze/async/" + event.getId();
            restTemplate.postForEntity(url, null, String.class);
            log.info("已投递 Agent 分析 eventId={} cost={}ms",
                    event.getId(), System.currentTimeMillis() - start);
        } catch (Exception e) {
            // 投递失败不抛出：告警接入是主链路，分析可由补偿扫描重试
            log.warn("投递 Agent 分析失败 eventId={} msg={}", event.getId(), e.getMessage());
        }
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
        log.info("告警分析投递线程池已关闭");
    }
}
