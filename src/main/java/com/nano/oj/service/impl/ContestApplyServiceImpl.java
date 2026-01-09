package com.nano.oj.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nano.oj.common.ErrorCode;
import com.nano.oj.exception.BusinessException;
import com.nano.oj.mapper.ContestApplyMapper;
import com.nano.oj.model.dto.contest.ContestApplyRequest;
import com.nano.oj.model.entity.Contest;
import com.nano.oj.model.entity.ContestApply;
import com.nano.oj.model.entity.User;
import com.nano.oj.service.ContestApplyService;
import com.nano.oj.service.ContestService;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

/**
 * 报名比赛
 */
@Service
public class ContestApplyServiceImpl extends ServiceImpl<ContestApplyMapper, ContestApply> implements ContestApplyService {

    @Resource
    private ContestService contestService; // 必须注入这个，否则无法查比赛信息

    @Override
    public void applyContest(ContestApplyRequest contestApplyRequest, User loginUser) {
        Long contestId = contestApplyRequest.getContestId();
        String password = contestApplyRequest.getPassword();

        // 1. 检查比赛是否存在
        // 注意：这里不能用 this.getById，因为 this 是查 ContestApply 表的
        Contest contest = contestService.getById(contestId);
        if (contest == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "比赛不存在");
        }

        // 2. 检查密码 (逻辑不变)
        if (StringUtils.isNotBlank(contest.getPwd()) && !contest.getPwd().equals(password)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "比赛密码错误");
        }

        // 3. 检查是否重复报名
        LambdaQueryWrapper<ContestApply> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ContestApply::getContestId, contestId);
        queryWrapper.eq(ContestApply::getUserId, loginUser.getId());

        // ✅ 修正：直接用 Service 自带的 count 方法
        long count = this.count(queryWrapper);
        if (count > 0) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "您已报名过该比赛");
        }

        // 4. 写入报名表
        ContestApply contestApply = new ContestApply();
        contestApply.setContestId(contestId);
        contestApply.setUserId(loginUser.getId());

        // ✅ 修正：直接用 Service 自带的 save 方法
        boolean saveResult = this.save(contestApply);
        if (!saveResult) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "报名失败");
        }
    }
}
