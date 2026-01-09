package com.nano.oj.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.nano.oj.common.BaseResponse;
import com.nano.oj.model.dto.contest.ContestAddRequest;
import com.nano.oj.model.dto.contest.ContestApplyRequest;
import com.nano.oj.model.dto.contest.ContestQueryRequest;
import com.nano.oj.model.entity.Contest;
import com.nano.oj.model.entity.ContestRanking;
import com.nano.oj.model.entity.User;
import com.nano.oj.model.vo.ContestVO;

import java.util.List;

public interface ContestService extends IService<Contest> {

    /**
     * 创建比赛
     */
    Long addContest(ContestAddRequest contestAddRequest, User loginUser);

    /**
     * 新比赛信息（包括题目列表）
     */
    boolean updateContest(Contest contest, List<Long> problemIds);

    /**
     * 删除比赛（包括关联数据）
     */
    boolean deleteContest(long id);

    /**
     * 分页获取比赛列表 (脱敏)
     */
    Page<ContestVO> getContestVOPage(Page<Contest> contestPage, User loginUser);

    /**
     * 比赛报名
     */
    void applyContest(ContestApplyRequest contestApplyRequest, User loginUser);

    /**
     * 获取比赛详情（包含是否已报名信息的封装）
     */
    ContestVO getContestById(long id, User loginUser);

    /**
     * 取消报名
     */
    void cancelApply(ContestApplyRequest contestApplyRequest, User loginUser);

    Page<ContestRanking> getContestRank(Long contestId, long current, long size);
}