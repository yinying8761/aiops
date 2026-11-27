package com.aiops.common.api;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 分页返回结构。与前端约定字段：records / total / pageNum / pageSize / pages。
 *
 * @param <T> 行数据类型
 */
@Data
public class PageResult<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private List<T> records;
    private Long total;
    private Long pageNum;
    private Long pageSize;
    private Long pages;

    public PageResult() {
    }

    public PageResult(List<T> records, Long total, Long pageNum, Long pageSize) {
        this.records = records == null ? Collections.emptyList() : records;
        this.total = total == null ? 0L : total;
        this.pageNum = pageNum;
        this.pageSize = pageSize;
        this.pages = (pageSize == null || pageSize <= 0) ? 0L
                : (this.total + pageSize - 1) / pageSize;
    }

    public static <T> PageResult<T> empty(Long pageNum, Long pageSize) {
        return new PageResult<>(Collections.emptyList(), 0L, pageNum, pageSize);
    }
}
