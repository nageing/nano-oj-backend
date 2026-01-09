package com.nano.oj.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.util.Date;

/**
 * 比赛题目关联实体
 * @TableName contest_problem
 */
@TableName(value = "contest_problem")
@Data
public class ContestProblem implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 比赛 id
     */
    private Long contestId;

    /**
     * 题目 id
     */
    private Long questionId;

    /**
     * 在比赛中的顺序号 (0, 1, 2...)
     */
    private Integer displayId;

    /**
     * 比赛中显示的自定义题目名称
     */
    private String displayTitle;

    private Date createTime;
    private Date updateTime;
    private Integer score;
    @TableLogic
    private Integer isDelete;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}