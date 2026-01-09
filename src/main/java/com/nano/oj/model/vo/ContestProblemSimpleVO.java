package com.nano.oj.model.vo;

import lombok.Data;
import java.io.Serializable;

/**
 * 比赛题目简要信息（用于管理端回显）
 */
@Data
public class ContestProblemSimpleVO implements Serializable {
    private Long id;
    private String title;

    // 如果需要显示题号（如 A, B, C），也可以加一个 displayTitle
    private String displayTitle;

    private static final long serialVersionUID = 1L;
}