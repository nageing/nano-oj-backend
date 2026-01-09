package com.nano.oj.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.util.Date;

/**
 * 比赛报名实体
 * @TableName contest_apply
 */
@TableName(value = "contest_apply")
@Data
public class ContestApply implements Serializable {

    /**
     * id
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 比赛 id
     */
    private Long contestId;

    /**
     * 用户 id
     */
    private Long userId;

    /**
     * 报名时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * 是否删除
     */
    @TableLogic
    private Integer isDelete;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}