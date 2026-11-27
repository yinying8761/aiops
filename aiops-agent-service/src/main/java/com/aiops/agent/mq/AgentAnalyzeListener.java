package com.aiops.agent.mq;

import com.aiops.agent.service.AgentOrchestrator;
import com.aiops.common.constant.CommonConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 告警分析任务消费者。
 *
 * <p>仅在 {@code aiops.mq.enabled=true} 时注册，与 alert-service 的
 * {@code AlertEventPublisher} 生产端配对，构成 RabbitMQ 异步分析链路：
 * <pre>
 * alert-service ──投递──▶ aiops.agent.analyze.queue ──消费──▶ agent-service 四段式分析
 * </pre>
 *
 * <p>消费语义为手动确认（auto 模式）：方法正常返回才 ACK，抛异常则按
 * {@code spring.rabbitmq.listener.simple.retry} 配置重试，重试耗尽后进入死信队列。
 * 因此分析任务不会因瞬时故障静默丢失。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "aiops.mq.enabled", havingValue = "true")
public class AgentAnalyzeListener {

    private final AgentOrchestrator orchestrator;

    @RabbitListener(queues = CommonConstants.MQ_QUEUE_AGENT_ANALYZE)
    public void onAnalyzeTask(Map<String, Object> payload) {
        Object raw = payload == null ? null : payload.get("eventId");
        if (raw == null) {
            log.warn("收到缺少 eventId 的分析任务，已丢弃 payload={}", payload);
            return;
        }
        Long eventId;
        try {
            eventId = Long.valueOf(String.valueOf(raw));
        } catch (NumberFormatException e) {
            log.warn("分析任务 eventId 非法，已丢弃 payload={}", payload);
            return;
        }

        long start = System.currentTimeMillis();
        log.info("MQ 消费分析任务 eventId={} instanceId={} level={}",
                eventId, payload.get("instanceId"), payload.get("level"));
        try {
            // analyzeAsync 内部已做异常兜底；此处同步等待以便把失败暴露给重试机制
            orchestrator.analyze(eventId);
            log.info("MQ 分析任务完成 eventId={} cost={}ms",
                    eventId, System.currentTimeMillis() - start);
        } catch (Exception e) {
            // 抛出以触发 RabbitMQ 重试 → 死信，避免任务被静默吞掉
            log.error("MQ 分析任务失败 eventId={} cost={}ms",
                    eventId, System.currentTimeMillis() - start, e);
            throw e;
        }
    }
}
