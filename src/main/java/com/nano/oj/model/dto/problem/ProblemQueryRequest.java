package com.nano.oj.model.dto.problem;

import com.nano.oj.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 题目查询请求
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class ProblemQueryRequest extends PageRequest implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * 标题 (关键词搜索)
     */
    private String title;

    /**
     * 内容 (关键词搜索)
     */
    private String content;

    /**
     * 标签列表 (搜索包含某些标签的题目)
     */
    private List<String> tags;

    /**
     * 创建人 id (查自己创建的题)
     */
    private Long userId;

    /**
     * 可见性
     */
    private Integer visible;

    @Serial
    private static final long serialVersionUID = 1L;
}