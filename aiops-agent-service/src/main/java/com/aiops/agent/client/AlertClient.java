package com.aiops.agent.client;

import com.aiops.common.api.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * 告警服务客户端：供「查历史告警」工具使用。
 */
@FeignClient(name = "aiops-alert-service", url = "${aiops.services.alert:http://127.0.0.1:8082}")
public interface AlertClient {

    /**
     * 查询历史聚合事件，用于判断「是否反复发生」，支撑根因推理。
     */
    @GetMapping("/alerts/events")
    Result<Map<String, Object>> queryEvents(
            @RequestParam(value = "pageNum", defaultValue = "1") long pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") long pageSize,
            @RequestParam(value = "keyword", required = false) String keyword);

    /**
     * 查询事件下的原始告警明细。
     */
    @GetMapping("/alerts/events/{id}/alerts")
    Result<List<Map<String, Object>>> listAlerts(@org.springframework.web.bind.annotation.PathVariable("id") Long id);
}
