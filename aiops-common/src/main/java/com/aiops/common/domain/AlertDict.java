package com.aiops.common.domain;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 告警分级与分类的领域字典。与文档 1.4「初始分级（P0/P1/P2/P3）」一致。
 */
public final class AlertDict {

    private AlertDict() {
    }

    /** 告警级别 */
    public enum Level {
        P0("P0", "紧急", 1),
        P1("P1", "严重", 2),
        P2("P2", "一般", 3),
        P3("P3", "提示", 4);

        private final String code;
        private final String label;
        private final int weight;

        Level(String code, String label, int weight) {
            this.code = code;
            this.label = label;
            this.weight = weight;
        }

        public String getCode() {
            return code;
        }

        public String getLabel() {
            return label;
        }

        public int getWeight() {
            return weight;
        }

        /** 级别 → 建议响应时限（分钟），用于 MTTR 目标值展示 */
        public int getSlaMinutes() {
            return switch (this) {
                case P0 -> 15;
                case P1 -> 30;
                case P2 -> 120;
                case P3 -> 480;
            };
        }

        public static Level of(String code) {
            if (code == null) {
                return P2;
            }
            for (Level v : values()) {
                if (v.code.equalsIgnoreCase(code.trim())) {
                    return v;
                }
            }
            return P2;
        }
    }

    /** 告警分类：由分类 Agent 输出的 category 字段取值 */
    public enum Category {
        CPU("CPU", "CPU 使用率异常"),
        MEMORY("MEMORY", "内存使用率异常"),
        NETWORK("NETWORK", "网络异常"),
        DISK("DISK", "磁盘/AIO 异常"),
        APPLICATION("APPLICATION", "应用异常"),
        DATABASE("DATABASE", "数据库异常"),
        MIDDLEWARE("MIDDLEWARE", "中间件异常"),
        OTHER("OTHER", "其他");

        private final String code;
        private final String label;

        Category(String code, String label) {
            this.code = code;
            this.label = label;
        }

        public String getCode() {
            return code;
        }

        public String getLabel() {
            return label;
        }

        public static Category of(String code) {
            if (code == null) {
                return OTHER;
            }
            String c = code.trim().toUpperCase();
            for (Category v : values()) {
                if (v.code.equals(c) || v.label.equals(code)) {
                    return v;
                }
            }
            return OTHER;
        }
    }

    /** 告警状态 */
    public enum AlertStatus {
        NEW("NEW", "待处理"),
        ACK("ACK", "已确认"),
        ANALYZING("ANALYZING", "分析中"),
        HANDLING("HANDLING", "处置中"),
        RESOLVED("RESOLVED", "已解决"),
        CLOSED("CLOSED", "已关闭"),
        IGNORED("IGNORED", "已忽略");

        private final String code;
        private final String label;

        AlertStatus(String code, String label) {
            this.code = code;
            this.label = label;
        }

        public String getCode() {
            return code;
        }

        public String getLabel() {
            return label;
        }
    }

    /** 供前端下拉框使用的字典数据 */
    public static Map<String, Object> dict() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("levels", java.util.Arrays.stream(Level.values())
                .map(l -> Map.of("code", l.getCode(), "label", l.getLabel(),
                        "weight", l.getWeight(), "slaMinutes", l.getSlaMinutes()))
                .toList());
        map.put("categories", java.util.Arrays.stream(Category.values())
                .map(c -> Map.of("code", c.getCode(), "label", c.getLabel()))
                .toList());
        map.put("statuses", java.util.Arrays.stream(AlertStatus.values())
                .map(s -> Map.of("code", s.getCode(), "label", s.getLabel()))
                .toList());
        return map;
    }
}
