package com.nano.oj.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nano.oj.common.BaseResponse;
import com.nano.oj.common.ResultUtils;
import com.nano.oj.model.entity.ContestApply;
import com.nano.oj.model.entity.User;
import com.nano.oj.service.ContestApplyService;
import com.nano.oj.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/contest_apply")
public class ContestApplyController {

    @Resource
    private ContestApplyService contestApplyService;

    @Resource
    private UserService userService;

    /**
     * 检查用户是否已报名
     */
    @GetMapping("/has_joined")
    public BaseResponse<Boolean> hasJoinedContest(long contestId, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);

        // 构造查询条件
        LambdaQueryWrapper<ContestApply> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ContestApply::getContestId, contestId);
        queryWrapper.eq(ContestApply::getUserId, loginUser.getId());

        // ✅ 修正：使用注入的 service 实例调用
        long count = contestApplyService.count(queryWrapper);

        return ResultUtils.success(count > 0);
    }
}