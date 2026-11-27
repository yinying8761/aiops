package com.aiops.common.util;

import com.aiops.common.constant.CommonConstants;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 告警指纹工具，对应文档 2.5「告警聚合去重算法」第 2 步：
 * fingerprint = MD5(service + "|" + type + "|" + 时间窗口(ts, 5min))
 */
public final class FingerprintUtils {

    private FingerprintUtils() {
    }

    /**
     * 计算告警指纹。
     *
     * @param instanceId 服务实例ID（归一化后的 service 字段）
     * @param alertType  告警类型（归一化后的 type 字段）
     * @param timestamp  告警发生时间（毫秒）
     * @param windowMin  时间窗口（分钟）
     */
    public static String fingerprint(Long instanceId, String alertType, long timestamp, int windowMin) {
        long windowMillis = windowMin * 60_000L;
        long windowStart = (timestamp / windowMillis) * windowMillis;
        String raw = instanceId + "|" + normalize(alertType) + "|" + windowStart;
        return md5Hex(raw);
    }

    /**
     * 使用默认 5 分钟窗口计算指纹。
     */
    public static String fingerprint(Long instanceId, String alertType, long timestamp) {
        return fingerprint(instanceId, alertType, timestamp, CommonConstants.ALERT_AGG_WINDOW_MINUTES);
    }

    /**
     * 归一化告警类型：去空格、转小写，避免 "CPU " 与 "cpu" 被当成两类。
     */
    public static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase().replaceAll("\\s+", "_");
    }

    public static String md5Hex(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(32);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JDK 未提供 MD5", e);
        }
    }
}
