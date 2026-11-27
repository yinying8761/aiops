package com.aiops.agent.config;

import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 消息转换器：统一使用 JSON 序列化。
 *
 * <p>Spring AMQP 默认的 SimpleMessageConverter 对非 String/byte[] 载荷使用 Java 原生序列化，
 * 消费端反序列化时会触发类白名单校验而失败（Attempt to deserialize unauthorized class）。
 * 改用 JSON 既规避该限制，也让消息可读、跨语言兼容。
 * 生产端与消费端必须使用同一种转换器。
 */
@Configuration
@ConditionalOnProperty(name = "aiops.mq.enabled", havingValue = "true")
public class RabbitConverterConfig {

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
