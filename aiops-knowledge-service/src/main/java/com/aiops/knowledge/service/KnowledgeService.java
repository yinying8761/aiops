package com.aiops.knowledge.service;

import com.aiops.common.api.PageResult;
import com.aiops.knowledge.dto.TemplateSaveRequest;
import com.aiops.knowledge.entity.KnowledgeTemplate;

import java.util.List;
import java.util.Map;

/**
 * 处置经验知识库服务。
 */
public interface KnowledgeService {

    /**
     * 关键词 + 分类检索，供处置建议 Agent 复用（Agent 工具 query_knowledge 的数据源）。
     */
    List<Map<String, Object>> search(String keyword, String category, int limit);

    /**
     * 分页查询模板。
     */
    PageResult<KnowledgeTemplate> page(long pageNum, long pageSize, String keyword, String category);

    /**
     * 模板详情。
     */
    KnowledgeTemplate get(Long id);

    /**
     * 新增或更新。
     */
    Long save(TemplateSaveRequest request);

    /**
     * 删除（逻辑删除）。
     */
    void delete(Long id);

    /**
     * 分类聚合统计（供前端标签云）。
     */
    List<Map<String, Object>> categories();
}
