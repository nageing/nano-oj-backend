package com.nano.oj.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.io.Serializable;
import java.util.Date;

@TableName(value = "problem_tag")
@Data
public class ProblemTag implements Serializable {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long problemId;

    private Long tagId;

    private Date createTime;

    private Date updateTime;

    private static final long serialVersionUID = 1L;
}