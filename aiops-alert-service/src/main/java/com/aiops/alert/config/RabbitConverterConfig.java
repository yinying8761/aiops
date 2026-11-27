package com.aiops.alert.config;

import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 消息转换器：统一使用 JSON 序列化。
 *
 * <p>与 agent-service 的 {@code RabbitConverterConfig} 保持一致。
 * 若两端转换器不一致，消费端会按 Java 原生序列化解析而失败。
 */
@Configuration
@ConditionalOnProperty(name = "aiops.mq.enabled", havingValue = "true")
public class RabbitConverterConfig {

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
