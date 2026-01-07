package com.nano.oj.judge.codesandbox.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecuteCodeRequest {
    /**
     * 输入用例列表
     */
    private List<String> inputList;

    /**
     * 代码
     */
    private String code;

    /**
     * 语言
     */
    private String language;

    /**
     * 题目限制的时间（单位：毫秒）
     * 用于沙箱快速终止死循环程序
     */
    private Long timeLimit;

    /**
     * 题目限制的内存（字节 Byte）
     * ✨✨✨ 新增：用于直接限制容器大小
     */
    private Long memoryLimit;
}