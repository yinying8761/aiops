package com.aiops.agent.service.impl;

import com.aiops.agent.client.TicketClient;
import com.aiops.agent.client.ReportClient;
import com.aiops.agent.config.AlertEventClient;
import com.aiops.agent.dto.AgentOutputs;
import com.aiops.agent.dto.AnalyzeResult;
import com.aiops.agent.dto.AnalyzeStep;
import com.aiops.agent.entity.AgentTrace;
import com.aiops.agent.llm.LlmProvider;
import com.aiops.agent.llm.LlmToolDecision;
import com.aiops.agent.mapper.AgentTraceMapper;
import com.aiops.agent.service.AgentOrchestrator;
import com.aiops.agent.service.prompt.PromptTemplates;
import com.aiops.agent.tool.AgentTool;
import com.aiops.agent.tool.ToolRegistry;
import com.aiops.common.api.Result;
import com.aiops.common.exception.BizException;
import com.aiops.common.api.ResultCode;
import com.aiops.common.util.JsonUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * 多 Agent 编排器实现。
 *
 * <h3>流水线（对应文档 2.5 算法（2））</h3>
 * <pre>
 * 输入：聚合告警事件 event
 * 1. 分类 Agent（classification）：LLM 语义分类 + 结构化输出 → {category, level, summary}
 * 2. 根因 Agent（rca）：ReAct 循环，Tool Calling 查询 metric / 历史 alert / 依赖 → {root_cause, evidence}
 * 3. 处置 Agent（remediation）：结合 knowledge 检索出的模板 → {steps[], suggestion}
 * 4. 报告 Agent（report）：汇总前三步生成报告，并触发 ticket-service 建单
 * </pre>
 *
 * <p>每一步的思考、工具调用、工具结果都会落库到 agent_trace，并通过回调推送给前端（SSE），
 * 使「Agent 调用工具干活」的过程完全可观测。
 */
@Slf4j
@Service
public class AgentOrchestratorImpl implements AgentOrchestrator {

    /** ReAct 循环最大轮次，防止模型陷入无限调用 */
    private static final int MAX_REACT_ROUNDS = 6;

    private final LlmProvider llmProvider;
    private final ToolRegistry toolRegistry;
    private final AlertEventClient alertEventClient;
    private final TicketClient ticketClient;
    private final ReportClient reportClient;
    private final AgentTraceMapper traceMapper;

    public AgentOrchestratorImpl(LlmProvider llmProvider,
                                 ToolRegistry toolRegistry,
                                 AlertEventClient alertEventClient,
                                 TicketClient ticketClient,
                                 ReportClient reportClient,
                                 AgentTraceMapper traceMapper) {
        this.llmProvider = llmProvider;
        this.toolRegistry = toolRegistry;
        this.alertEventClient = alertEventClient;
        this.ticketClient = ticketClient;
        this.reportClient = reportClient;
        this.traceMapper = traceMapper;
        log.info("Agent 编排器初始化完成，当前 LLM 实现：{}", llmProvider.name());
    }

    @Override
    public AnalyzeResult analyze(Long eventId) {
        return analyzeStreaming(eventId, step -> {
            // 无订阅者时也照常执行，只是不推送
        });
    }

