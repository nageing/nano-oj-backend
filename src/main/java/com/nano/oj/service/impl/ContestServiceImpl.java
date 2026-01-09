package com.nano.oj.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nano.oj.common.ErrorCode;
import com.nano.oj.exception.BusinessException;
import com.nano.oj.mapper.ContestApplyMapper;
import com.nano.oj.mapper.ContestMapper;
import com.nano.oj.mapper.ContestProblemMapper;
import com.nano.oj.mapper.QuestionSubmitMapper;
import com.nano.oj.model.dto.contest.ContestAddRequest;
import com.nano.oj.model.dto.contest.ContestApplyRequest;
import com.nano.oj.model.entity.*;
import com.nano.oj.model.vo.ContestVO;
import com.nano.oj.model.vo.ProblemVO;
import com.nano.oj.service.ContestService;
import com.nano.oj.service.ProblemService;
import com.nano.oj.service.UserService;
import jakarta.annotation.Resource;
import cn.hutool.core.collection.CollUtil;

// 使用 Spring 自带工具类
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    private ContestApplyMapper contestApplyMapper; // ✅ 确保注入，否则报空指针

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

        // 2. 获取题目 ID 列表 (这是关键修正点！！！)
        List<Long> problemIds = contestAddRequest.getProblemIds();

        // 3. 插入题目关联
        if (CollUtil.isNotEmpty(problemIds)) {
            // 循环插入并设置 displayId
            for (int i = 0; i < problemIds.size(); i++) {
                ContestProblem cp = new ContestProblem();
                cp.setContestId(contest.getId());
                cp.setQuestionId(problemIds.get(i));
                cp.setDisplayId(i + 1); // 设置次序：1, 2, 3...
                contestProblemMapper.insert(cp);
            }
        }

        return contest.getId();
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
        // 1. 基础信息查询
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
        // ================== ✅ 新增：强制矫正比赛状态 ==================
        // 数据库里的 status 可能不准（比如定时任务没跑），所以我们按时间现场算一遍
        long now1 = System.currentTimeMillis();
        long start = contest.getStartTime().getTime();
        long end = contest.getEndTime().getTime();

        if (now1 < start) {
            contestVO.setStatus(0); // 未开始
        } else if (now1 > end) {
            contestVO.setStatus(2); // 已结束
        } else {
            contestVO.setStatus(1); // 进行中
        }
        // 2. 权限判断
        Date now2 = new Date();
        boolean isAdmin = loginUser != null && (userService.isAdmin(loginUser) || contest.getUserId().equals(loginUser.getId()));
        boolean isEnded = contest.getStatus() == 2 || now2.after(contest.getEndTime());
        boolean canSeeProblems = isAdmin || isEnded || (hasJoined && contest.getStatus() != 0);

        if (canSeeProblems) {
            // A. 查关联表 (决定最终顺序)
            LambdaQueryWrapper<ContestProblem> cpWrapper = new LambdaQueryWrapper<>();
            cpWrapper.eq(ContestProblem::getContestId, id);
            cpWrapper.orderByAsc(ContestProblem::getDisplayId); // 按题号排序
            List<ContestProblem> contestProblems = contestProblemMapper.selectList(cpWrapper);

            if (!CollectionUtils.isEmpty(contestProblems)) {
                // 提取 ID 列表
                List<Long> questionIds = contestProblems.stream()
                        .map(ContestProblem::getQuestionId)
                        .collect(Collectors.toList());

                // B. 查题目详情并转 Map
                List<Problem> problems = problemService.listByIds(questionIds);
                Map<Long, Problem> problemMap = problems.stream()
                        .collect(Collectors.toMap(Problem::getId, Function.identity()));

                List<ProblemVO> problemVOList = new ArrayList<>();

                // C. 遍历并计算状态
                for (ContestProblem cp : contestProblems) {
                    Problem problem = problemMap.get(cp.getQuestionId());
                    if (problem == null) continue;

                    ProblemVO problemVO = ProblemVO.objToVo(problem);
                    problemVO.setUserStatus(0); // 默认未开始

                    // ----------------- ✅ 核心修改：基于时间的判断逻辑 -----------------
                    if (loginUser != null) {
                        LambdaQueryWrapper<QuestionSubmit> baseQuery = new LambdaQueryWrapper<>();
                        baseQuery.eq(QuestionSubmit::getQuestionId, problem.getId());
                        baseQuery.eq(QuestionSubmit::getUserId, loginUser.getId());

                        // 如果比赛【没结束】(正在进行)，只查【比赛时间段内】的提交
                        if (!isEnded) {
                            baseQuery.ge(QuestionSubmit::getCreateTime, contest.getStartTime());
                            baseQuery.le(QuestionSubmit::getCreateTime, contest.getEndTime());
                            // ❌ 移除了 .eq(QuestionSubmit::getContestId, id)
                            // 只要是在比赛时间内提交的，不管是从哪提交的，都算数
                        }
                        // 否则(已结束)，不加时间限制，查全部历史

                        // 1. 查 AC
                        LambdaQueryWrapper<QuestionSubmit> successQuery = baseQuery.clone();
                        successQuery.eq(QuestionSubmit::getStatus, 2);
                        successQuery.like(QuestionSubmit::getJudgeInfo, "\"AC\"");

                        if (questionSubmitMapper.selectCount(successQuery) > 0) {

                            problemVO.setUserStatus(1); // ✅ 通过
                        } else {
                            // 2. 查是否尝试过
                            if (questionSubmitMapper.selectCount(baseQuery) > 0) {
                                problemVO.setUserStatus(2); // ❌ 错误
                            }
                        }
                    }
                    // -------------------------------------------------------------

                    problemVOList.add(problemVO);
                }
                contestVO.setProblems(problemVOList);
            }
        }

        System.out.println("\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n####################################" + contestVO.getStatus() + "#################################\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n");

        return contestVO;
    }
    /**
     * ✅ 实现更新比赛
     * 策略：更新基本信息 -> 删除旧题目关联 -> 插入新题目关联
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateContest(Contest contest, List<Long> problemIds) {
        // 1. 更新比赛基本信息
        boolean result = this.updateById(contest);
        if (!result) return false;

        // 2. 更新题目关联
        if (problemIds != null) {
            // 2.1 删除该比赛原有的所有关联题目
            LambdaQueryWrapper<ContestProblem> deleteWrapper = new LambdaQueryWrapper<>();
            deleteWrapper.eq(ContestProblem::getContestId, contest.getId());
            contestProblemMapper.delete(deleteWrapper);

            // 2.2 插入新的题目列表
            if (CollUtil.isNotEmpty(problemIds)) {
                // ✅ 改用普通 for 循环，方便获取索引 i 来设置 displayId
                for (int i = 0; i < problemIds.size(); i++) {
                    ContestProblem cp = new ContestProblem();
                    cp.setContestId(contest.getId());
                    cp.setQuestionId(problemIds.get(i));

                    // ✨✨✨ 关键修复点 ✨✨✨
                    // 必须设置 displayId，否则数据库会报错
                    cp.setDisplayId(i + 1);

                    contestProblemMapper.insert(cp);
                }
            }
        }
        return true;
    }

    /**
     * ✅ 实现删除比赛
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
}