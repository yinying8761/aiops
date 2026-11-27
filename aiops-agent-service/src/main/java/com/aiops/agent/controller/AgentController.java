package com.aiops.agent.controller;

import com.aiops.agent.dto.AnalyzeResult;
import com.aiops.agent.dto.AnalyzeStep;
import com.aiops.agent.llm.LlmProvider;
import com.aiops.agent.service.AgentOrchestrator;
import com.aiops.common.api.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Agent 分析接口。对应文档 2.6：
 * POST /agent/analyze（触发 Agent 分析，返回 SSE 流式结果）。
 */
@Slf4j
@RestController
@RequestMapping("/agent")
@RequiredArgsConstructor
public class AgentController {

    private final AgentOrchestrator orchestrator;
    private final LlmProvider llmProvider;
    private final com.aiops.agent.service.AnalyzeResultQuery resultQuery;

    /** SSE 推送专用线程，避免占用 Tomcat 请求线程 */
    private final ExecutorService sseExecutor = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "agent-sse");
        t.setDaemon(true);
        return t;
    });

    /**
     * 同步分析：一次性返回完整结果。适合调试与测试断言（TC-05 / TC-06）。
     */
    @PostMapping("/analyze")
    public Result<AnalyzeResult> analyze(@RequestParam Long eventId) {
        return Result.ok("分析完成", orchestrator.analyze(eventId));
    }

    /**
     * SSE 流式分析：逐段推送分类、根因、处置与报告，前端实时渲染。
     * 对应 TC-09「SSE 流式输出」。
     */
    @GetMapping(value = "/analyze/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter analyzeStream(@RequestParam Long eventId) {
        // 0 表示不超时，由前端主动断开或分析完成后 complete
        SseEmitter emitter = new SseEmitter(0L);

        emitter.onTimeout(() -> log.warn("SSE 超时 eventId={}", eventId));
        emitter.onError(e -> log.warn("SSE 异常 eventId={}：{}", eventId, e.getMessage()));

        sseExecutor.submit(() -> {
            try {
                AnalyzeResult result = orchestrator.analyzeStreaming(eventId, step -> {
                    try {
                        emitter.send(SseEmitter.event()
                                .name("step")
                                .id(String.valueOf(step.getStepNo()))
                                .data(step, MediaType.APPLICATION_JSON));
                    } catch (IOException e) {
                        log.debug("SSE 推送中断（客户端已断开）：{}", e.getMessage());
                    }
                });
                // 结束时把完整结果作为最后一个事件推给前端
                emitter.send(SseEmitter.event()
                        .name("result")
                        .data(result, MediaType.APPLICATION_JSON));
                emitter.complete();
            } catch (Exception e) {
                log.error("SSE 分析失败 eventId={}", eventId, e);
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data(Map.of("message", String.valueOf(e.getMessage())),
                                    MediaType.APPLICATION_JSON));
                } catch (IOException ignored) {
                    // 客户端已断开
                }
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    /**
     * 异步触发（由 alert-service 内部投递调用），立即返回，不阻塞告警链路。
     */
    @PostMapping("/analyze/async/{eventId}")
    public Result<Void> analyzeAsync(@PathVariable Long eventId) {
        orchestrator.analyzeAsync(eventId);
        return Result.ok("已投递异步分析", null);
    }

    /**
     * 查询某事件的历史分析轨迹（过程回放）。
     */
    @GetMapping("/trace/{eventId}")
    public Result<List<AnalyzeStep>> trace(@PathVariable Long eventId) {
        return Result.ok(orchestrator.loadTrace(eventId));
    }

    /**
     * 查询某事件最近一次分析结果。用于进入分析页时回显上次结论，
     * 避免用户每次都要重新触发分析。无历史结果时 data 为 null。
     */
    @GetMapping("/result/{eventId}")
    public Result<AnalyzeResult> lastResult(@PathVariable Long eventId) {
        return Result.ok(resultQuery.loadLastResult(eventId));
    }

    /**
     * 当前生效的 LLM 实现，便于前端标注「模拟/真实模型」。
     */
    @GetMapping("/provider")
    public Result<Map<String, Object>> provider() {
        return Result.ok(Map.of(
                "provider", llmProvider.name(),
                "mode", "mock".equals(llmProvider.name()) ? "模拟模型（规则+模板）" : "真实大模型"));
    }
}