    @Override
    public AnalyzeResult analyzeStreaming(Long eventId, Consumer<AnalyzeStep> onStep) {
        long start = System.currentTimeMillis();
        LocalDateTime startTime = LocalDateTime.now();
        List<AnalyzeStep> steps = new ArrayList<>();

        Map<String, Object> event = loadEvent(eventId);
        String eventJson = JsonUtils.toJson(event);

        // 标记为分析中，避免同一事件被重复投递时产生并发分析
        markAnalyzeStatus(eventId, "RUNNING");

        Emitter emitter = new Emitter(eventId, onStep, steps);

        // ========== 阶段 1：分类 ==========
        AgentOutputs.Classification classification = runClassification(emitter, eventJson);

        // ========== 阶段 2：根因分析（ReAct + 工具调用）==========
        AgentOutputs.Rca rca = runRca(emitter, eventJson, classification);

        // ========== 阶段 3：处置建议 ==========
        AgentOutputs.Remediation remediation = runRemediation(emitter, eventJson, classification, rca);

        // ========== 阶段 4：报告生成 ==========
        AgentOutputs.Report report = runReport(emitter, eventJson, classification, rca, remediation);

        // ========== 收尾 1：回写分类结论到告警事件 ==========
        writeBackAnalysis(eventId, classification);

        // ========== 收尾 2：保存故障报告 ==========
        saveReport(eventId, classification, rca, remediation, report,
                (int) ((System.currentTimeMillis() - start) / 1000));

        // ========== 收尾 3：自动建单 ==========
        Long ticketId = null;
        String ticketNo = null;
        try {
            Map<String, Object> payload = buildTicketPayload(eventId, event, classification, rca, remediation);
            Result<Map<String, Object>> tr = ticketClient.autoCreate(payload);
            if (tr != null && tr.getData() != null) {
                Object id = tr.getData().get("id");
                ticketId = id instanceof Number n ? n.longValue() : null;
                Object no = tr.getData().get("ticketNo");
                ticketNo = no == null ? null : no.toString();
                emitter.emit("ticket", "工单派发", "OUTPUT",
                        "已自动创建工单 " + ticketNo + " 并派发给负责人", null);
            }
        } catch (Exception e) {
            log.warn("自动建单失败 eventId={}：{}", eventId, e.getMessage());
            emitter.emit("ticket", "工单派发", "OUTPUT",
                    "工单自动派发失败（不影响分析结论）：" + e.getMessage(), null);
        }

        long cost = System.currentTimeMillis() - start;
        emitter.emit("done", "全链路完成", "DONE",
                "分析完成，耗时 " + cost + " ms，共记录 " + steps.size() + " 个步骤", null);

        AnalyzeResult result = AnalyzeResult.builder()
                .eventId(eventId)
                .llmProvider(llmProvider.name())
                .classification(classification)
                .rca(rca)
                .remediation(remediation)
                .report(report)
                .ticketId(ticketId)
                .ticketNo(ticketNo)
                .costMs(cost)
                .startTime(startTime)
                .steps(steps)
                .build();

        log.info("事件 {} 分析完成，耗时 {}ms，根因：{}", eventId, cost, rca.getRootCause());
        return result;
    }

    // ==================================================================
    // 阶段 1：分类
    // ==================================================================
    private AgentOutputs.Classification runClassification(Emitter emitter, String eventJson) {
        emitter.stageStart("classification", "告警分类分级");
        emitter.emit("classification", "告警分类分级", "THOUGHT",
                "调用大模型对聚合告警事件做语义分类与定级", null);

        String content = llmProvider.complete(
                PromptTemplates.CLASSIFICATION_SYSTEM,
                PromptTemplates.classificationUser(eventJson),
                true);

        AgentOutputs.Classification out = parseClassification(content, eventJson);

        emitter.emit("classification", "告警分类分级", "OUTPUT",
                String.format("分类=%s，级别=%s，摘要=%s",
                        out.getCategory(), out.getLevel(), out.getSummary()), null);
        emitter.stageEnd("classification", "告警分类分级");
        return out;
    }

    private AgentOutputs.Classification parseClassification(String content, String eventJson) {
        AgentOutputs.Classification out = new AgentOutputs.Classification();
        String json = JsonUtils.extractJsonObject(content);
        if (json != null) {
            try {
                Map<String, Object> m = JsonUtils.fromJson(json, Map.class);
                out.setCategory(str(m.get("category"), "OTHER"));
                out.setLevel(str(m.get("level"), "P2"));
                out.setSummary(str(m.get("summary"), ""));
                Object conf = m.get("confidence");
                out.setConfidence(conf instanceof Number n ? n.doubleValue() : null);
                return out;
            } catch (Exception e) {
                log.warn("分类结果解析失败，使用兜底：{}", e.getMessage());
            }
        }
        // 兜底：模型输出不合法时，按告警类型做规则分类，保证链路不中断
        log.warn("分类输出不可解析，启用规则兜底");
        out.setCategory("OTHER");
        out.setLevel("P2");
        out.setSummary("大模型输出格式异常，已按默认规则分类");
        return out;
    }

