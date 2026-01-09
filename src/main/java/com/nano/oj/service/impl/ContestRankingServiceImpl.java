package com.nano.oj.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nano.oj.mapper.ContestRankingMapper;
import com.nano.oj.model.entity.*;
import com.nano.oj.service.ContestProblemService;
import com.nano.oj.service.ContestRankingService;
import com.nano.oj.service.UserService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import cn.hutool.json.JSONUtil;
import com.nano.oj.model.dto.questionsubmit.JudgeInfo ;

import java.util.HashMap;
import java.util.Map;

/**
 * 比赛排行榜服务实现类
 */
@Service
public class ContestRankingServiceImpl extends ServiceImpl<ContestRankingMapper, ContestRanking>
        implements ContestRankingService {

    @Resource
    private UserService userService;

    // ✅ 注入这个 Service，用来查题目在比赛中的设定分数
    @Resource
    private ContestProblemService contestProblemService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateRanking(Contest contest, QuestionSubmit submit) {
        Long userId = submit.getUserId();
        Long questionId = submit.getQuestionId();
        Long contestId = contest.getId();
        // -------------------------------------------------------
        // 1. 查询该用户在当前比赛的排名记录
        // -------------------------------------------------------
        LambdaQueryWrapper<ContestRanking> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ContestRanking::getContestId, contestId);
        wrapper.eq(ContestRanking::getUserId, userId);
        ContestRanking ranking = this.getOne(wrapper);
        // 2. 如果是该用户第一次提交，初始化一条新记录
        if (ranking == null) {
            ranking = new ContestRanking();
            ranking.setContestId(contestId);
            ranking.setUserId(userId);
            ranking.setSolved(0);
            ranking.setTotalTime(0L);
            ranking.setTotalScore(0);
            ranking.setSubmissionInfo(new HashMap<>());

            // 查用户信息(头像、昵称)存入冗余字段，避免列表查询时联表
            User user = userService.getById(userId);
            if (user != null) {
                ranking.setRealName(user.getUserName());
                ranking.setUserAvatar(user.getUserAvatar());
            }
        }
        Map<String, ContestRanking.SubmissionInfo> submissionInfoMap = ranking.getSubmissionInfo();

        if (submissionInfoMap == null) {
            submissionInfoMap = new HashMap<>();
        }
        String key = questionId.toString();
        Object rawInfo = submissionInfoMap.get(key);

        ContestRanking.SubmissionInfo problemInfo;

        if (rawInfo == null) {
            // 情况A：之前没做过这道题，新建一个
            problemInfo = new ContestRanking.SubmissionInfo();
        } else {
            // 情况B：做过，但拿出来的是 LinkedHashMap
            // 🔥 核心修复：先把 Map 转成 JSON 字符串，再反序列化成我们要的 Bean
            String jsonStr = JSONUtil.toJsonStr(rawInfo);
            problemInfo = JSONUtil.toBean(jsonStr, ContestRanking.SubmissionInfo.class);
        }

// 确保属性不为 null (防止空指针)
        if (problemInfo.getErrorNum() == null) problemInfo.setErrorNum(0);
        if (problemInfo.getScore() == null) problemInfo.setScore(0);
        if (problemInfo.getStatus() == null) problemInfo.setStatus(0);
        // -------------------------------------------------------
        // 4. 准备基础数据
        // -------------------------------------------------------
        // 判断是否 AC
        JudgeInfo judgeInfo = JSONUtil.toBean(submit.getJudgeInfo(), JudgeInfo.class);
        boolean isAccepted = judgeInfo != null && "Accepted".equals(judgeInfo.getMessage());

        // 🟢【新增逻辑】：查询当前题目在这场比赛中的满分配置
        int problemMaxScore = 100; // 默认值
        ContestProblem contestProblem = contestProblemService.getOne(
                new LambdaQueryWrapper<ContestProblem>()
                        .eq(ContestProblem::getContestId, contestId)
                        .eq(ContestProblem::getQuestionId, questionId)
                        .select(ContestProblem::getScore) // 只查分数优化性能
        );
        if (contestProblem != null && contestProblem.getScore() != null) {
            problemMaxScore = contestProblem.getScore();
        }
        // -------------------------------------------------------
        // 5. 根据赛制分别处理
        // -------------------------------------------------------
        if (contest.getType() == 0) {
            // ==================== ACM 赛制 ====================

            // 只有当这道题【之前没有 AC】时，才更新排行榜
            // (如果已经 AC 过了，再提交只更新记录，不影响排名)
            // 如果这道题之前【没 AC 过】，才进行更新
            System.out.println("\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n##########################!!!!\n" + problemInfo.getStatus() + "\n" + isAccepted + "\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n##########################");
            if (problemInfo.getStatus() != 1) {
                System.out.println("\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n##########################\n" + problemInfo.getStatus() + "\n" + isAccepted + "\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n##########################");
                if (isAccepted) {
                    // ✅ AC 了
                    problemInfo.setStatus(1);

                    // 计算耗时
                    long passTime = (submit.getCreateTime().getTime() - contest.getStartTime().getTime()) / 1000;
                    problemInfo.setTime(passTime);

                    // 更新总榜
                    ranking.setSolved(ranking.getSolved() + 1);
                    // 只有 AC 了才把之前的罚时加到总罚时里
                    long penalty = passTime + (long) problemInfo.getErrorNum() * 20 * 60;
                    ranking.setTotalTime(ranking.getTotalTime() + penalty);
                } else {
                    problemInfo.setErrorNum(problemInfo.getErrorNum() + 1);
                    System.out.println("\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n##########################\n" + problemInfo.getErrorNum() + "\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n##########################");
                }
            }
        } else {
            // ==================== OI 赛制 ====================

            // 计算本次提交的实际得分
            int currentScore;
            if (submit.getScore() != null) {
                // 如果判题机返回了具体分数，直接用
                currentScore = submit.getScore();
            } else {
                // 如果判题机只返回了状态没返回分数，手动计算 (AC=满分, 否则=0)
                currentScore = isAccepted ? problemMaxScore : 0;
            }

            // OI 核心逻辑：取最高分
            int oldScore = problemInfo.getScore();
            if (currentScore > oldScore) {
                // 只有当“本次得分”比“历史最高分”高时，才更新
                problemInfo.setScore(currentScore);

                // 更新单题状态：如果拿到了设定满分就是 AC(1)，否则是部分分(2)
                problemInfo.setStatus(currentScore >= problemMaxScore ? 1 : 2);

                // 更新总榜得分：加上差值 (比如原来 30 分，现在 100 分，总分 +70)
                ranking.setTotalScore(ranking.getTotalScore() + (currentScore - oldScore));
            }
        }

        // -------------------------------------------------------
        // 6. 保存落库
        // -------------------------------------------------------
        submissionInfoMap.put(key, problemInfo);
        ranking.setSubmissionInfo(submissionInfoMap);

        // Mybatis-Plus 自动判断是插入还是更新
        return this.saveOrUpdate(ranking);
    }
}