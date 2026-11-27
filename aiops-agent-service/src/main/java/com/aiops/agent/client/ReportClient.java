package com.aiops.agent.client;

import com.aiops.common.api.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * 报表服务客户端：把 Agent 生成的故障报告落库，供报表页查看与导出。
 */
@FeignClient(name = "aiops-report-service", url = "${aiops.services.report:http://127.0.0.1:8087}")
public interface ReportClient {

    @PostMapping("/reports")
    Result<Long> save(@RequestBody Map<String, Object> report);
}