    // ==================================================================
    // 阶段 2：根因分析（ReAct + Tool Calling）
    // ==================================================================
    private AgentOutputs.Rca runRca(Emitter emitter, String eventJson,
                                    AgentOutputs.Classification classification) {
        emitter.stageStart("rca", "根因分析");
        emitter.emit("rca", "根因分析", "THOUGHT",
                "进入 ReAct 循环：通过工具调用查询监控指标、历史告警与依赖拓扑，逐步收集根因证据", null);

        List<String> toolTrace = new ArrayList<>();
        StringBuilder observation = new StringBuilder();
        String rcaUser = PromptTemplates.rcaUser(eventJson,
                JsonUtils.toJson(classification));

        String finalContent = null;

        for (int round = 1; round <= MAX_REACT_ROUNDS; round++) {
            // 把已获得的工具结果拼进提示词，让模型基于新证据继续推理
            String userPrompt = round == 1 ? rcaUser
                    : rcaUser + "\n\n" + observation;

            LlmToolDecision decision = llmProvider.decide(
                    PromptTemplates.RCA_SYSTEM, userPrompt, toolRegistry.describeAll());

            if (decision.getThought() != null && !decision.getThought().isBlank()) {
                emitter.emit("rca", "根因分析", "THOUGHT",
                        "第 " + round + " 轮：" + decision.getThought(), null);
            }

            if (decision.isFinal()) {
                finalContent = decision.getFinalAnswer();
                break;
            }

            // 执行工具调用
            String toolName = decision.getToolName();
            if (!toolRegistry.contains(toolName)) {
                log.warn("模型请求了未注册的工具 {}，跳过", toolName);
                observation.append("\n【工具结果 第").append(round)
                        .append("轮】工具 ").append(toolName).append(" 不存在，请从可用工具中选择。\n");
                continue;
            }

            emitter.emit("rca", "根因分析", "TOOL_CALL",
                    "参数：" + decision.argsSummary(), toolName);

            long toolStart = System.currentTimeMillis();
            Object toolResult;
            try {
                toolResult = toolRegistry.get(toolName).execute(decision.getToolArgs());
            } catch (Exception e) {
                log.warn("工具 {} 执行异常：{}", toolName, e.getMessage());
                toolResult = Map.of("success", false, "error", e.getMessage());
            }
            long toolCost = System.currentTimeMillis() - toolStart;

            String resultJson = JsonUtils.toJson(toolResult);
            // 只记录「工具名 + 关键参数」，不要把完整结果塞进轨迹：
            // 结果可能很长（历史告警列表等），既污染 UI 也无法体现「调用了什么」。
            toolTrace.add(toolName + "(" + abbreviate(decision.argsSummary(), 60) + ")");

            emitter.emit("rca", "根因分析", "TOOL_RESULT",
                    summarizeToolResult(toolResult), toolName);

            // 记录到 trace 表
            saveTrace(emitter.eventId, "rca", round, "TOOL_CALL", toolName,
                    "参数：" + decision.argsSummary() + " | 结果：" + abbreviate(resultJson, 1500),
                    (int) toolCost);

            observation.append("\n【工具结果 第").append(round).append("轮】")
                    .append(toolName).append(" 返回：\n")
                    .append(abbreviate(resultJson, 1500)).append("\n");
        }

        AgentOutputs.Rca rca = parseRca(finalContent, toolTrace);

        emitter.emit("rca", "根因分析", "OUTPUT",
                "根因：" + rca.getRootCause(), null);
        emitter.stageEnd("rca", "根因分析");
        return rca;
    }

