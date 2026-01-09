package com.nano.oj.controller;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.google.gson.Gson;
import com.nano.oj.common.BaseResponse;
import com.nano.oj.common.ErrorCode;
import com.nano.oj.common.ResultUtils;
import com.nano.oj.exception.BusinessException;
import com.nano.oj.model.dto.problem.*;
import com.nano.oj.model.entity.Problem;
import com.nano.oj.model.entity.User;
import com.nano.oj.model.vo.ProblemVO;
import com.nano.oj.service.ProblemService;
import com.nano.oj.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

/**
 * 题目接口
 */
@RestController
@RequestMapping("/problem")
public class ProblemController {

    @Resource
    private ProblemService problemService;

    @Resource
    private UserService userService;

    private final static Gson GSON = new Gson();

    /**
     * 创建题目
     */
    @PostMapping("/add")
    public BaseResponse<Long> addProblem(@RequestBody ProblemAddRequest problemAddRequest, HttpServletRequest request) {
        if (problemAddRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        User loginUser = userService.getLoginUser(request);
        Problem problem = new Problem();
        BeanUtils.copyProperties(problemAddRequest, problem);

        // 1. 处理 JSON 字段 (judgeCase, judgeConfig)
        // ⚠️ 注意：这里不再处理 tags，因为 tags 已经不是 problem 表的字段了
        this.setJsonValues(problem, problemAddRequest.getJudgeCase(), problemAddRequest.getJudgeConfig());

        // 2. 设置可见性
        if (problemAddRequest.getVisible() != null) {
            problem.setVisible(problemAddRequest.getVisible());
        } else {
            problem.setVisible(0); // 默认公开
        }

        problem.setUserId(loginUser.getId());
        problem.setThumbNum(0);
        problem.setFavourNum(0);

        // ✅ 3. 调用 Service 的新方法，传入 tags 列表，让 Service 去维护关联表
        long newProblemId = problemService.addProblem(problem, problemAddRequest.getTags());

        return ResultUtils.success(newProblemId);
    }

    /**
     * 更新题目
     */
    @PostMapping("/update")
    public BaseResponse<Boolean> updateProblem(@RequestBody ProblemUpdateRequest problemUpdateRequest, HttpServletRequest request) {
        if (problemUpdateRequest == null || problemUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        Problem problem = new Problem();
        BeanUtils.copyProperties(problemUpdateRequest, problem);

        // 1. 处理 JSON 字段
        // ⚠️ 注意：这里也不处理 tags
        this.setJsonValues(problem, problemUpdateRequest.getJudgeCase(), problemUpdateRequest.getJudgeConfig());

        // 2. 更新可见性
        if (problemUpdateRequest.getVisible() != null) {
            problem.setVisible(problemUpdateRequest.getVisible());
        }

        User loginUser = userService.getLoginUser(request);
        long id = problemUpdateRequest.getId();
        Problem oldProblem = problemService.getById(id);
        if (oldProblem == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        if (!loginUser.getId().equals(oldProblem.getUserId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }

        // ✅ 3. 调用 Service 的新方法，同时更新题目和标签关联
        boolean result = problemService.updateProblem(problem, problemUpdateRequest.getTags());

        return ResultUtils.success(result);
    }

    /**
     * 删除题目 (逻辑保持不变)
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteProblem(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        long id = deleteRequest.getId();
        Problem oldProblem = problemService.getById(id);
        if (oldProblem == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        if (!loginUser.getId().equals(oldProblem.getUserId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // MyBatis-Plus 逻辑删除，关联表的清理可以写在 XML 里，或者手动删，这里暂时只删题目
        boolean b = problemService.removeById(id);
        return ResultUtils.success(b);
    }

    /**
     * 1. 获取题目详情（管理员/创建者用，返回完整 Entity）
     */
    @GetMapping("/get")
    public BaseResponse<Problem> getProblemById(long id, HttpServletRequest request) {
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Problem problem = problemService.getById(id);
        if (problem == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        if (!problem.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        return ResultUtils.success(problem);
    }

    /**
     * 2. 获取题目详情（做题者用，返回脱敏 VO）
     */
    @GetMapping("/get/vo")
    public BaseResponse<ProblemVO> getProblemVOById(long id, HttpServletRequest request) {
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        // 1. 查询题目信息
        Problem problem = problemService.getById(id);
        if (problem == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }

        // 2. 权限校验
        User loginUser = userService.getLoginUser(request); // 允许未登录
        boolean isAdmin = loginUser != null && userService.isAdmin(loginUser);
        if (problem.getVisible() == 1 && !isAdmin) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "该题目为私有题目，不可访问");
        }

        // 3. 构造 VO（此处需要注意：objToVo 不再能自动填充 tags，需要 Service 层支持或者单独查）
        ProblemVO problemVO = problemService.getProblemVO(problem, request);

        // 脱敏
        String judgeCaseStr = problem.getJudgeCase();
        if (StringUtils.isNotBlank(judgeCaseStr)) {
            List<JudgeCase> judgeCaseList = JSONUtil.toList(judgeCaseStr, JudgeCase.class);
            if (CollUtil.isNotEmpty(judgeCaseList)) {
                problemVO.setJudgeCase(Collections.singletonList(judgeCaseList.getFirst()));
            }
        }
        problemVO.setAnswer(null);

        return ResultUtils.success(problemVO);
    }

    /**
     * 分页获取题目列表（脱敏版）
     */
    @PostMapping("/list/page")
    public BaseResponse<Page<ProblemVO>> listProblemVOByPage(@RequestBody ProblemQueryRequest problemQueryRequest, HttpServletRequest request) {
        long current = problemQueryRequest.getCurrent();
        long pageSize = problemQueryRequest.getPageSize();
        if (pageSize > 20) pageSize = 20;

        // 1. 权限与可见性过滤
        User loginUser = userService.getLoginUser(request);
        boolean isAdmin = loginUser != null && userService.isAdmin(loginUser);
        if (!isAdmin) {
            problemQueryRequest.setVisible(0);
        }

        // 2. 构建查询条件
        QueryWrapper<Problem> queryWrapper = new QueryWrapper<>();
        Long id = problemQueryRequest.getId();
        String title = problemQueryRequest.getTitle();
        Integer visible = problemQueryRequest.getVisible();

        queryWrapper.like(StringUtils.isNotBlank(title), "title", title);
        queryWrapper.eq(id != null, "id", id);
        if (visible != null) {
            queryWrapper.eq("visible", visible);
        }

        // ⚠️ 删除了 tags 过滤逻辑
        // 因为 tags 字段已删除，且多对多查询 tags 比较复杂，
        // 如果需要按标签搜题，需要重写 Mapper XML 进行联表查询。
        // 这里为了防止报错，暂时忽略 tags 搜索条件。

        // 排序
        String sortField = problemQueryRequest.getSortField();
        String sortOrder = problemQueryRequest.getSortOrder();
        queryWrapper.orderBy(StringUtils.isNotBlank(sortField), "ascend".equals(sortOrder), sortField);

        // 3. 查询分页
        Page<Problem> problemPage = problemService.page(new Page<>(current, pageSize), queryWrapper);

        // 4. ✅ 重点：调用 Service 的 getProblemVOPage 方法
        // 这个方法会负责去 problem_tag 表和 tag 表查数据，填入 tags 字段
        Page<ProblemVO> problemVOPage = problemService.getProblemVOPage(problemPage, request);

        return ResultUtils.success(problemVOPage);
    }

    /**
     * JSON 设置辅助方法 (已移除 tags 处理)
     */
    private void setJsonValues(Problem problem, Object judgeCase, Object judgeConfig) {
        if (judgeCase != null) {
            problem.setJudgeCase(GSON.toJson(judgeCase));
        }
        if (judgeConfig != null) {
            problem.setJudgeConfig(GSON.toJson(judgeConfig));
        }
    }
}