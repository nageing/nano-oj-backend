package com.nano.oj.model.dto.post;

import lombok.Data;
import java.io.Serializable;
import java.util.List;

@Data
public class PostAddRequest implements Serializable {

    /**
     * 标题
     */
    private String title;

    /**
     * 内容
     */
    private String content;

    /**
     * 标签列表
     */
    private List<String> tags;

    /**
     * 关联题目 id (可以为空)
     */
    private Long questionId;

    private static final long serialVersionUID = 1L;
}