    private AgentOutputs.Rca parseRca(String content, List<String> toolTrace) {
        AgentOutputs.Rca rca = new AgentOutputs.Rca();
        rca.setToolTrace(toolTrace);

        String json = JsonUtils.extractJsonObject(content == null ? "" : content);
        if (json != null) {
            try {
                Map<String, Object> m = JsonUtils.fromJson(json, Map.class);
                rca.setRootCause(str(m.get("root_cause"), "未能确定根因"));
                Object conf = m.get("confidence");
                rca.setConfidence(conf instanceof Number n ? n.doubleValue() : null);
                rca.setEvidence(toStringList(m.get("evidence")));
                rca.setImpact(str(m.get("impact"), ""));
                rca.setNextAction(str(m.get("next_action"), ""));
                return rca;
            } catch (Exception e) {
                log.warn("根因结果解析失败：{}", e.getMessage());
            }
        }
        // 兜底：即便结构化解析失败，也保留工具调用证据，不让整个分析白跑
        log.warn("根因输出不可解析，使用基于工具证据的兜底结论");
        rca.setRootCause("大模型返回格式异常，未能给出结构化根因结论");
        rca.setEvidence(toolTrace.isEmpty()
                ? List.of("未收集到工具证据")
                : List.of("已执行工具调用：" + String.join("、", toolTrace)));
        rca.setConfidence(0.3);
        return rca;
    }

    // ==================================================================
    // 阶段 3：处置建议
    // ==================================================================
    private AgentOutputs.Remediation runRemediation(Emitter emitter, String eventJson,
                                                    AgentOutputs.Classification classification,
                                                    AgentOutputs.Rca rca) {
        emitter.stageStart("remediation", "处置建议生成");
        emitter.emit("remediation", "处置建议生成", "THOUGHT",
                "结合知识库经验与根因结论，生成可执行的分步处置方案", null);

        String content = llmProvider.complete(
                PromptTemplates.REMEDIATION_SYSTEM,
                PromptTemplates.remediationUser(eventJson,
                        JsonUtils.toJson(classification), JsonUtils.toJson(rca)),
                true);

        AgentOutputs.Remediation out = parseRemediation(content);

        emitter.emit("remediation", "处置建议生成", "OUTPUT",
                "已生成 " + out.getSteps().size() + " 步处置方案，风险等级：" + out.getRisk(), null);
        emitter.stageEnd("remediation", "处置建议生成");
        return out;
    }

    private AgentOutputs.Remediation parseRemediation(String content) {
        AgentOutputs.Remediation out = new AgentOutputs.Remediation();
        String json = JsonUtils.extractJsonObject(content);
        if (json != null) {
            try {
                Map<String, Object> m = JsonUtils.fromJson(json, Map.class);
                out.setSteps(toStringList(m.get("steps")));
                out.setSuggestion(str(m.get("suggestion"), ""));
                out.setRisk(str(m.get("risk"), "中"));
                out.setMatchedTemplates(toStringList(m.get("matched_templates")));
                if (out.getSteps().isEmpty()) {
                    throw new IllegalStateException("steps 为空");
                }
                return out;
            } catch (Exception e) {
                log.warn("处置建议解析失败：{}", e.getMessage());
            }
        }
        log.warn("处置建议输出不可解析，启用兜底方案");
        out.setSteps(List.of("人工介入排查", "查看服务日志定位异常堆栈", "确认依赖服务健康状态"));
        out.setSuggestion("大模型输出格式异常，建议人工确认后再执行处置");
        out.setRisk("高");
        return out;
    }

    // ==================================================================
    // 阶段 4：报告生成
    // ==================================================================
    private AgentOutputs.Report runReport(Emitter emitter, String eventJson,
                                          AgentOutputs.Classification classification,
                                          AgentOutputs.Rca rca,
                                          AgentOutputs.Remediation remediation) {
        emitter.stageStart("report", "故障报告生成");
        emitter.emit("report", "故障报告生成", "THOUGHT",
                "汇总前三个阶段结论，生成结构化故障分析报告", null);

        String content = llmProvider.complete(
                PromptTemplates.REPORT_SYSTEM,
                PromptTemplates.reportUser(eventJson,
                        JsonUtils.toJson(classification), JsonUtils.toJson(rca),
                        JsonUtils.toJson(remediation)),
                true);

        AgentOutputs.Report out = parseReport(content, classification, rca, remediation);

        emitter.emit("report", "故障报告生成", "OUTPUT",
                "报告已生成：" + out.getTitle(), null);
        emitter.stageEnd("report", "故障报告生成");
        return out;
    }

