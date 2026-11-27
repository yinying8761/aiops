package com.aiops.knowledge.mapper;

import com.aiops.knowledge.entity.KnowledgeTemplate;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 知识模板 Mapper。
 */
@Mapper
public interface KnowledgeTemplateMapper extends BaseMapper<KnowledgeTemplate> {

    /**
     * 复用计数加一，用于统计哪些经验最常被采用。
     */
    @Update("UPDATE knowledge_template SET use_count = use_count + 1 WHERE id = #{id}")
    int increaseUseCount(@Param("id") Long id);
}
