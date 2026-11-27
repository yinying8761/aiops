package com.aiops.knowledge.controller;

import com.aiops.common.api.PageResult;
import com.aiops.common.api.Result;
import com.aiops.knowledge.dto.TemplateSaveRequest;
import com.aiops.knowledge.entity.KnowledgeTemplate;
import com.aiops.knowledge.service.KnowledgeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 知识库接口。对应文档 2.6：GET /knowledge/search。
 */
@RestController
@RequestMapping("/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    /**
     * 检索处置模板（Agent 工具 query_knowledge 的数据源）。
     */
    @GetMapping("/search")
    public Result<List<Map<String, Object>>> search(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "3") int limit) {
        return Result.ok(knowledgeService.search(keyword, category, limit));
    }

    /**
     * 分页查询模板。
     */
    @GetMapping("/templates")
    public Result<PageResult<KnowledgeTemplate>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category) {
        return Result.ok(knowledgeService.page(pageNum, pageSize, keyword, category));
    }

    /**
     * 模板详情（同时累加复用次数）。
     */
    @GetMapping("/templates/{id}")
    public Result<KnowledgeTemplate> get(@PathVariable Long id) {
        return Result.ok(knowledgeService.get(id));
    }

    /**
     * 新增或更新模板。
     */
    @PostMapping("/templates")
    public Result<Long> save(@Valid @RequestBody TemplateSaveRequest request) {
        return Result.ok(request.getId() == null ? "新增成功" : "更新成功",
                knowledgeService.save(request));
    }

    @PutMapping("/templates/{id}")
    public Result<Long> update(@PathVariable Long id, @Valid @RequestBody TemplateSaveRequest request) {
        request.setId(id);
        return Result.ok("更新成功", knowledgeService.save(request));
    }

    @DeleteMapping("/templates/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        knowledgeService.delete(id);
        return Result.ok("删除成功", null);
    }

    /**
     * 分类统计。
     */
    @GetMapping("/categories")
    public Result<List<Map<String, Object>>> categories() {
        return Result.ok(knowledgeService.categories());
    }
}