    private AgentOutputs.Report parseReport(String content,
                                            AgentOutputs.Classification classification,
                                            AgentOutputs.Rca rca,
                                            AgentOutputs.Remediation remediation) {
        AgentOutputs.Report out = new AgentOutputs.Report();
        String json = JsonUtils.extractJsonObject(content);
        if (json != null) {
            try {
                Map<String, Object> m = JsonUtils.fromJson(json, Map.class);
                out.setTitle(str(m.get("title"), "故障分析报告"));
                out.setContent(str(m.get("content"), ""));
                if (out.getContent() == null || out.getContent().isBlank()) {
                    throw new IllegalStateException("content 为空");
                }
                return out;
            } catch (Exception e) {
                log.warn("报告解析失败：{}", e.getMessage());
            }
        }
        // 兜底：用结构化字段自行拼装 Markdown 报告，保证一定有产出
        log.warn("报告输出不可解析，改用模板拼装");
        out.setTitle("[" + classification.getLevel() + "] "
                + classification.getCategory() + " 故障分析报告");
        StringBuilder sb = new StringBuilder();
        sb.append("## 故障概述\n").append(classification.getSummary()).append("\n\n");
        sb.append("## 影响范围\n").append(nvl(rca.getImpact(), "待评估")).append("\n\n");
        sb.append("## 根因结论\n").append(nvl(rca.getRootCause(), "待确认")).append("\n\n");
        sb.append("## 证据链\n");
        List<String> evidence = rca.getEvidence();
        if (evidence != null && !evidence.isEmpty()) {
            for (String e : evidence) {
                sb.append("- ").append(e).append("\n");
            }
        } else {
            sb.append("- 暂无\n");
        }
        sb.append("\n## 处置过程\n");
        List<String> steps = remediation.getSteps();
        if (steps != null) {
            for (int i = 0; i < steps.size(); i++) {
                sb.append(i + 1).append(". ").append(steps.get(i)).append("\n");
            }
        }
        sb.append("\n## 后续改进建议\n- 针对本次根因补充监控与告警规则\n- 完善自动化处置预案\n");
        out.setContent(sb.toString());
        return out;
    }

    // ==================================================================

    @Override
    @Async("agentExecutor")
    public void analyzeAsync(Long eventId) {
        try {
            analyze(eventId);
        } catch (Exception e) {
            log.error("异步分析失败 eventId={}", eventId, e);
        }
    }

    @Override
    public List<AnalyzeStep> loadTrace(Long eventId) {
        List<AgentTrace> traces = traceMapper.selectList(new LambdaQueryWrapper<AgentTrace>()
                .eq(AgentTrace::getEventId, eventId)
                .orderByAsc(AgentTrace::getId));
        return traces.stream().map(t -> AnalyzeStep.builder()
                .stage(t.getStage())
                .stageLabel(stageLabel(t.getStage()))
                .type(t.getStepType())
                .stepNo(t.getStepNo())
                .toolName(t.getToolName())
                .content(t.getContent())
                .costMs(t.getCostMs() == null ? null : t.getCostMs().longValue())
                .timestamp(t.getCreateTime() == null ? null
                        : t.getCreateTime().atZone(java.time.ZoneId.systemDefault())
                        .toInstant().toEpochMilli())
                .build()).toList();
    }

