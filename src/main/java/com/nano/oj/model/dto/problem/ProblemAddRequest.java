package com.nano.oj.model.dto.problem;

import com.nano.oj.model.entity.Problem;
import lombok.Data;
import java.io.Serializable;
import java.util.List;

/**
 * 创建题目请求封装类
 */
@Data
public class ProblemAddRequest implements Serializable {

    /**
     * 标题
     */
    private String title;

    /**
     * 内容
     */
    private String content;

    /**
     * 标签列表 (前端传的是 ["栈", "困难"] 这种数组)
     */
    private List<String> tags;

    /**
     * 题目答案
     */
    private String answer;

    /**
     * 判题用例 (JSON 数组)
     * 暂时用 Object，后面我们会定义专门的类
     */
    private List<Object> judgeCase;

    /**
     * 判题配置 (JSON 对象)
     * 暂时用 Object
     */
    private Object judgeConfig;

    private static final long serialVersionUID = 1L;
}