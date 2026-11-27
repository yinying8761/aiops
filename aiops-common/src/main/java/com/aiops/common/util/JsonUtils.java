package com.aiops.common.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.aiops.common.exception.BizException;
import com.aiops.common.api.ResultCode;

/**
 * JSON 工具。重点服务于大模型结构化输出的容错解析（详见 4.4「大模型返回格式不稳定」的解决方案）。
 */
public final class JsonUtils {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private JsonUtils() {
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }

    public static String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            throw new BizException(ResultCode.SYSTEM_ERROR, "JSON 序列化失败: " + e.getMessage());
        }
    }

    public static <T> T fromJson(String json, Class<T> clazz) {
        try {
            return MAPPER.readValue(json, clazz);
        } catch (Exception e) {
            throw new BizException(ResultCode.LLM_OUTPUT_INVALID, "JSON 反序列化失败: " + e.getMessage());
        }
    }

    public static <T> T fromJson(String json, TypeReference<T> typeRef) {
        try {
            return MAPPER.readValue(json, typeRef);
        } catch (Exception e) {
            throw new BizException(ResultCode.LLM_OUTPUT_INVALID, "JSON 反序列化失败: " + e.getMessage());
        }
    }

    /**
     * 从大模型返回的「脏文本」中提取第一段平衡的 JSON 对象。
     * <p>真实 LLM 常见输出：```json { ... } ``` 或 "好的，结果如下：{...} 希望有帮助"。
     * 这里做三重兜底：去代码围栏 → 找首个 '{' → 按括号深度截取平衡片段。
     *
     * @return 合法 JSON 字符串，找不到返回 null
     */
    public static String extractJsonObject(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String text = stripCodeFence(raw).trim();
        int start = text.indexOf('{');
        if (start < 0) {
            return null;
        }
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            switch (c) {
                case '"' -> inString = true;
                case '{' -> depth++;
                case '}' -> {
                    depth--;
                    if (depth == 0) {
                        String candidate = text.substring(start, i + 1);
                        return isValidJson(candidate) ? candidate : null;
                    }
                }
                default -> {
                    // 其它字符忽略
                }
            }
        }
        return null;
    }

    /**
     * 去掉 Markdown 代码围栏 ```json ... ```
     */
    public static String stripCodeFence(String raw) {
        String text = raw.trim();
        if (!text.startsWith("```")) {
            return text;
        }
        int firstNewline = text.indexOf('\n');
        if (firstNewline < 0) {
            return text.replace("```", "").trim();
        }
        String body = text.substring(firstNewline + 1);
        int endFence = body.lastIndexOf("```");
        if (endFence >= 0) {
            body = body.substring(0, endFence);
        }
        return body.trim();
    }

    public static boolean isValidJson(String text) {
        try {
            MAPPER.readTree(text);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static JsonNode readTree(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            throw new BizException(ResultCode.LLM_OUTPUT_INVALID, "JSON 解析失败: " + e.getMessage());
        }
    }

    public static ArrayNode createArrayNode() {
        return MAPPER.createArrayNode();
    }
}
