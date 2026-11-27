package com.aiops.agent.llm.impl;

import com.aiops.agent.llm.LlmProvider;
import com.aiops.agent.llm.LlmToolDecision;
import com.aiops.common.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mock 大模型实现（默认启用）。
 *
 * <p>没有 DeepSeek API Key 时，用「关键词规则 + 领域知识模板」模拟大模型的推理与工具调用，
 * 保证整条 Agent 链路可运行、可演示、可测试。接口契约与真实实现完全一致，
 * 配置 {@code aiops.llm.provider=deepseek} 即切换为真实调用。
 *
 * <p>虽然不调用真实模型，但**工具选择逻辑是真实执行的**：它同样会产出 tool_call 决策，
 * 由编排层实际调用 monitor/alert/knowledge 服务取数，因此「Agent 调用工具干活」的
 * 业务闭环与真实模型一致，差别仅在推理文本由模板生成。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "aiops.llm.provider", havingValue = "mock", matchIfMissing = true)
public class MockLlmProvider implements LlmProvider {

    /** 从提示词中提取告警类型 */
    private static final Pattern TYPE_PATTERN =
            Pattern.compile("告警类型[:：]\\s*([a-zA-Z_]+)");
    private static final Pattern INSTANCE_PATTERN =
            Pattern.compile("实例ID[:：]\\s*(\\d+)");

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public String complete(String systemPrompt, String userPrompt, boolean jsonMode) {
        log.debug("Mock LLM complete: jsonMode={} userPrompt={}", jsonMode, brief(userPrompt));
        String type = extractType(userPrompt);
        if (jsonMode) {
            return JsonUtils.toJson(guessJson(userPrompt, type));
        }
        return "【Mock 推理】针对 " + type + " 类告警：" + guessNarrative(userPrompt, type);
    }

    /**
     * 模拟 ReAct 决策：根据已进行的工具调用轮次，决定下一步调哪个工具或直接给结论。
     *
     * <p>策略与真人运维的排查顺序一致：
     * <ol>
     *   <li>先查监控指标（确认资源是否真的异常、程度如何）；</li>
     *   <li>再查历史告警（判断是否复发）；</li>
     *   <li>再查依赖拓扑（判断是否上下游传导）；</li>
     *   <li>最后检索知识库（复用既有处置经验）；</li>
     *   <li>信息充分后输出根因结论。</li>
     * </ol>
     */
    @Override
    public LlmToolDecision decide(String systemPrompt, String userPrompt,
                                  List<Map<String, Object>> tools) {
        String type = extractType(userPrompt);
        Long instanceId = extractInstanceId(userPrompt);
        int round = countRounds(userPrompt);

        log.debug("Mock LLM decide: type={} instanceId={} round={}", type, instanceId, round);

        String metric = metricOf(type);

        // 第 1 轮：查指标
        if (round == 0) {
            Map<String, Object> args = new LinkedHashMap<>();
            args.put("instance_id", instanceId == null ? 1L : instanceId);
            args.put("metric_name", metric);
            args.put("time_range", "30m");
            return LlmToolDecision.toolCall("query_metrics", args,
                    "先取 " + metric + " 的近期时序，确认指标是否真的越界以及峰值出现在什么时候。");
        }

        // 第 2 轮：查历史告警
        if (round == 1) {
            Map<String, Object> args = new LinkedHashMap<>();
            args.put("instance_id", instanceId == null ? 1L : instanceId);
            args.put("alert_type", type);
            args.put("limit", 5);
            return LlmToolDecision.toolCall("query_history_alerts", args,
                    "指标异常已确认，接下来判断这是首次发生还是复发型故障。");
        }

        // 第 3 轮：查依赖拓扑
        if (round == 2) {
            Map<String, Object> args = new LinkedHashMap<>();
            args.put("instance_id", instanceId == null ? 1L : instanceId);
            return LlmToolDecision.toolCall("query_service_dependency", args,
                    "需要区分根因在本服务自身还是由上下游依赖传导。");
        }

        // 第 4 轮：检索知识库
        if (round == 3) {
            Map<String, Object> args = new LinkedHashMap<>();
            args.put("keyword", keywordOf(type));
            args.put("category", categoryOf(type));
            args.put("limit", 3);
            return LlmToolDecision.toolCall("query_knowledge", args,
                    "信息已基本齐全，检索历史处置经验以复用成熟方案。");
        }

        // 第 5 轮：输出结论
        return LlmToolDecision.finalAnswer(
                JsonUtils.toJson(guessRcaConclusion(userPrompt, type)),
                "指标、历史、依赖、知识四条线索均已收集，可以给出根因结论。");
    }

