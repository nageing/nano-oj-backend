package com.nano.oj.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nano.oj.annotation.AuthCheck;
import com.nano.oj.common.BaseResponse;
import com.nano.oj.common.ErrorCode;
import com.nano.oj.common.ResultUtils;
import com.nano.oj.constant.UserConstant;
import com.nano.oj.exception.BusinessException;
import com.nano.oj.mapper.ContestApplyMapper;
import com.nano.oj.model.dto.contest.*;
import com.nano.oj.model.dto.problem.DeleteRequest;
import com.nano.oj.model.entity.*;
import com.nano.oj.model.vo.ContestAdminVO;
import com.nano.oj.model.vo.ContestProblemSimpleVO;
import com.nano.oj.model.vo.ContestVO;
import com.nano.oj.service.*;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 比赛接口
 */
@RestController
@RequestMapping("/contest")
public class ContestController {

    @Resource
    private ContestApplyMapper contestApplyMapper;

    @Resource
    private ContestService contestService;

    @Resource
    private UserService userService;

    @Resource
    private ContestProblemService contestProblemService;

    @Resource
    private ProblemService problemService;

    /**
     * 创建比赛
     */
    @PostMapping("/add")
    public BaseResponse<Long> addContest(@RequestBody ContestAddRequest contestAddRequest, HttpServletRequest request) {
        if (contestAddRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        // 权限校验：比如只有管理员能创建，或者普通用户也能？这里假设是管理员
        if (!userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "仅管理员可创建比赛");
        }

        long contestId = contestService.addContest(contestAddRequest, loginUser);
        return ResultUtils.success(contestId);
    }

