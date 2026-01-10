package com.nano.oj.model.dto.contest;

import lombok.Data;
import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * 创建比赛请求 (包含基本信息 + 题目列表)
 */
@Data
public class ContestAddRequest implements Serializable {

    /**
     * --- 步骤 1 数据 ---
     */
    private String title;
    private String description;
    private Date startTime;
    private Date endTime;
    private String pwd;   // 密码
    private Integer type; // 赛制

    /**
     * 关联的题目 列表
     */
    private List<ContestProblemItem> problems;

    @Data
    public static class ContestProblemItem implements Serializable {
        /**
         * 题目 ID
         */
        private Long id;

        /**
         * 题目分数 (前端传过来的)
         */
        private Integer score;

        /**
         * 原本标题
         */
        private String title;

        /**
         * 展示标题
         */
        private String displayTitle;
    }

    private static final long serialVersionUID = 1L;
}