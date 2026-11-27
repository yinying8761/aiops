package com.aiops.agent.config;

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
 * Agent 侧 RabbitMQ 拓扑声明。
 *
 * <p>生产端（alert-service）与消费端（agent-service）各自声明一次同样的拓扑，
 * 这样无论哪个服务先启动，队列都已存在，消费者不会因队列缺失而启动失败。
 * RabbitMQ 的声明是幂等的，重复声明只要参数一致即安全。
 */
@Configuration
@ConditionalOnProperty(name = "aiops.mq.enabled", havingValue = "true")
public class AgentMqConfig {

    @Bean
    public DirectExchange alertExchange() {
        return new DirectExchange(CommonConstants.MQ_EXCHANGE_ALERT, true, false);
    }

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
