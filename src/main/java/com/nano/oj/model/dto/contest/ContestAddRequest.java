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
     * 关联的题目 ID 列表
     */
    private List<Long> problemIds;

    private static final long serialVersionUID = 1L;
}