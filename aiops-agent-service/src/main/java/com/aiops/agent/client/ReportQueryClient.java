package com.aiops.agent.client;

import com.aiops.common.api.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

/**
 * 报表服务只读客户端：读取某事件已保存的故障报告，用于分析页回显。
 */
@FeignClient(name = "aiops-report-service", contextId = "reportQueryClient",
        url = "${aiops.services.report:http://127.0.0.1:8087}")
public interface ReportQueryClient {

    /**
     * 按事件查询最近一份报告。无报告时 data 为 null。
     */
    @GetMapping("/reports/by-event/{eventId}")
    Result<Map<String, Object>> findByEvent(@PathVariable("eventId") Long eventId);
}
