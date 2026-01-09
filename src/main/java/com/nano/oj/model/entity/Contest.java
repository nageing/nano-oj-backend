package com.nano.oj.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.util.Date;

/**
 * 比赛实体
 * @TableName contest
 */
@TableName(value = "contest")
@Data
public class Contest implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 比赛名称
     */
    private String title;

    /**
     * 比赛描述
     */
    private String description;

    /**
     * 开始时间
     */
    private Date startTime;

    /**
     * 结束时间
     */
    private Date endTime;

    /**
     * 比赛状态（0-未开始，1-进行中，2-已结束）
     */
    private Integer status;

    /**
     * 访问密码（空则为公开）
     */
    private String pwd;

    /**
     * 赛制：0-ACM, 1-OI
     */
    private Integer type;

    /**
     * 创建者id
     */
    private Long userId;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    @TableLogic
    private Integer isDelete;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}