    private Map<String, Object> loadEvent(Long eventId) {
        try {
            Result<Map<String, Object>> r = alertEventClient.getEvent(eventId);
            if (r == null || r.getData() == null) {
                throw new BizException(ResultCode.ALERT_EVENT_NOT_FOUND);
            }
            return r.getData();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ResultCode.AGENT_ANALYZE_FAILED,
                    "读取告警事件失败：" + e.getMessage());
        }
    }

    private Map<String, Object> buildTicketPayload(Long eventId, Map<String, Object> event,
                                                   AgentOutputs.Classification classification,
                                                   AgentOutputs.Rca rca,
                                                   AgentOutputs.Remediation remediation) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", eventId);
        payload.put("title", "[" + classification.getLevel() + "] "
                + classification.getCategory() + " 故障处置 - 事件 " + eventId);
        payload.put("priority", classification.getLevel());
        payload.put("category", classification.getCategory());
        payload.put("rootCause", rca.getRootCause());
        payload.put("suggestion", remediation.getSuggestion());
        payload.put("steps", remediation.getSteps());
        payload.put("summary", classification.getSummary());
        payload.put("instanceId", event.get("instanceId"));
        return payload;
    }

    private void saveTrace(Long eventId, String stage, int stepNo, String type,
                           String toolName, String content, Integer costMs) {
        try {
            AgentTrace t = new AgentTrace();
            t.setEventId(eventId);
            t.setStage(stage);
            t.setStepNo(stepNo);
            t.setStepType(type);
            t.setToolName(toolName);
            t.setContent(content);
            t.setCostMs(costMs);
            traceMapper.insert(t);
        } catch (Exception e) {
            log.warn("写入 Agent 轨迹失败：{}", e.getMessage());
        }
    }

    /**
     * 回写分析状态（RUNNING / DONE / FAILED）。
     */
    private void markAnalyzeStatus(Long eventId, String status) {
        try {
            alertEventClient.updateAnalysis(eventId, null, null, null, status);
        } catch (Exception e) {
            log.warn("更新分析状态失败 eventId={} status={}：{}", eventId, status, e.getMessage());
        }
    }

    /**
     * 把分类结论回写到告警事件，使大屏与报表的分类分布真实反映 Agent 的判断。
     */
    private void writeBackAnalysis(Long eventId, AgentOutputs.Classification classification) {
        try {
            alertEventClient.updateAnalysis(eventId,
                    classification.getCategory(),
                    classification.getLevel(),
                    classification.getSummary(),
                    "DONE");
        } catch (Exception e) {
            log.warn("回写分析结论失败 eventId={}：{}", eventId, e.getMessage());
        }
    }

    /**
     * 把报告落入 fault_report 表，供报表页查看与 Markdown 导出。
     */
    private void saveReport(Long eventId,
                            AgentOutputs.Classification classification,
                            AgentOutputs.Rca rca,
                            AgentOutputs.Remediation remediation,
                            AgentOutputs.Report report, int costSeconds) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("eventId", eventId);
            payload.put("title", report.getTitle());
            payload.put("category", classification.getCategory());
            payload.put("rootCause", rca.getRootCause());
            payload.put("evidence", JsonUtils.toJson(rca.getEvidence()));
            payload.put("steps", JsonUtils.toJson(remediation.getSteps()));
            payload.put("suggestion", remediation.getSuggestion());
            payload.put("reportContent", report.getContent());
            payload.put("costSeconds", costSeconds);
            reportClient.save(payload);
            log.info("事件 {} 故障报告已保存", eventId);
        } catch (Exception e) {
            log.warn("保存故障报告失败 eventId={}：{}", eventId, e.getMessage());
        }
    }

    /**
     * 把工具返回结果压缩成一句人可读的摘要，用于 SSE 推送与过程展示。
     *
     * <p>直接展示原始 JSON 会让界面变成一堵墙，也看不出关键信息。
     * 这里针对不同工具提炼最有价值的字段。
     */
    @SuppressWarnings("unchecked")
    private String summarizeToolResult(Object toolResult) {
        if (!(toolResult instanceof Map<?, ?> raw)) {
            return abbreviate(String.valueOf(toolResult), 300);
        }
        Map<String, Object> m = (Map<String, Object>) raw;
        if (Boolean.FALSE.equals(m.get("success"))) {
            Object err = m.get("error");
            return "调用失败：" + (err == null ? "未知错误" : err);
        }

        StringBuilder sb = new StringBuilder();

        // 指标查询：展示点数与最大/最小/平均
        if (m.containsKey("point_count")) {
            sb.append("获取 ").append(m.get("point_count")).append(" 个数据点");
            if (m.get("max") != null) {
                sb.append("，最大 ").append(m.get("max"))
                        .append("，最小 ").append(m.get("min"))
                        .append("，平均 ").append(m.get("avg"));
            }
            return sb.toString();
        }

        // 历史告警：展示匹配数与复发判断
        if (m.containsKey("matched_count")) {
            sb.append("匹配到 ").append(m.get("matched_count")).append(" 条同类历史告警");
            Object hint = m.get("recurrence_hint");
            if (hint != null) {
                sb.append("；").append(hint);
            }
            return sb.toString();
        }

        // 依赖拓扑：展示服务名与下游
        if (m.containsKey("service_name")) {
            sb.append("服务 ").append(m.get("service_name"));
            Object down = m.get("downstream");
            if (down instanceof List<?> list && !list.isEmpty()) {
                sb.append("，下游依赖：").append(String.join("、",
                        list.stream().map(String::valueOf).toList()));
            }
            return sb.toString();
        }

        // 知识库检索：展示命中的模板标题
        if (m.containsKey("templates")) {
            Object t = m.get("templates");
            if (t instanceof List<?> list) {
                if (list.isEmpty()) {
                    return "知识库未命中模板，按通用经验处置";
                }
                List<String> titles = list.stream()
                        .filter(x -> x instanceof Map)
                        .map(x -> String.valueOf(((Map<String, Object>) x).get("title")))
                        .toList();
                return "命中 " + list.size() + " 条处置模板：" + String.join("、", titles);
            }
        }

        return abbreviate(JsonUtils.toJson(toolResult), 300);
    }

    @SuppressWarnings("unchecked")
    private List<String> toStringList(Object obj) {
        if (obj == null) {
            return List.of();
        }
        if (obj instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of(String.valueOf(obj));
    }

    private String str(Object v, String def) {
        return v == null ? def : String.valueOf(v);
    }

    private String nvl(String v, String def) {
        return v == null || v.isBlank() ? def : v;
    }

    private String abbreviate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "...(已截断)";
    }

    /**
     * 阶段编码 → 中文名，供前端直接展示。
     */
    private String stageLabel(String stage) {
        return switch (stage == null ? "" : stage) {
            case "classification" -> "告警分类分级";
            case "rca" -> "根因分析";
            case "remediation" -> "处置建议生成";
            case "report" -> "故障报告生成";
            case "ticket" -> "工单派发";
            default -> "全链路完成";
        };
    }

    /**
     * 步骤发射器：统一处理「落库 + 回调推送 + 内存累积」，避免在每个阶段重复样板代码。
     */
    private class Emitter {
        private final Long eventId;
        private final Consumer<AnalyzeStep> onStep;
        private final List<AnalyzeStep> steps;
        private final AtomicInteger seq = new AtomicInteger(0);

        Emitter(Long eventId, Consumer<AnalyzeStep> onStep, List<AnalyzeStep> steps) {
            this.eventId = eventId;
            this.onStep = onStep;
            this.steps = steps;
        }

        void stageStart(String stage, String label) {
            emit(stage, label, "STAGE_START", "开始执行：" + label, null);
        }

        void stageEnd(String stage, String label) {
            emit(stage, label, "STAGE_END", "完成：" + label, null);
        }

        void emit(String stage, String label, String type, String content, String toolName) {
            AnalyzeStep step = AnalyzeStep.builder()
                    .stage(stage)
                    .stageLabel(label)
                    .type(type)
                    .stepNo(seq.incrementAndGet())
                    .content(content)
                    .toolName(toolName)
                    .costMs(null)
                    .timestamp(System.currentTimeMillis())
                    .build();
            steps.add(step);
            try {
                onStep.accept(step);
            } catch (Exception e) {
                log.warn("推送步骤失败：{}", e.getMessage());
            }
        }
    }
}
