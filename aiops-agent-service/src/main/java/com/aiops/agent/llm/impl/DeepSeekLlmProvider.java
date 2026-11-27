package com.aiops.agent.llm.impl;

import com.aiops.agent.llm.LlmProvider;
import com.aiops.agent.llm.LlmToolDecision;
import com.aiops.common.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于 Spring AI 的真实大模型实现（DeepSeek）。
 *
 * <p>启用方式：在配置中设置
 * <pre>
 * aiops:
 *   llm:
 *     provider: deepseek
 * spring:
 *   ai:
 *     openai:
 *       api-key: ${DEEPSEEK_API_KEY}
 *       base-url: https://api.deepseek.com
 *       chat:
 *         options:
 *           model: deepseek-chat
 *           temperature: 0.2
 * </pre>
 * DeepSeek 提供 OpenAI 兼容接口，因此直接复用 Spring AI 的 OpenAI starter。
 *
 * <p>容错（对应文档 4.4「DeepSeek API 调用失败」与「大模型返回格式不稳定」）：
 * <ul>
 *   <li>指数退避重试；</li>
 *   <li>JSON 解析失败时用 JsonUtils 做平衡括号截取；</li>
 *   <li>多次失败后降级到 Mock，保证主链路不中断。</li>
 * </ul>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "aiops.llm.provider", havingValue = "deepseek")
public class DeepSeekLlmProvider implements LlmProvider {

    private final ChatClient chatClient;
    private final MockLlmProvider fallback;

    /** 最大重试次数 */
    private static final int MAX_RETRY = 3;

    public DeepSeekLlmProvider(ChatModel chatModel, MockLlmProvider fallback) {
        this.chatClient = ChatClient.builder(chatModel).build();
        this.fallback = fallback;
        log.info("已启用 DeepSeek 真实大模型（Spring AI）");
    }

    @Override
    public String name() {
        return "deepseek";
    }

    @Override
    public String complete(String systemPrompt, String userPrompt, boolean jsonMode) {
        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            try {
                String content = chatClient.prompt()
                        .system(systemPrompt)
                        .user(userPrompt)
                        .call()
                        .content();
                if (content == null || content.isBlank()) {
                    throw new IllegalStateException("模型返回为空");
                }
                if (jsonMode) {
                    String json = JsonUtils.extractJsonObject(content);
                    if (json == null) {
                        throw new IllegalStateException("模型未返回合法 JSON: " + brief(content));
                    }
                    return json;
                }
                return content;
            } catch (Exception e) {
                long backoff = (long) Math.pow(2, attempt - 1) * 500L;
                log.warn("DeepSeek 调用失败（第 {}/{} 次），{}ms 后重试：{}",
                        attempt, MAX_RETRY, backoff, e.getMessage());
                if (attempt == MAX_RETRY) {
                    log.error("DeepSeek 连续失败，降级到 Mock 实现", e);
                    return fallback.complete(systemPrompt, userPrompt, jsonMode);
                }
                sleep(backoff);
            }
        }
        return fallback.complete(systemPrompt, userPrompt, jsonMode);
    }

    @Override
    public LlmToolDecision decide(String systemPrompt, String userPrompt,
                                  List<Map<String, Object>> tools) {
        // 真实实现：让模型以 JSON 形式返回决策，避免依赖各家的 function calling 差异
        String toolDesc = buildToolDescription(tools);
        String decisionPrompt = userPrompt + """

                【可用工具】
                """ + toolDesc + """

                【输出要求】
                请只输出一个 JSON 对象，不要输出任何解释文字。两种形态之一：
                1) 需要调用工具：
                   {"type":"TOOL_CALL","thought":"为什么要调这个工具","tool_name":"工具名","tool_args":{...}}
                2) 信息已足够，给出结论：
                   {"type":"FINAL","thought":"结论依据","final_answer":"JSON 字符串形式的结论"}

                【约束】
                - 每轮最多调用一个工具；
                - 已调用过的工具不要用相同参数重复调用；
                - tool_args 必须是合法 JSON 对象，数值型参数用数字而非字符串。
                """;

        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            try {
                String content = chatClient.prompt()
                        .system(systemPrompt)
                        .user(decisionPrompt)
                        .call()
                        .content();
                String json = JsonUtils.extractJsonObject(content);
                if (json == null) {
                    throw new IllegalStateException("未返回合法 JSON: " + brief(content));
                }
                Map<String, Object> m = JsonUtils.fromJson(json, Map.class);
                LlmToolDecision d = parseDecision(m);
                if (d != null) {
                    return d;
                }
                throw new IllegalStateException("决策字段不完整: " + json);
            } catch (Exception e) {
                long backoff = (long) Math.pow(2, attempt - 1) * 500L;
                log.warn("DeepSeek 决策失败（第 {}/{} 次）：{}", attempt, MAX_RETRY, e.getMessage());
                if (attempt == MAX_RETRY) {
                    log.error("DeepSeek 决策连续失败，降级到 Mock", e);
                    return fallback.decide(systemPrompt, userPrompt, tools);
                }
                sleep(backoff);
            }
        }
        return fallback.decide(systemPrompt, userPrompt, tools);
    }

    @SuppressWarnings("unchecked")
    private LlmToolDecision parseDecision(Map<String, Object> m) {
        Object typeObj = m.get("type");
        String type = typeObj == null ? "" : typeObj.toString().toUpperCase();
        Object thought = m.get("thought");

        if ("TOOL_CALL".equals(type)) {
            Object nameObj = m.get("tool_name");
            if (nameObj == null) {
                return null;
            }
            Object argsObj = m.get("tool_args");
            Map<String, Object> args = argsObj instanceof Map<?, ?> mm
                    ? new LinkedHashMap<>((Map<String, Object>) mm)
                    : new LinkedHashMap<>();
            return LlmToolDecision.toolCall(nameObj.toString(), args,
                    thought == null ? "" : thought.toString());
        }
        if ("FINAL".equals(type)) {
            Object answer = m.get("final_answer");
            if (answer == null) {
                return null;
            }
            String ans = answer instanceof String s ? s : JsonUtils.toJson(answer);
            return LlmToolDecision.finalAnswer(ans, thought == null ? "" : thought.toString());
        }
        return null;
    }

    private String buildToolDescription(List<Map<String, Object>> tools) {
        if (tools == null || tools.isEmpty()) {
            return "（无可用工具）";
        }
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> t : tools) {
            Object fn = t.get("function");
            if (fn instanceof Map<?, ?> fm) {
                sb.append("- ").append(fm.get("name")).append("：")
                        .append(fm.get("description")).append("\n")
                        .append("  参数：").append(JsonUtils.toJson(fm.get("parameters"))).append("\n");
            }
        }
        return sb.toString();
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String brief(String s) {
        if (s == null) {
            return "";
        }
        return s.length() <= 200 ? s : s.substring(0, 200) + "...";
    }
}
