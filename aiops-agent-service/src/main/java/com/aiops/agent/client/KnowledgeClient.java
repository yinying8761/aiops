package com.aiops.agent.client;

import com.aiops.common.api.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * 知识库服务客户端：供处置建议 Agent 检索经验模板。
 */
@FeignClient(name = "aiops-knowledge-service", url = "${aiops.services.knowledge:http://127.0.0.1:8086}")
public interface KnowledgeClient {

    /**
     * 按症状/标签检索处置模板。
     */
    @GetMapping("/knowledge/search")
    Result<List<Map<String, Object>>> search(
            @RequestParam("keyword") String keyword,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "limit", defaultValue = "3") int limit);
}
