package com.nano.oj.model.dto.post;

import com.nano.oj.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.io.Serializable;

@Data
@EqualsAndHashCode(callSuper = true)
public class PostQueryRequest extends PageRequest implements Serializable {

    /**
     * 搜索词 (搜标题、内容)
     */
    private String searchText;

    /**
     * 关联题目 id
     */
    private Long questionId;

    /**
     * 用户 id
     */
    private Long userId;

    private static final long serialVersionUID = 1L;
}