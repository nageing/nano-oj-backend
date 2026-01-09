package com.nano.oj.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.nano.oj.model.entity.Contest;
import com.nano.oj.model.entity.ContestRanking;
import com.nano.oj.model.entity.QuestionSubmit;

/**
 * 比赛排行榜服务接口
 */
public interface ContestRankingService extends IService<ContestRanking> {
    /**
     * 更新排行榜（当提交判题结束后调用）
     * @param contest 比赛信息
     * @param questionSubmit 提交记录（包含判题结果、分数等）
     */
    boolean updateRanking(Contest contest, QuestionSubmit questionSubmit);
}