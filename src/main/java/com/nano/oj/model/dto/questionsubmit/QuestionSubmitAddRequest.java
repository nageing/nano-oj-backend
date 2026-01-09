package com.nano.oj.model.dto.questionsubmit;

import lombok.Data;
import java.io.Serializable;

/**
 * 创建题目提交请求
 */
@Data
public class QuestionSubmitAddRequest implements Serializable {

    /**
     * 编程语言
     */
    private String language;

    /**
     * 用户代码
     */
    private String code;

    /**
     * 题目 id
     */
    private Long problemId;

    /**
     * 比赛 ID (非必填，仅比赛提交时有值)
     */
    private Long contestId;

    private static final long serialVersionUID = 1L;
}