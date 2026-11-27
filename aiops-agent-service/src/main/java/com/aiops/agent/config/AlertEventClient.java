package com.aiops.agent.config;

import com.aiops.common.api.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * 读取/回写告警事件的客户端。Agent 分析的输入是「聚合告警事件」，
 * 分析完成后把分类结果与状态回写到事件上，供大屏与报表使用。
 */
@FeignClient(name = "aiops-alert-service", contextId = "alertEventClient",
        url = "${aiops.services.alert:http://127.0.0.1:8082}")
public interface AlertEventClient {

    @GetMapping("/alerts/events/{id}")
    Result<Map<String, Object>> getEvent(@PathVariable("id") Long id);

    /**
     * 回写分析结论（分类、级别、摘要、分析状态）。
     */
    @PutMapping("/alerts/events/{id}/analysis")
    Result<Void> updateAnalysis(@PathVariable("id") Long id,
                                @RequestParam(value = "category", required = false) String category,
                                @RequestParam(value = "level", required = false) String level,
                                @RequestParam(value = "summary", required = false) String summary,
                                @RequestParam(value = "analyzeStatus", required = false) String analyzeStatus);
}
