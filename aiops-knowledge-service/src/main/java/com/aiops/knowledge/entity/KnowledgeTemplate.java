package com.aiops.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 处置经验模板，对应 knowledge_template 表。
 * <p>核心结构即「症状 — 根因 — 解法」三段式（文档 4.2）。
 */
@Data
@TableName("knowledge_template")
public class KnowledgeTemplate {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String symptom;

    private String rootCause;

    private String solution;

    /** 逗号分隔的标签 */
    private String tags;

    private String category;

    private Integer useCount;

    private String createBy;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
