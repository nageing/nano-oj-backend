package com.nano.oj.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;
import java.util.Map;

/**
 * 比赛排行榜
 * @TableName contest_ranking
 */
@TableName(value = "contest_ranking", autoResultMap = true)
@Data
public class ContestRanking implements Serializable {
    /**
     * id
     */
    @TableId(type = IdType.ASSIGN_ID)
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
     * 用户昵称(冗余)
     */
    private String realName;

    /**
     * 用户头像(冗余)
     */
    private String userAvatar;

    /**
     * 解题数量(ACM核心)
     */
    private Integer solved;

    /**
     * 总罚时(ACM次要)
     */
    private Long totalTime;

    /**
     * 总得分(OI核心)
     */
    private Integer totalScore;

    /**
     * 每道题的提交详情(JSON格式)
     * Key: questionId, Value: SubmissionInfo
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, SubmissionInfo> submissionInfo;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * 排行榜排名 (数据库不存，查询时计算)
     */
    @TableField(exist = false)
    private Integer rank;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

    /**
     * 内部静态类：单题详情
     */
    @Data
    public static class SubmissionInfo implements Serializable {
        private Integer status;   // 1=AC, 2=WA/TLE...
        private Integer score;    // OI得分
        private Long time;        // ACM耗时(秒)
        private Integer errorNum; // 错误次数
    }
}