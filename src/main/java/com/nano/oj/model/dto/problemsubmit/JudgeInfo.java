package com.nano.oj.model.dto.problemsubmit;

import lombok.Data;

/**
 * 判题信息
 */
@Data
public class JudgeInfo {

    /**
     * 程序执行信息 (例如: Accepted, Wrong Answer, Compile Error 等)
     */
    private String message;

    /**
     * 错误详情 (例如: 具体的编译报错信息、运行时堆栈信息)
     */
    private String detail;

    /**
     * 消耗内存 (KB)
     */
    private Long memory;

    /**
     * 消耗时间 (ms)
     */
    private Long time;
}