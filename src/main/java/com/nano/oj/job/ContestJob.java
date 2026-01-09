package com.nano.oj.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nano.oj.model.entity.Contest;
import com.nano.oj.model.entity.ContestProblem;
import com.nano.oj.model.entity.Problem;
import com.nano.oj.service.ContestProblemService;
import com.nano.oj.service.ContestService;
import com.nano.oj.service.ProblemService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Component
@Slf4j
public class ContestJob {

    @Resource
    private ContestService contestService;
    @Resource
    private ContestProblemService contestProblemService;
    @Resource
    private ProblemService problemService;

    /**
     * 每分钟执行一次：扫描已结束的比赛，将其题目设为公开
     * 注意：为了避免重复更新，可以加一个字段判断比赛是否已归档，
     * 或者这里简单粗暴一点，只查 visible=1 的题目进行更新。
     */
    @Scheduled(cron = "0 0/1 * * * ?")
    @Transactional(rollbackFor = Exception.class)
    public void autoPublishContestProblems() {
        // 1. 查出所有“已结束”的比赛
        // 注意：这里假设 status=2 是已结束，或者根据 endTime < now 判断
        Date now = new Date();
        LambdaQueryWrapper<Contest> contestWrapper = new LambdaQueryWrapper<>();
        contestWrapper.le(Contest::getEndTime, now);
        // 只要结束了就查出来 (实际生产中建议加一个标记字段 is_problems_published 避免重复查)
        List<Contest> endedContests = contestService.list(contestWrapper);

        if (endedContests.isEmpty()) {
            return;
        }

        List<Long> endedContestIds = endedContests.stream().map(Contest::getId).collect(Collectors.toList());

        // 2. 查出这些比赛关联的题目ID
        LambdaQueryWrapper<ContestProblem> cpWrapper = new LambdaQueryWrapper<>();
        cpWrapper.in(ContestProblem::getContestId, endedContestIds);
        List<ContestProblem> contestProblems = contestProblemService.list(cpWrapper);

        if (contestProblems.isEmpty()) {
            return;
        }

        List<Long> problemIds = contestProblems.stream()
                .map(ContestProblem::getQuestionId) // 或 getProblemId
                .collect(Collectors.toList());

        // 3. 把这些题目中 visible=1 (私有) 的改为 visible=0 (公开)
        if (!problemIds.isEmpty()) {
            // update problem set visible = 0 where id in (...) and visible = 1
            // 只有当它目前是私有时才更新，防止重复更新
            boolean updateResult = problemService.lambdaUpdate()
                    .in(Problem::getId, problemIds)
                    .eq(Problem::getVisible, 1)
                    .set(Problem::getVisible, 0)
                    .update();

            if (updateResult) {
                log.info("定时任务: 已将 {} 个比赛题目转为公开", problemIds.size());
            }
        }
    }
}