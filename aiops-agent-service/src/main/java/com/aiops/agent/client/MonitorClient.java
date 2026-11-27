package com.aiops.agent.client;

import com.aiops.common.api.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * 监控指标服务客户端。
 * <p>用 URL 直连而非服务名，保证未启用 Nacos 时也能跑通（见配置 aiops.services.monitor）；
 * 启用 Nacos 后把 url 换成服务名即可由注册中心寻址。
 */
@FeignClient(name = "aiops-monitor-service", url = "${aiops.services.monitor:http://127.0.0.1:8083}")
public interface MonitorClient {

    /**
     * 查询指定实例、指定指标的时序数据。
     */
    @GetMapping("/metrics/{instanceId}/series")
    Result<List<Map<String, Object>>> querySeries(
            @PathVariable("instanceId") Long instanceId,
            @RequestParam("metricName") String metricName,
            @RequestParam(value = "range", defaultValue = "30m") String range);

    /**
     * 查询实例当前全部指标快照。
     */
    @GetMapping("/metrics/{instanceId}/latest")
    Result<Map<String, Object>> queryLatest(@PathVariable("instanceId") Long instanceId);
}
