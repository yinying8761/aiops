package com.aiops.alert.config;

import com.aiops.common.constant.CommonConstants;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 拓扑声明。
 *
 * <p>仅在 {@code aiops.mq.enabled=true} 时生效，默认关闭以保证未安装 MQ 的环境也能启动。
 * 使用 Direct 交换机按路由键精确投递，队列声明为持久化，服务重启不丢消息。
 */
@Configuration
@ConditionalOnProperty(name = "aiops.mq.enabled", havingValue = "true")
public class RabbitMqConfig {

    /** 告警分析任务交换机（Direct，持久化）。 */
    @Bean
    public DirectExchange alertExchange() {
        return new DirectExchange(CommonConstants.MQ_EXCHANGE_ALERT, true, false);
    }

    /**
     * 分析任务队列。
     * <p>开启死信：消费失败重试耗尽后进入死信队列，避免任务静默丢失。
     */
    @Bean
    public Queue agentAnalyzeQueue() {
        return QueueBuilder.durable(CommonConstants.MQ_QUEUE_AGENT_ANALYZE)
                .deadLetterExchange(CommonConstants.MQ_EXCHANGE_ALERT)
                .deadLetterRoutingKey(CommonConstants.MQ_ROUTING_AGENT_ANALYZE + ".dlq")
                .build();
    }

    @Bean
    public Binding agentAnalyzeBinding() {
        return BindingBuilder.bind(agentAnalyzeQueue())
                .to(alertExchange())
                .with(CommonConstants.MQ_ROUTING_AGENT_ANALYZE);
    }

    /** 死信队列：保存反复失败的分析任务，便于人工排查。 */
    @Bean
    public Queue agentAnalyzeDlq() {
        return QueueBuilder.durable(CommonConstants.MQ_QUEUE_AGENT_ANALYZE + ".dlq").build();
    }

    @Bean
    public Binding agentAnalyzeDlqBinding() {
        return BindingBuilder.bind(agentAnalyzeDlq())
                .to(alertExchange())
                .with(CommonConstants.MQ_ROUTING_AGENT_ANALYZE + ".dlq");
    }
}
