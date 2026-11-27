package com.aiops.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 知识模板保存请求。
 */
@Data
public class TemplateSaveRequest {

    private Long id;

    @NotBlank(message = "标题不能为空")
    @Size(max = 200, message = "标题不超过 200 字")
    private String title;

    @NotBlank(message = "症状描述不能为空")
    @Size(max = 500, message = "症状不超过 500 字")
    private String symptom;

    @Size(max = 1000, message = "根因不超过 1000 字")
    private String rootCause;

    private String solution;

    @Size(max = 255, message = "标签不超过 255 字")
    private String tags;

    private String category;
}
