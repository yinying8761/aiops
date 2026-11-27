package com.aiops.alert.service;

import com.aiops.alert.dto.LevelRule;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 告警分级器。
 * <p>规则优先级：显式上报的 level &gt; 阈值规则 &gt; 类型默认规则 &gt; P2 兜底。
 * 对应文档 1.4「告警的初始分级（P0/P1/P2/P3）」与 TC-04「上报 CPU 告警→定级 P1」。
 */
@Slf4j
@Component
@ConfigurationProperties(prefix = "aiops.alert")
public class LevelResolver {

    /** 阈值规则列表，可在 application.yml 中覆盖 */
    private List<LevelRule> rules = new ArrayList<>();

    @PostConstruct
    public void init() {
        if (rules.isEmpty()) {
            rules = defaultRules();
        }
        log.info("告警分级规则已加载，共 {} 条", rules.size());
    }

    /**
     * 推导告警级别。
     *
     * @param alertType   告警类型
     * @param declared    上报方声明的级别，为空则走规则
     * @param metricValue 指标值，可用于阈值判断
     */
    public String resolve(String alertType, String declared, Double metricValue) {
        // 1) 上报方显式指定优先
        if (declared != null && !declared.isBlank()) {
            return normalizeLevel(declared);
        }
        if (alertType == null) {
            return "P2";
        }
        String type = alertType.trim().toLowerCase(Locale.ROOT);

        // 2) 阈值规则
        for (LevelRule rule : rules) {
            if (rule.getAlertType() != null
                    && rule.getAlertType().equalsIgnoreCase(type)
                    && rule.getThreshold() != null
                    && metricValue != null
                    && metricValue >= rule.getThreshold()
                    && rule.getLevelOver() != null) {
                return normalizeLevel(rule.getLevelOver());
            }
        }

        // 3) 类型默认级别
        for (LevelRule rule : rules) {
            if (rule.getAlertType() != null && rule.getAlertType().equalsIgnoreCase(type)) {
                return normalizeLevel(rule.getLevelDefault());
            }
        }

        // 4) 兜底
        return "P2";
    }

    /**
     * 把各种写法的级别归一为 P0~P3。
     */
    public static String normalizeLevel(String level) {
        if (level == null) {
            return "P2";
        }
        String l = level.trim().toUpperCase(Locale.ROOT);
        return switch (l) {
            case "P0", "CRITICAL", "EMERGENCY", "紧急" -> "P0";
            case "P1", "ERROR", "MAJOR", "严重" -> "P1";
            case "P2", "WARN", "WARNING", "MINOR", "一般" -> "P2";
            case "P3", "INFO", "NOTICE", "提示" -> "P3";
            default -> "P2";
        };
    }

    private List<LevelRule> defaultRules() {
        List<LevelRule> list = new ArrayList<>();
        list.add(rule("cpu", 95.0, "P0", "P1"));
        list.add(rule("memory", 95.0, "P0", "P1"));
        list.add(rule("disk", 90.0, "P1", "P2"));
        list.add(rule("network", 80.0, "P1", "P2"));
        list.add(rule("database", null, null, "P1"));
        list.add(rule("connection_pool", 90.0, "P0", "P1"));
        list.add(rule("thread_pool", 90.0, "P1", "P1"));
        list.add(rule("mq", null, null, "P2"));
        list.add(rule("application", null, null, "P2"));
        list.add(rule("health", null, null, "P1"));
        return list;
    }

    private LevelRule rule(String type, Double threshold, String over, String def) {
        LevelRule r = new LevelRule();
        r.setAlertType(type);
        r.setThreshold(threshold);
        r.setLevelOver(over);
        r.setLevelDefault(def);
        return r;
    }

    public List<LevelRule> getRules() {
        return rules;
    }

    public void setRules(List<LevelRule> rules) {
        this.rules = rules;
    }
}