    /**
     * 删除比赛
     */
    @PostMapping("/delete")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE) // 必须管理员
    public BaseResponse<Boolean> deleteContest(@RequestBody DeleteRequest deleteRequest) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        boolean b = contestService.deleteContest(deleteRequest.getId());
        return ResultUtils.success(b);
    }

    /**
     * 更新比赛
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateContest(@RequestBody ContestUpdateRequest contestUpdateRequest) {
        if (contestUpdateRequest == null || contestUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        Contest contest = new Contest();
        BeanUtils.copyProperties(contestUpdateRequest, contest);

        // 调用 Service 处理更新
        boolean result = contestService.updateContest(contest, contestUpdateRequest.getProblemIds());
        return ResultUtils.success(result);
    }

    /**
     * 分页查询比赛列表
     */
    @PostMapping("/list/page")
    public BaseResponse<Page<ContestVO>> listContestByPage(@RequestBody ContestQueryRequest contestQueryRequest, HttpServletRequest request) {
        if (contestQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);

        long current = contestQueryRequest.getCurrent();
        long size = contestQueryRequest.getPageSize();

        QueryWrapper<Contest> queryWrapper = new QueryWrapper<>();
        String keyword = contestQueryRequest.getKeyword();
        Integer status = contestQueryRequest.getStatus();

        // 拼接查询条件
        if (StringUtils.isNotBlank(keyword)) {
            queryWrapper.and(qw -> qw.like("title", keyword).or().like("description", keyword));
        }

        // 状态筛选逻辑
        if (status != null) {
            Date now = new Date();
            switch (status) {
                case 0: // 未开始: start_time > now
                    queryWrapper.gt("start_time", now);
                    queryWrapper.orderByAsc("start_time");
                    break;
                case 1: // 进行中: start_time <= now && end_time > now
                    queryWrapper.le("start_time", now).gt("end_time", now);
                    queryWrapper.orderByAsc("end_time");
                    break;
                case 2: // 已结束: end_time <= now
                    queryWrapper.le("end_time", now);
                    queryWrapper.orderByDesc("end_time");
                    break;
                default:
            }
        } else {
            // ✅ 全部列表：正在进行 > 还没开始 > 已结束
            String customSql = "ORDER BY " +
                    "CASE " +
                    "  WHEN start_time <= NOW() AND end_time > NOW() THEN 0 " + // 正在进行
                    "  WHEN start_time > NOW() THEN 1 " +                       // 还没开始
                    "  ELSE 2 " +                                               // 已结束
                    "END ASC, " +
                    // 已结束的按结束时间倒序（刚结束的在前）
                    "CASE WHEN end_time <= NOW() THEN end_time ELSE NULL END DESC, " +
                    // 进行中的按结束时间正序（快结束的在前），未开始的按开始时间正序（快开始的在前）
                    "CASE " +
                    "  WHEN start_time <= NOW() AND end_time > NOW() THEN end_time " +
                    "  WHEN start_time > NOW() THEN start_time " +
                    "  ELSE NULL " +
                    "END ASC";

            queryWrapper.last(customSql);
        }

        Page<Contest> contestPage = contestService.page(new Page<>(current, size), queryWrapper);
        return ResultUtils.success(contestService.getContestVOPage(contestPage, loginUser));
    }
    /**
     * 新增：取消报名
     */
    @PostMapping("/apply/cancel")
    public BaseResponse<Boolean> cancelApply(@RequestBody ContestApplyRequest contestApplyRequest, HttpServletRequest request) {
        if (contestApplyRequest == null || contestApplyRequest.getContestId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        contestService.cancelApply(contestApplyRequest, loginUser);
        return ResultUtils.success(true);
    }

    /**
     * 根据 id 获取比赛详情
     */
    @GetMapping("/get")
    public BaseResponse<ContestVO> getContestById(long id, HttpServletRequest request) {
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        ContestVO contestVO = contestService.getContestById(id, loginUser);
        return ResultUtils.success(contestVO);
    }

    @GetMapping("/get/admin")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE) // 确保只有管理员能调
    public BaseResponse<ContestAdminVO> getContestByIdForAdmin(long id) {
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        // 1. 查比赛基本信息
        Contest contest = contestService.getById(id);
        if (contest == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }

        // 2. 转换成 AdminVO
        ContestAdminVO adminVO = new ContestAdminVO();
        BeanUtils.copyProperties(contest, adminVO);

        // 3. 查关联的题目 ID 列表
        // select * from contest_problem where contest_id = {id}
        QueryWrapper<ContestProblem> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("contest_id", id);
        // 如果你的表里有 'display_id' 或 'order' 字段，可以在这里 .orderByAsc("order")
        List<ContestProblem> cpList = contestProblemService.list(queryWrapper);

        // 4. 查具体的题目详情（只需要 ID 和 Title）
        List<ContestProblemSimpleVO> pList = new ArrayList<>();

        if (!cpList.isEmpty()) {
            // 拿到所有题目 ID
            List<Long> pIds = cpList.stream()
                    .map(ContestProblem::getQuestionId) // 注意检查你的实体类是 questionId 还是 problemId
                    .collect(Collectors.toList());

            // 批量查题目表
            List<Problem> problems = problemService.listByIds(pIds);

            // 组装 SimpleVO
            // 注意：listByIds 返回的顺序可能和 pIds 不一致，如果对顺序敏感，建议手动重排
            // 这里简单处理，直接映射
            pList = problems.stream().map(p -> {
                ContestProblemSimpleVO vo = new ContestProblemSimpleVO();
                vo.setId(p.getId());
                vo.setTitle(p.getTitle());
                vo.setDisplayTitle(p.getTitle()); // 默认展示标题
                return vo;
            }).collect(Collectors.toList());
        }

        adminVO.setProblems(pList);

        return ResultUtils.success(adminVO);
    }

    /**
     * 报名比赛
     */
    @PostMapping("/apply")
    public BaseResponse<Boolean> applyContest(@RequestBody ContestApplyRequest contestApplyRequest, HttpServletRequest request) {
        if (contestApplyRequest == null || contestApplyRequest.getContestId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        contestService.applyContest(contestApplyRequest, loginUser);
        return ResultUtils.success(true);
    }

    /**
     * 检查用户是否已报名
     */
    @GetMapping("/has_joined")
    public BaseResponse<Boolean> hasJoined(long contestId, HttpServletRequest request) {
        if (contestId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        // 获取当前登录用户
        User loginUser = userService.getLoginUser(request);

        // 如果没登录，肯定没报名，返回 false (不要报错，否则前端会红一片)
        if (loginUser == null) {
            return ResultUtils.success(false);
        }

        // 查询数据库
        LambdaQueryWrapper<ContestApply> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ContestApply::getContestId, contestId);
        queryWrapper.eq(ContestApply::getUserId, loginUser.getId());

        Long count = contestApplyMapper.selectCount(queryWrapper);

        return ResultUtils.success(count > 0);
    }

    /**
     * 获取比赛排行榜 (分页)
     * 地址：POST /contest/rank/list/page
     */
    @PostMapping("/rank/list/page")
    public BaseResponse<Page<ContestRanking>> getContestRank(@RequestBody ContestRankQueryRequest contestRankQueryRequest) {
        if (contestRankQueryRequest == null || contestRankQueryRequest.getContestId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        long current = contestRankQueryRequest.getCurrent();
        long size = contestRankQueryRequest.getPageSize();
        Long contestId = contestRankQueryRequest.getContestId();

        // 调用 Service
        Page<ContestRanking> page = contestService.getContestRank(contestId, current, size);
        return ResultUtils.success(page);
    }
}