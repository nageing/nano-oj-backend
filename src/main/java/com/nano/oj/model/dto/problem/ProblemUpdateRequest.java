package com.nano.oj.model.dto.problem;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 更新题目请求
 */
@Data
public class ProblemUpdateRequest implements Serializable {

    /**
     * id (必须要有，否则不知道改谁)
     */
    private Long id;

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
     * 答案
     */
    private String answer;

    /**
     * 判题用例
     */
    private List<Object> judgeCase;

    /**
     * 判题配置
     */
    private Object judgeConfig;

    @Serial
    private static final long serialVersionUID = 1L;
}