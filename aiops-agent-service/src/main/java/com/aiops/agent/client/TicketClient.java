package com.aiops.agent.client;

import com.aiops.common.api.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * 工单服务客户端：报告 Agent 完成后自动建单派发；分析页回显时查询关联工单。
 */
@FeignClient(name = "aiops-ticket-service", url = "${aiops.services.ticket:http://127.0.0.1:8085}")
public interface TicketClient {

    /**
     * 依据分析结果自动创建并派发工单。
     */
    @PostMapping("/tickets/auto")
    Result<Map<String, Object>> autoCreate(@RequestBody Map<String, Object> payload);

    /**
     * 按事件ID查询工单。无工单时 data 为 null。
     */
    @GetMapping("/tickets/by-event/{eventId}")
    Result<Map<String, Object>> findByEvent(@PathVariable("eventId") Long eventId);
}
