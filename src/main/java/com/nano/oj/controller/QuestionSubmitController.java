package com.nano.oj.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nano.oj.common.BaseResponse;
import com.nano.oj.common.ErrorCode;
import com.nano.oj.common.ResultUtils;
import com.nano.oj.exception.BusinessException;
import com.nano.oj.model.dto.questionsubmit.QuestionRunRequest;
import com.nano.oj.model.dto.questionsubmit.QuestionSubmitAddRequest;
import com.nano.oj.model.dto.questionsubmit.QuestionSubmitQueryRequest;
import com.nano.oj.model.entity.QuestionSubmit;
import com.nano.oj.model.entity.User;
import com.nano.oj.model.vo.QuestionSubmitVO;
import com.nano.oj.service.QuestionSubmitService;
import com.nano.oj.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 题目提交接口
 */
@RestController
@RequestMapping("/problem_submit")
public class QuestionSubmitController {

    @Resource
    private QuestionSubmitService questionSubmitService;

    @Resource
    private UserService userService;

    /**
     * 提交代码
     */
    @PostMapping("/")
    public BaseResponse<Long> doSubmit(@RequestBody QuestionSubmitAddRequest questionSubmitAddRequest,
                                       HttpServletRequest request) {
        if (questionSubmitAddRequest == null || questionSubmitAddRequest.getProblemId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        final User loginUser = userService.getLoginUser(request);

        // 调用 QuestionSubmitService
        long questionSubmitId = questionSubmitService.doQuestionSubmit(questionSubmitAddRequest, loginUser);

        return ResultUtils.success(questionSubmitId);
    }

    /**
     * 分页获取提交列表
     */
    @PostMapping("/list/page")
    public BaseResponse<Page<QuestionSubmitVO>> listProblemSubmitByPage(
            @RequestBody QuestionSubmitQueryRequest questionSubmitQueryRequest,
            HttpServletRequest request) {
        long current = questionSubmitQueryRequest.getCurrent();
        long size = questionSubmitQueryRequest.getPageSize();

        // 1. 获取分页数据
        Page<QuestionSubmit> questionSubmitPage = questionSubmitService.page(new Page<>(current, size),
                getQueryWrapper(questionSubmitQueryRequest));

        // 2. 获取当前登录用户 (用来判断是否脱敏)
        User loginUser = userService.getLoginUser(request);

        // 3. 转 VO
        return ResultUtils.success(questionSubmitService.getProblemSubmitVOPage(questionSubmitPage, loginUser));
    }

    /**
     * 补全的方法：获取查询条件
     */
    private QueryWrapper<QuestionSubmit> getQueryWrapper(QuestionSubmitQueryRequest searchRequest) {
        QueryWrapper<QuestionSubmit> queryWrapper = new QueryWrapper<>();
        if (searchRequest == null) {
            return queryWrapper;
        }
        String language = searchRequest.getLanguage();
        Integer status = searchRequest.getStatus();
        Long questionId = searchRequest.getQuestionId();
        Long userId = searchRequest.getUserId();
        String sortField = searchRequest.getSortField();
        String sortOrder = searchRequest.getSortOrder();

        // 拼接查询条件
        queryWrapper.eq(StringUtils.isNotBlank(language), "language", language);
        queryWrapper.eq(userId != null, "user_id", userId);
        queryWrapper.eq(questionId != null, "question_id", questionId);
        queryWrapper.eq(status != null, "status", status);

        // 排序
        queryWrapper.orderBy(StringUtils.isNotBlank(sortField), "ascend".equals(sortOrder), sortField);
        // 默认按创建时间倒序（最新的在最前面）
        if (StringUtils.isBlank(sortField)) {
            queryWrapper.orderByDesc("create_time");
        }

        return queryWrapper;
    }

    /**
     * 运行代码 (自测)
     */
    @PostMapping("/run")
    public BaseResponse<QuestionSubmitVO> doRun(@RequestBody QuestionRunRequest runRequest,
                                                HttpServletRequest request) {
        if (runRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 登录校验
        User loginUser = userService.getLoginUser(request);

        QuestionSubmitVO res = questionSubmitService.doQuestionRun(runRequest, loginUser);
        return ResultUtils.success(res);
    }
}