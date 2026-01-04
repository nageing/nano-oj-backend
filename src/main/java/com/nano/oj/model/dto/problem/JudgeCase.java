package com.nano.oj.model.dto.problem;

import lombok.Data;

/**
 * 判题用例
 */
@Data
public class JudgeCase {

    /**
     * 输入用例
     */
    private String input;

    /**
     * 预期输出
     */
    private String output;
}