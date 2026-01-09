package com.nano.oj.model.dto.questionsubmit;

import lombok.Data;
import java.io.Serializable;

/**
 * 自测代码请求
 */
@Data
public class QuestionRunRequest implements Serializable {
    /**
     * 代码
     */
    private String code;

    /**
     * 自测输入 (用户在控制台输入的用例)
     */
    private String input;

    /**
     * 编程语言
     */
    private String language;

    private static final long serialVersionUID = 1L;
}