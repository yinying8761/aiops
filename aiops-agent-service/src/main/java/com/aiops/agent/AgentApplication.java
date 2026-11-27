package com.aiops.agent;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

/**
 * 多 Agent 编排、Tool Calling、DeepSeek 接入、SSE 流式输出（核心服务）。
 *
 * <p>关于 Spring AI 自动配置：OpenAI starter 在启动时就要求 api-key 有效。
 * 为了在无 Key 的环境（默认 mock 模式）也能正常启动，这里用两个互斥的配置类控制
 * 是否导入 Spring AI 的自动配置。设 {@code aiops.llm.provider=deepseek} 并配置
 * {@code DEEPSEEK_API_KEY} 后，才会真正初始化大模型客户端。
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.aiops")
@ComponentScan(basePackages = {"com.aiops"})
@MapperScan("com.aiops.agent.mapper")
public class AgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentApplication.class, args);
    }

    /**
     * Mock 模式：排除 OpenAI 自动配置，避免因缺少可用 api-key 而启动失败。
     */
    @org.springframework.context.annotation.Configuration
    @ConditionalOnProperty(name = "aiops.llm.provider", havingValue = "mock", matchIfMissing = true)
    @org.springframework.boot.autoconfigure.EnableAutoConfiguration(exclude = {
            org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration.class,
            org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration.class,
            org.springframework.ai.model.openai.autoconfigure.OpenAiImageAutoConfiguration.class,
            org.springframework.ai.model.openai.autoconfigure.OpenAiAudioSpeechAutoConfiguration.class,
            org.springframework.ai.model.openai.autoconfigure.OpenAiAudioTranscriptionAutoConfiguration.class,
            org.springframework.ai.model.openai.autoconfigure.OpenAiModerationAutoConfiguration.class
    })
    static class MockLlmConfiguration {
    }
}
