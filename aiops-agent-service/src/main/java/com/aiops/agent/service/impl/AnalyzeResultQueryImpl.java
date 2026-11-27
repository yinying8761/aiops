package com.aiops.agent.service.impl;

import com.aiops.agent.client.ReportQueryClient;
import com.aiops.agent.client.TicketClient;
import com.aiops.agent.dto.AgentOutputs;
import com.aiops.agent.dto.AnalyzeResult;
import com.aiops.agent.dto.AnalyzeStep;
import com.aiops.agent.service.AgentOrchestrator;
import com.aiops.agent.service.AnalyzeResultQuery;
import com.aiops.common.api.Result;
import com.aiops.common.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 分析结果回显实现。
 *
 * <p>进入分析页时如果该事件已分析过，直接展示上次结论，而不是让用户再跑一遍
 * （重跑会重复调用大模型、产生额外成本，也不符合「回看历史」的直觉）。
 *
 * <p>数据来源：fault_report 保存了分类/根因/报告，agent_trace 保存了过程步骤，
 * 工单信息通过 ticket 表按 eventId 查询。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyzeResultQueryImpl implements AnalyzeResultQuery {

    private final ReportQueryClient reportQueryClient;
    private final TicketClient ticketClient;
    private final AgentOrchestrator orchestrator;

    @Override
    @SuppressWarnings("unchecked")
    public AnalyzeResult loadLastResult(Long eventId) {
        Map<String, Object> report;
        try {
            Result<Map<String, Object>> r = reportQueryClient.findByEvent(eventId);
            report = r == null ? null : r.getData();
        } catch (Exception e) {
            log.warn("查询历史报告失败 eventId={}：{}", eventId, e.getMessage());
            return null;
        }
        if (report == null) {
            return null;
        }

        AgentOutputs.Classification classification = new AgentOutputs.Classification();
        classification.setCategory(str(report.get("category")));
        classification.setSummary(firstSummary(report));
        // 报告表未单独存级别，这里从报告标题的 "[P0]" 前缀解析
        classification.setLevel(extractLevel(str(report.get("title"))));

        AgentOutputs.Rca rca = new AgentOutputs.Rca();
        rca.setRootCause(str(report.get("rootCause")));
        rca.setEvidence(parseEvidence(str(report.get("evidence"))));

        AgentOutputs.Remediation remediation = new AgentOutputs.Remediation();
        remediation.setSuggestion(str(report.get("suggestion")));
        remediation.setSteps(parseStringList(str(report.get("steps"))));
        remediation.setRisk("中");

        AgentOutputs.Report rep = new AgentOutputs.Report();
        rep.setTitle(str(report.get("title")));
        rep.setContent(str(report.get("reportContent")));

        List<AnalyzeStep> steps = orchestrator.loadTrace(eventId);

        // 从 trace 中还原本次实际调用过哪些工具。
        // TOOL_CALL 步骤的 content 形如「调用工具 xxx(args)」，这里只取工具名与参数摘要，
        // 避免把工具返回的长 JSON 也当作"工具调用"展示。
        List<String> toolTrace = steps.stream()
                .filter(s -> "TOOL_CALL".equals(s.getType()))
                .map(this::extractToolCall)
                .filter(s -> s != null && !s.isBlank())
                .toList();
        rca.setToolTrace(toolTrace);

        Long ticketId = null;
        String ticketNo = null;
        try {
            Result<Map<String, Object>> t = ticketClient.findByEvent(eventId);
            if (t != null && t.getData() != null) {
                Object id = t.getData().get("id");
                ticketId = id instanceof Number n ? n.longValue() : null;
                Object no = t.getData().get("ticketNo");
                ticketNo = no == null ? null : no.toString();
            }
        } catch (Exception e) {
            log.debug("查询事件 {} 关联工单失败：{}", eventId, e.getMessage());
        }

        Object costObj = report.get("costSeconds");
        long costMs = costObj instanceof Number n ? n.longValue() * 1000 : 0L;

        return AnalyzeResult.builder()
                .eventId(eventId)
                .llmProvider("history")
                .classification(classification)
                .rca(rca)
                .remediation(remediation)
                .report(rep)
                .ticketId(ticketId)
                .ticketNo(ticketNo)
                .costMs(costMs)
                .startTime(LocalDateTime.now())
                .steps(steps)
                .build();
    }

    /**
     * 从 TOOL_CALL 步骤内容中提取「工具名(参数摘要)」。
     *
     * <p>trace 里保存的 content 形如：
     * {@code 参数：instance_id=1, metric_name=cpu_usage | 结果：{"success":true,...}}
     * 因此必须优先按「参数：」与「|」分隔符解析，不能按括号截取——
     * 否则会截到「结果」里的 JSON。
     */
    private String extractToolCall(AnalyzeStep step) {
        String toolName = step.getToolName();
        if (toolName == null) {
            return null;
        }
        String content = step.getContent();
        if (content == null || content.isBlank()) {
            return toolName + "()";
        }

        String args = "";
        int argsIdx = content.indexOf("参数：");
        if (argsIdx < 0) {
            argsIdx = content.indexOf("参数:");
        }
        if (argsIdx >= 0) {
            int start = argsIdx + 3;
            // 兼容新旧两种格式：新版直接是参数，旧版后面跟着 "| 结果：..."
            int end = content.indexOf('|', start);
            if (end < 0) {
                // 也兼容把工具名写在中间的旧格式
                int callIdx = content.indexOf("调用工具 ");
                end = callIdx >= 0 && callIdx > start ? callIdx : content.length();
            }
            args = content.substring(start, end).trim();
        } else {
            // 兜底：形如「调用工具 xxx(a=1)」
            int lb = content.indexOf('(');
            int rb = content.indexOf(')');
            if (lb >= 0 && rb > lb) {
                args = content.substring(lb + 1, rb).trim();
            }
        }

        // 参数过长时截断，避免撑破 UI
        if (args.length() > 70) {
            args = args.substring(0, 70) + "...";
        }
        return toolName + "(" + args + ")";
    }

    private String firstSummary(Map<String, Object> report) {
        // fault_report 没有单独摘要字段，取根因作为摘要展示即可
        String root = str(report.get("rootCause"));
        return root == null ? "" : root;
    }

    private String extractLevel(String title) {
        if (title == null) {
            return "P2";
        }
        int lb = title.indexOf('[');
        int rb = title.indexOf(']');
        if (lb >= 0 && rb > lb) {
            String level = title.substring(lb + 1, rb).trim();
            if (level.matches("P[0-3]")) {
                return level;
            }
        }
        return "P2";
    }

    @SuppressWarnings("unchecked")
    private List<String> parseEvidence(String evidenceJson) {
        return parseStringList(evidenceJson);
    }

    /**
     * 把存储的 JSON 数组字符串还原成 List。非 JSON 时按单条文本处理。
     */
    @SuppressWarnings("unchecked")
    private List<String> parseStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            Object parsed = JsonUtils.fromJson(json, List.class);
            if (parsed instanceof List<?> list) {
                return list.stream().map(String::valueOf).toList();
            }
        } catch (Exception ignored) {
            // 非 JSON 时按单条处理
        }
        return List.of(json);
    }

    private String str(Object v) {
        return v == null ? null : v.toString();
    }
}