    // ------------------------------------------------------------------
    // 内部：从提示词中做轻量解析
    // ------------------------------------------------------------------

    private String extractType(String prompt) {
        Matcher m = TYPE_PATTERN.matcher(prompt == null ? "" : prompt);
        if (m.find()) {
            return m.group(1).toLowerCase(Locale.ROOT);
        }
        String lower = prompt == null ? "" : prompt.toLowerCase(Locale.ROOT);
        if (lower.contains("cpu")) return "cpu";
        if (lower.contains("内存") || lower.contains("memory")) return "memory";
        if (lower.contains("连接池") || lower.contains("connection_pool")) return "connection_pool";
        if (lower.contains("磁盘") || lower.contains("disk")) return "disk";
        if (lower.contains("网络") || lower.contains("network")) return "network";
        if (lower.contains("线程池") || lower.contains("thread_pool")) return "thread_pool";
        if (lower.contains("数据库") || lower.contains("database")) return "database";
        if (lower.contains("mq") || lower.contains("队列")) return "mq";
        return "application";
    }

    private Long extractInstanceId(String prompt) {
        Matcher m = INSTANCE_PATTERN.matcher(prompt == null ? "" : prompt);
        if (m.find()) {
            try {
                return Long.valueOf(m.group(1));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        Pattern p2 = Pattern.compile("实例\\s*(\\d+)");
        Matcher m2 = p2.matcher(prompt == null ? "" : prompt);
        if (m2.find()) {
            try {
                return Long.valueOf(m2.group(1));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /**
     * 统计提示词中已有的「工具结果」条数，即已完成的轮次。
     */
    private int countRounds(String prompt) {
        if (prompt == null) {
            return 0;
        }
        int n = 0;
        int idx = 0;
        String marker = "【工具结果";
        while ((idx = prompt.indexOf(marker, idx)) >= 0) {
            n++;
            idx += marker.length();
        }
        return n;
    }

    private String metricOf(String type) {
        return switch (type) {
            case "cpu" -> "cpu_usage";
            case "memory" -> "mem_usage";
            case "disk" -> "disk_usage";
            case "connection_pool", "database" -> "conn_pool_usage";
            case "thread_pool" -> "thread_pool_usage";
            case "network" -> "rt_p95";
            default -> "cpu_usage";
        };
    }

    private String keywordOf(String type) {
        return switch (type) {
            case "cpu" -> "CPU 使用率过高";
            case "memory" -> "内存使用率过高 内存泄漏";
            case "connection_pool" -> "数据库连接池耗尽";
            case "database" -> "慢 SQL 数据库异常";
            case "disk" -> "磁盘 IO 空间不足";
            case "network" -> "网络延迟与丢包";
            case "thread_pool" -> "线程池耗尽 请求排队";
            case "mq" -> "MQ 消息堆积";
            default -> type;
        };
    }

    private String categoryOf(String type) {
        return switch (type) {
            case "cpu" -> "CPU";
            case "memory" -> "MEMORY";
            case "disk" -> "DISK";
            case "network" -> "NETWORK";
            case "connection_pool", "database" -> "DATABASE";
            case "mq" -> "MIDDLEWARE";
            default -> "APPLICATION";
        };
    }

    private String guessNarrative(String prompt, String type) {
        return "依据指标趋势与依赖关系判断，" + type + " 类异常需优先确认资源瓶颈点，再排查上下游传导。";
    }

    /**
     * 生成结构化 JSON（分类 / 根因 / 处置 / 报告各阶段复用同一入口）。
     *
     * <p>注意判断顺序：必须**先匹配「任务N」标记**，再匹配内容关键词。
     * 因为处置阶段与报告阶段的提示词里同样包含「分类结论」「根因结论」等字样，
     * 若先判断关键词会把它们误判为分类任务。
     */
    private Map<String, Object> guessJson(String prompt, String type) {
        Map<String, Object> m = new LinkedHashMap<>();
        String p = prompt == null ? "" : prompt;

        if (p.contains("任务四")) {
            m.put("title", "[" + levelOf(type) + "] " + categoryOf(type) + " 故障分析报告");
            m.put("content", reportOf(type));
            return m;
        }
        if (p.contains("任务三")) {
            m.put("steps", stepsOf(type));
            m.put("suggestion", suggestionOf(type));
            m.put("risk", riskOf(type));
            return m;
        }
        if (p.contains("任务二")) {
            return guessRcaConclusion(prompt, type);
        }
        if (p.contains("任务一")) {
            m.put("category", categoryOf(type));
            m.put("level", levelOf(type));
            m.put("summary", summaryOf(type));
            m.put("confidence", 0.86);
            return m;
        }

        // 无任务标记时的兜底：按内容猜测
        m.put("category", categoryOf(type));
        m.put("level", levelOf(type));
        m.put("summary", summaryOf(type));
        return m;
    }

    private String levelOf(String type) {
        return switch (type) {
            case "cpu", "memory", "connection_pool" -> "P0";
            case "disk", "network", "thread_pool", "database" -> "P1";
            default -> "P2";
        };
    }

    private String summaryOf(String type) {
        return switch (type) {
            case "cpu" -> "CPU 资源耗尽风险，可能导致接口响应变慢与请求堆积";
            case "memory" -> "内存持续增长，存在 OOM 与频繁 Full GC 风险";
            case "connection_pool" -> "数据库连接池耗尽，应用将无法获取连接，出现大面积请求失败";
            case "database" -> "数据库侧异常，影响所有依赖该库的业务";
            case "disk" -> "磁盘空间或 IO 瓶颈，日志与临时文件写入可能失败";
            case "network" -> "网络延迟或丢包，服务间调用超时率上升";
            case "thread_pool" -> "线程池耗尽，请求排队并触发拒绝策略";
            case "mq" -> "消息堆积，消费能力不足，业务出现延迟";
            default -> "应用层异常，需结合日志进一步定位";
        };
    }

    private List<String> stepsOf(String type) {
        return switch (type) {
            case "connection_pool" -> List.of(
                    "查询 MySQL processlist，定位长时间运行的慢 SQL 并优化、补索引",
                    "检查事务边界，避免在事务中调用远程服务导致连接长时间占用",
                    "确认连接是否正常归还，排查连接泄漏",
                    "临时调大连接池 maxActive 与数据库 max_connections 缓解",
                    "对上游突发流量在网关侧限流，避免继续压垮数据库");
            case "cpu" -> List.of(
                    "top -Hp 定位高 CPU 线程，转换为十六进制后 jstack 查线程栈",
                    "排查死循环、正则回溯、频繁 GC 等常见原因",
                    "检查是否突发流量，必要时限流或扩容",
                    "观察 GC 日志，必要时调整堆大小与 GC 参数");
            case "memory" -> List.of(
                    "jmap -histo 查看对象分布，定位异常增长的类型",
                    "dump 堆文件并用 MAT 分析支配树，确认泄漏引用链",
                    "为本地缓存设置容量上限与过期策略",
                    "临时重启实例缓解，并持续观察内存曲线");
            case "network" -> List.of(
                    "ping / mtr 确认丢包节点与链路质量",
                    "检查网卡带宽使用率与 DNS 解析耗时",
                    "对下游启用熔断降级，避免超时扩散",
                    "必要时切换备用线路");
            case "disk" -> List.of(
                    "du -sh 逐层定位大目录与异常大文件",
                    "清理过期日志与临时文件",
                    "配置日志滚动与保留天数",
                    "评估扩容磁盘或挂载新分区");
            case "thread_pool" -> List.of(
                    "查看线程池活跃数、队列长度与拒绝次数",
                    "定位阻塞调用点，增加超时或改为异步",
                    "调整核心线程数与队列容量",
                    "对下游增加熔断与降级");
            default -> List.of(
                    "查看服务日志定位异常堆栈",
                    "确认依赖服务健康状态",
                    "必要时重启实例并观察");
        };
    }

    private String suggestionOf(String type) {
        return "建议按以下顺序处置：" + String.join("；", stepsOf(type)) + "。处置后需观察指标 15 分钟确认恢复。";
    }

    private Map<String, Object> guessRcaConclusion(String prompt, String type) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("root_cause", rootCauseOf(type));
        m.put("confidence", 0.82);
        m.put("evidence", List.of(
                "监控指标显示相关资源使用率在告警时间窗口内显著越界",
                "依赖拓扑表明该资源为本服务的关键下游依赖",
                "历史告警记录显示同类问题存在复发情况"));
        m.put("impact", impactOf(type));
        m.put("next_action", "建议立即执行处置方案并持续观察 15 分钟");
        return m;
    }

    private String rootCauseOf(String type) {
        return switch (type) {
            case "connection_pool" -> "数据库连接池被慢 SQL 与长事务占满，连接无法及时归还，导致应用获取连接超时";
            case "cpu" -> "应用存在高 CPU 消耗逻辑（死循环/复杂计算/频繁 GC），叠加流量增长导致 CPU 打满";
            case "memory" -> "内存中存在未释放的长生命周期对象引用，或缓存未设上限，导致内存持续增长";
            case "network" -> "服务间网络链路质量下降（丢包/延迟抖动），或下游响应变慢导致调用超时";
            case "disk" -> "日志未轮转与临时文件堆积占满磁盘，写入失败并拖高 IO wait";
            case "thread_pool" -> "同步阻塞调用过多且下游响应慢，线程池被占满后新请求排队并触发拒绝";
            case "mq" -> "消费端处理能力不足或消费逻辑阻塞，消息生产速率持续高于消费速率";
            case "database" -> "数据库侧存在慢查询或锁等待，响应变慢并向上游传导";
            default -> "应用内部异常导致，需要结合服务日志进一步定位具体代码路径";
        };
    }

    private String impactOf(String type) {
        return switch (type) {
            case "connection_pool", "database" ->
                    "影响所有依赖数据库的接口，可能造成大面积 500 与订单失败";
            case "cpu", "memory" ->
                    "接口响应时间显著上升，严重时服务不可用";
            case "network" ->
                    "跨服务调用超时率上升，可能引发雪崩";
            default -> "影响本服务部分功能，需关注是否扩散";
        };
    }

    private String riskOf(String type) {
        return switch (type) {
            case "cpu", "memory" -> "中";
            case "connection_pool", "database" -> "高";
            default -> "低";
        };
    }

    /**
     * 生成 Markdown 格式的故障分析报告正文。
     */
    private String reportOf(String type) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 故障概述\n").append(summaryOf(type)).append("\n\n");
        sb.append("## 影响范围\n").append(impactOf(type)).append("\n\n");
        sb.append("## 根因结论\n").append(rootCauseOf(type)).append("\n\n");
        sb.append("## 处置过程\n");
        List<String> steps = stepsOf(type);
        for (int i = 0; i < steps.size(); i++) {
            sb.append(i + 1).append(". ").append(steps.get(i)).append("\n");
        }
        sb.append("\n## 后续改进建议\n");
        sb.append("- 针对该根因补充监控指标与告警阈值，做到提前发现\n");
        sb.append("- 将本次处置经验沉淀到知识库，供同类故障复用\n");
        sb.append("- 完善自动化处置预案，缩短 MTTR\n");
        return sb.toString();
    }

    private String brief(String s) {
        if (s == null) {
            return "";
        }
        return s.length() <= 120 ? s : s.substring(0, 120) + "...";
    }
}
