package com.nano.oj.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.nano.oj.model.dto.contest.ContestApplyRequest;
import com.nano.oj.model.entity.ContestApply;
import com.nano.oj.model.entity.User;

public interface ContestApplyService extends IService<ContestApply> {
    void applyContest(ContestApplyRequest contestApplyRequest, User loginUser);
}
