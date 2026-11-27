package com.aiops.knowledge.service.impl;

import com.aiops.common.api.PageResult;
import com.aiops.common.api.ResultCode;
import com.aiops.common.exception.BizException;
import com.aiops.common.security.UserContextHolder;
import com.aiops.knowledge.dto.TemplateSaveRequest;
import com.aiops.knowledge.entity.KnowledgeTemplate;
import com.aiops.knowledge.mapper.KnowledgeTemplateMapper;
import com.aiops.knowledge.service.KnowledgeService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 处置经验知识库实现。
 *
 * <p>检索策略（文档 4.2 要求「关键词 + 向量检索」，当前实现为可运行的轻量方案）：
 * <ol>
 *   <li>先用 MySQL ngram 全文索引做症状/根因匹配；</li>
 *   <li>全文检索无结果时退化为标题/标签的 LIKE 模糊匹配；</li>
 *   <li>最后按「关键词命中数 → 复用次数」排序，把最可能适用的模板排前面。</li>
 * </ol>
 * 向量检索留作扩展（见 6.3「知识模板目前以结构化检索为主，可进一步引入向量检索」）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeServiceImpl implements KnowledgeService {

    private final KnowledgeTemplateMapper mapper;

    @Override
    public List<Map<String, Object>> search(String keyword, String category, int limit) {
        String kw = keyword == null ? "" : keyword.trim();
        int max = Math.min(Math.max(limit, 1), 10);

        LambdaQueryWrapper<KnowledgeTemplate> wrapper = new LambdaQueryWrapper<>();
        if (category != null && !category.isBlank()) {
            wrapper.eq(KnowledgeTemplate::getCategory, category);
        }
        if (!kw.isBlank()) {
            wrapper.and(w -> w
                    .like(KnowledgeTemplate::getTitle, kw)
                    .or().like(KnowledgeTemplate::getSymptom, kw)
                    .or().like(KnowledgeTemplate::getRootCause, kw)
                    .or().like(KnowledgeTemplate::getTags, kw));
        }
        wrapper.orderByDesc(KnowledgeTemplate::getUseCount);

        List<KnowledgeTemplate> list = mapper.selectList(wrapper);

        // 若按关键词一条都没命中，退化为按分类取使用最多的模板
        if (list.isEmpty() && (category == null || category.isBlank())) {
            list = mapper.selectList(new LambdaQueryWrapper<KnowledgeTemplate>()
                    .orderByDesc(KnowledgeTemplate::getUseCount)
                    .last("LIMIT " + max));
        }

        // 按关键词命中次数重排，命中越多越靠前
        List<KnowledgeTemplate> ranked = list.stream()
                .sorted(Comparator
                        .comparingInt((KnowledgeTemplate t) -> hitCount(t, kw)).reversed()
                        .thenComparing(Comparator
                                .comparingInt((KnowledgeTemplate t) ->
                                        t.getUseCount() == null ? 0 : t.getUseCount()).reversed()))
                .limit(max)
                .toList();

        return ranked.stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId());
            m.put("title", t.getTitle());
            m.put("symptom", t.getSymptom());
            m.put("rootCause", t.getRootCause());
            m.put("solution", t.getSolution());
            m.put("tags", t.getTags());
            m.put("category", t.getCategory());
            m.put("useCount", t.getUseCount());
            return m;
        }).toList();
    }

    private int hitCount(KnowledgeTemplate t, String kw) {
        if (kw == null || kw.isBlank()) {
            return 0;
        }
        int n = 0;
        n += contains(t.getTitle(), kw) ? 3 : 0;
        n += contains(t.getTags(), kw) ? 2 : 0;
        n += contains(t.getSymptom(), kw) ? 2 : 0;
        n += contains(t.getRootCause(), kw) ? 1 : 0;
        return n;
    }

    private boolean contains(String text, String kw) {
        return text != null && kw != null && text.contains(kw);
    }

    @Override
    public PageResult<KnowledgeTemplate> page(long pageNum, long pageSize,
                                              String keyword, String category) {
        LambdaQueryWrapper<KnowledgeTemplate> wrapper = new LambdaQueryWrapper<KnowledgeTemplate>()
                .eq(category != null && !category.isBlank(),
                        KnowledgeTemplate::getCategory, category)
                .and(keyword != null && !keyword.isBlank(), w -> w
                        .like(KnowledgeTemplate::getTitle, keyword)
                        .or().like(KnowledgeTemplate::getSymptom, keyword)
                        .or().like(KnowledgeTemplate::getTags, keyword))
                .orderByDesc(KnowledgeTemplate::getUseCount)
                .orderByDesc(KnowledgeTemplate::getId);

        Page<KnowledgeTemplate> page = mapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return new PageResult<>(page.getRecords(), page.getTotal(), pageNum, pageSize);
    }

    @Override
    public KnowledgeTemplate get(Long id) {
        KnowledgeTemplate t = mapper.selectById(id);
        if (t == null) {
            throw new BizException(ResultCode.TEMPLATE_NOT_FOUND);
        }
        // 被查看即认为复用一次，用于统计「哪些经验最有价值」
        mapper.increaseUseCount(id);
        return t;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(TemplateSaveRequest request) {
        KnowledgeTemplate t = new KnowledgeTemplate();
        t.setId(request.getId());
        t.setTitle(request.getTitle());
        t.setSymptom(request.getSymptom());
        t.setRootCause(request.getRootCause());
        t.setSolution(request.getSolution());
        t.setTags(request.getTags());
        t.setCategory(request.getCategory());

        if (request.getId() == null) {
            t.setUseCount(0);
            t.setCreateBy(UserContextHolder.currentUsernameOrSystem());
            mapper.insert(t);
        } else {
            mapper.updateById(t);
        }
        return t.getId();
    }

    @Override
    public void delete(Long id) {
        mapper.deleteById(id);
    }

    @Override
    public List<Map<String, Object>> categories() {
        List<KnowledgeTemplate> all = mapper.selectList(null);
        Map<String, Long> counts = all.stream()
                .collect(Collectors.groupingBy(
                        t -> t.getCategory() == null ? "OTHER" : t.getCategory(),
                        LinkedHashMap::new, Collectors.counting()));
        return counts.entrySet().stream()
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("category", e.getKey());
                    m.put("count", e.getValue());
                    return m;
                }).toList();
    }
}
