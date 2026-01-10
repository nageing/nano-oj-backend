package com.nano.oj.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nano.oj.common.ErrorCode;
import com.nano.oj.common.UserHolder;
import com.nano.oj.exception.BusinessException;
import com.nano.oj.mapper.ContestApplyMapper;
import com.nano.oj.mapper.ContestMapper;
import com.nano.oj.mapper.ContestProblemMapper;
import com.nano.oj.mapper.QuestionSubmitMapper;
import com.nano.oj.model.dto.contest.ContestAddRequest;
import com.nano.oj.model.dto.contest.ContestApplyRequest;
import com.nano.oj.model.dto.contest.ContestUpdateRequest;
import com.nano.oj.model.entity.*;
import com.nano.oj.model.vo.ContestVO;
import com.nano.oj.model.vo.ProblemVO;
import com.nano.oj.service.ContestService;
import com.nano.oj.service.ProblemService;
import com.nano.oj.service.UserService;
import com.nano.oj.service.ContestRankingService;
import jakarta.annotation.Resource;
import cn.hutool.core.collection.CollUtil;

// 使用 Spring 自带工具类
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ContestServiceImpl extends ServiceImpl<ContestMapper, Contest> implements ContestService {

    @Resource
    private ContestProblemMapper contestProblemMapper;

    @Resource
    private ProblemService problemService;
    @Resource
    private UserService userService;
    // 2. 注入提交表的 Mapper
    @Resource
    private QuestionSubmitMapper questionSubmitMapper;

    @Resource
    private ContestApplyMapper contestApplyMapper;

    @Resource
    private ContestRankingService contestRankingService;

    /**
     * 创建比赛
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long addContest(ContestAddRequest contestAddRequest, User loginUser) {
        // 1. 创建并保存比赛基本信息
        Contest contest = new Contest();
        BeanUtils.copyProperties(contestAddRequest, contest);
        contest.setUserId(loginUser.getId());

        // 注意：如果你的 Contest 实体里有 status 字段，建议设置初始值
        // contest.setStatus(0);

        boolean result = this.save(contest);
        if (!result) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "创建比赛失败");
        }

        // 2. 获取题目 以及分数 列表
        List<ContestAddRequest.ContestProblemItem> problems = contestAddRequest.getProblems();

        // 3. 插入题目关联
        if (CollUtil.isNotEmpty(problems)) {
            // 循环插入并设置 displayId
            for (int i = 0; i < problems.size(); i++) {
                ContestProblem cp = new ContestProblem();
                cp.setContestId(contest.getId());
                cp.setQuestionId(problems.get(i).getId());
                cp.setScore(problems.get(i).getScore() == null ? 100 : problems.get(i).getScore());
                cp.setDisplayId(i + 1); // 设置次序：1, 2, 3...
                // 如果前端没传，就兜底用原标题
                String finalTitle = StringUtils.isNotBlank(problems.get(i).getDisplayTitle()) ? problems.get(i).getDisplayTitle() : problems.get(i).getTitle();
                cp.setDisplayTitle(finalTitle);
                contestProblemMapper.insert(cp);
            }
        }

        return contest.getId();
    }


    /**
     * 更新比赛
     * 策略：更新基本信息 -> 删除旧题目关联 -> 插入新题目关联
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateContest(ContestUpdateRequest contestUpdateRequest) {
        Long contestId = contestUpdateRequest.getId();

        // 1. 更新比赛基本信息
        Contest contest = new Contest();
        BeanUtils.copyProperties(contestUpdateRequest, contest);

        if (contest.getId() == null) {
            log.error("❌ [更新失败] 比赛ID为空");
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        boolean result = this.updateById(contest);

        if (!result) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "更新比赛失败");
        }

        // 2. 更新题目列表
        List<ContestAddRequest.ContestProblemItem> problems = contestUpdateRequest.getProblems();

        if (problems == null) {
            // warn 级别表示警告，需要注意但不是错误
            log.warn("⚠️ [跳过题目更新] problems字段为null，请检查前端传参是否正确");
        } else {

            // A. 删除旧关联
            QueryWrapper<ContestProblem> deleteWrapper = new QueryWrapper<>();
            deleteWrapper.eq("contest_id", contestId);
            contestProblemMapper.delete(deleteWrapper);

            // B. 插入新关联
            if (CollUtil.isNotEmpty(problems)) {
                List<ContestProblem> newEntities = new ArrayList<>();
                for (int i = 0; i < problems.size(); i++) {
                    ContestAddRequest.ContestProblemItem item = problems.get(i);

                    ContestProblem cp = new ContestProblem();
                    cp.setContestId(contestId);
                    cp.setQuestionId(item.getId());
                    cp.setDisplayId(i + 1);
                    String finalTitle = StringUtils.isNotBlank(item.getDisplayTitle()) ? item.getDisplayTitle() : item.getTitle();
                    cp.setDisplayTitle(finalTitle);

                    // 处理分数
                    if (item.getScore() != null) {
                        cp.setScore(item.getScore());
                    } else {
                        log.warn("⚠️ [分数缺失] 题目ID: {} 未设置分数，使用默认值 100", item.getId());
                        cp.setScore(100);
                    }

                    newEntities.add(cp);
                }

                // 批量插入
                // 如果你有 saveBatch 方法最好，没有就循环插
                int insertCount = 0;
                for (ContestProblem cp : newEntities) {
                    contestProblemMapper.insert(cp);
                    insertCount++;
                }
                log.info("✅ [插入新数据] 成功插入条数: {}", insertCount);

            } else {
                log.info("ℹ️ [题目清空] 前端传入了空列表，比赛题目已被清空");
            }
        }

        log.info("✅ [更新结束] updateContest 执行完毕");
        return true;
    }

    /**
     * 分页获取 VO (修正版：带 loginUser 参数)
     */
    @Override
    public Page<ContestVO> getContestVOPage(Page<Contest> contestPage, User loginUser) { // ✅ 这里加上了 loginUser
        if (contestPage == null) return null;

        Page<ContestVO> contestVOPage = new Page<>(contestPage.getCurrent(), contestPage.getSize(), contestPage.getTotal());
        List<Contest> contestList = contestPage.getRecords();

        if (CollectionUtils.isEmpty(contestList)) {
            return contestVOPage;
        }

        List<ContestVO> contestVOList = contestList.stream().map(contest -> {
            ContestVO contestVO = ContestVO.objToVo(contest);

            // 1. 填充创建人
            Long userId = contest.getUserId();
            User user = userService.getById(userId);
            contestVO.setCreatorName(user != null ? user.getUserName() : "官方");

            // 2. ✅ 核心修复：判断当前用户在列表页的报名状态
            if (loginUser != null) {
                LambdaQueryWrapper<ContestApply> queryWrapper = new LambdaQueryWrapper<>();
                queryWrapper.eq(ContestApply::getContestId, contest.getId());
                queryWrapper.eq(ContestApply::getUserId, loginUser.getId());
                Long count = contestApplyMapper.selectCount(queryWrapper);
                contestVO.setHasJoined(count > 0);
            } else {
                contestVO.setHasJoined(false);
            }

            return contestVO;
        }).collect(Collectors.toList());

        contestVOPage.setRecords(contestVOList);
        return contestVOPage;
    }

    /**
     * 报名比赛 (逻辑补全)
     */
    @Override
    public void applyContest(ContestApplyRequest contestApplyRequest, User loginUser) {
        Long contestId = contestApplyRequest.getContestId();
        String password = contestApplyRequest.getPassword();

        // 1. 检查比赛是否存在
        Contest contest = this.getById(contestId);
        if (contest == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "比赛不存在");
        }
        // 2. 检查密码
        if (StringUtils.isNotBlank(contest.getPwd()) && !contest.getPwd().equals(password)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "比赛密码错误");
        }
        // 3. 检查是否重复报名
        LambdaQueryWrapper<ContestApply> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ContestApply::getContestId, contestId);
        queryWrapper.eq(ContestApply::getUserId, loginUser.getId());
        Long count = contestApplyMapper.selectCount(queryWrapper);
        if (count > 0) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "您已报名过该比赛");
        }
        // 4. 写入报名表
        ContestApply contestApply = new ContestApply();
        contestApply.setContestId(contestId);
        contestApply.setUserId(loginUser.getId());
        int insert = contestApplyMapper.insert(contestApply);
        if (insert <= 0) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "报名失败");
        }
    }

    /**
     * 取消报名 (逻辑补全)
     */
    @Override
    public void cancelApply(ContestApplyRequest contestApplyRequest, User loginUser) {
        Long contestId = contestApplyRequest.getContestId();

        // 1. 检查比赛是否存在
        Contest contest = this.getById(contestId);
        if (contest == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "比赛不存在");
        }

        // 2. 如果比赛已经开始或结束，不允许取消 (根据需求调整)
        if (contest.getStatus() != 0) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "比赛已开始，无法取消报名");
        }

        // 3. 删除报名记录
        LambdaQueryWrapper<ContestApply> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ContestApply::getContestId, contestId);
        wrapper.eq(ContestApply::getUserId, loginUser.getId());

        int delete = contestApplyMapper.delete(wrapper);
        if (delete <= 0) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "取消失败，您可能尚未报名");
        }
    }

    /**
     * 获取详情
     */
    @Override
    public ContestVO getContestById(long id, User loginUser) {
        // ================== 1. 基础信息查询 ==================
        Contest contest = this.getById(id);
        if (contest == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        ContestVO contestVO = ContestVO.objToVo(contest);

        // 填充创建人
        User creator = userService.getById(contest.getUserId());
        contestVO.setCreatorName(creator != null ? creator.getUserName() : "官方");

        // 计算是否已报名
        boolean hasJoined = false;
        if (loginUser != null) {
            LambdaQueryWrapper<ContestApply> applyWrapper = new LambdaQueryWrapper<>();
            applyWrapper.eq(ContestApply::getContestId, id);
            applyWrapper.eq(ContestApply::getUserId, loginUser.getId());
            hasJoined = contestApplyMapper.selectCount(applyWrapper) > 0;
        }
        contestVO.setHasJoined(hasJoined);

        // ================== ✅ 2. 强制矫正比赛状态 (基于时间) ==================
        long now = System.currentTimeMillis();
        long start = contest.getStartTime().getTime();
        long end = contest.getEndTime().getTime();

        if (now < start) {
            contestVO.setStatus(0); // 未开始
        } else if (now > end) {
            contestVO.setStatus(2); // 已结束
        } else {
            contestVO.setStatus(1); // 进行中
        }

        // ================== 3. 权限与题目获取 ==================
        boolean isAdmin = loginUser != null && (userService.isAdmin(loginUser) || contestVO.getUserId().equals(loginUser.getId()));
        boolean isEnded = contestVO.getStatus() == 2;
        boolean isRunning = contestVO.getStatus() == 1; // 标记是否进行中
        boolean canSeeProblems = isAdmin || isEnded || (hasJoined && contestVO.getStatus() != 0);

        // 如果没有权限看题，直接返回空题目列表
        if (!canSeeProblems) {
            contestVO.setProblems(new ArrayList<>());
            return contestVO;
        }

        // A. 查关联表 (决定题目顺序)
        LambdaQueryWrapper<ContestProblem> cpWrapper = new LambdaQueryWrapper<>();
        cpWrapper.eq(ContestProblem::getContestId, id);
        cpWrapper.orderByAsc(ContestProblem::getDisplayId);
        List<ContestProblem> contestProblems = contestProblemMapper.selectList(cpWrapper);

        if (CollectionUtils.isEmpty(contestProblems)) {
            contestVO.setProblems(new ArrayList<>());
            return contestVO;
        }

        // B. 查题目详情并转 Map (避免循环查库)
        List<Long> questionIds = contestProblems.stream().map(ContestProblem::getQuestionId).collect(Collectors.toList());
        List<Problem> problems = problemService.listByIds(questionIds);
        Map<Long, Problem> problemMap = problems.stream().collect(Collectors.toMap(Problem::getId, Function.identity()));

        List<ProblemVO> problemVOList = new ArrayList<>();

        // C. 遍历计算状态
        for (ContestProblem cp : contestProblems) {
            Problem problem = problemMap.get(cp.getQuestionId());
            if (problem == null) continue;

            ProblemVO problemVO = ProblemVO.objToVo(problem);

            if (StringUtils.isNotBlank(cp.getDisplayTitle())) {
                problemVO.setDisplayTitle(cp.getDisplayTitle());
            } else {
                problemVO.setDisplayTitle(problem.getTitle()); // 兜底：如果没有自定义，就用原标题
            }

            // ✅ 补充：设置赛内展示题号 (如 A, B, C...)
            problemVO.setDisplayId(cp.getDisplayId());
            // ✅ 补充：设置分数
            problemVO.setScore(cp.getScore() != null ? cp.getScore() : 100);

            problemVO.setUserStatus(0); // 默认未开始

            // ----------------- 计算真实状态 (Real Status) -----------------
            if (loginUser != null) {
                LambdaQueryWrapper<QuestionSubmit> baseQuery = new LambdaQueryWrapper<>();
                baseQuery.eq(QuestionSubmit::getQuestionId, problem.getId());
                baseQuery.eq(QuestionSubmit::getUserId, loginUser.getId());

                // 建议：加上 contestId 限制，确保只统计本场比赛的提交
                // 如果你的逻辑允许引用题库里的提交，可以去掉这行，但通常比赛是隔离的
                baseQuery.eq(QuestionSubmit::getContestId, id);

                // 如果比赛正在进行，严格限制时间 (防止拿出以前练习的记录)
                if (isRunning) {
                    baseQuery.ge(QuestionSubmit::getCreateTime, contest.getStartTime());
                    baseQuery.le(QuestionSubmit::getCreateTime, contest.getEndTime());
                }

                // 1. 查 AC
                LambdaQueryWrapper<QuestionSubmit> successQuery = baseQuery.clone();
                successQuery.eq(QuestionSubmit::getStatus, 2); // 假设 2 是判题完成
                successQuery.like(QuestionSubmit::getJudgeInfo, "\"Accepted\"");

                if (questionSubmitMapper.selectCount(successQuery) > 0) {
                    problemVO.setUserStatus(1); // ✅ 真实状态：通过
                } else {
                    // 2. 查是否尝试过
                    if (questionSubmitMapper.selectCount(baseQuery) > 0) {
                        problemVO.setUserStatus(2); // ❌ 真实状态：错误/尝试过
                    }
                }
            }
            problemVOList.add(problemVO);
        }

        // ================== ✅ 4. 核心修改：OI 赛制脱敏逻辑 ==================
        // 如果是 (OI赛制) && (进行中) && (非管理员)
        // 强制把所有 "做过(1或2)" 的状态改为 "10(已提交)"
        boolean isOi = contest.getType() != null && contest.getType() == 2;

        if (isOi && isRunning && !isAdmin) {
            for (ProblemVO vo : problemVOList) {
                if (vo.getUserStatus() != null && vo.getUserStatus() != 0) {
                    vo.setUserStatus(10); // 🎭 掩盖真实状态，前端显示黄色问号
                }
            }
        }

        contestVO.setProblems(problemVOList);
        return contestVO;
    }


    /**
     * 实现删除比赛
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteContest(long id) {
        // 1. 删除关联的题目映射
        LambdaQueryWrapper<ContestProblem> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(ContestProblem::getContestId, id);
        contestProblemMapper.delete(deleteWrapper);

        // 2. 删除比赛本身
        return this.removeById(id);
    }


    @Override
    public Page<ContestRanking> getContestRank(Long contestId, long current, long size) {
        Contest contest = this.getById(contestId);
        if (contest == null) throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);

        // OI 赛制封榜逻辑 (仅针对 Type=2)
        boolean isOi = (contest.getType() == 2);
        long now = System.currentTimeMillis();
        boolean isRunning = contest.getStatus() == 1;
        if (contest.getStartTime() != null && contest.getEndTime() != null) {
            long start = contest.getStartTime().getTime();
            long end = contest.getEndTime().getTime();
            isRunning = (now >= start && now < end); // 只要在时间范围内，就是进行中
        }

        // 获取当前用户并判断是否管理员 (伪代码)
        User loginUser = UserHolder.getUser();
        boolean isAdmin = userService.isAdmin(loginUser);

        if (isOi && isRunning && !isAdmin) {
            // OI赛制进行中，普通用户看不了榜
            return new Page<>(current, size);
        }

        // 构建查询条件
        QueryWrapper<ContestRanking> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("contest_id", contestId);

        // 根据赛制决定排序规则 (利用数据库索引)
        if (contest.getType() == 0) {
            // ACM: 解题数降序，罚时升序
            queryWrapper.orderByDesc("solved").orderByAsc("total_time");
        } else {
            // IOI: 总分降序
            queryWrapper.orderByDesc("total_score");
        }

        // 这行代码现在应该能正常编译通过了
        Page<ContestRanking> page = contestRankingService.page(new Page<>(current, size), queryWrapper);

        // 填充排名序号 (current - 1) * size + index + 1
        long startRank = (current - 1) * size + 1;
        List<ContestRanking> records = page.getRecords();
        for (int i = 0; i < records.size(); i++) {
            records.get(i).setRank((int) ((current - 1) * size + i + 1));
        }

        return page;
    }
}