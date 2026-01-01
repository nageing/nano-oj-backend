package com.nano.oj.common;

import lombok.Data;

/**
 * 通用分页请求参数
 */
@Data
public class PageRequest {

    /**
     * 当前页号 (默认第 1 页)
     */
    protected int current = 1;

    /**
     * 页面大小 (默认每页 10 条)
     */
    protected int pageSize = 10;

    /**
     * 排序字段
     */
    protected String sortField;

    /**
     * 排序顺序 (asc / desc)
     */
    protected String sortOrder = "ascend";
}