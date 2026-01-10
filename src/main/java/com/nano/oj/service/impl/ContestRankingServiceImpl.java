package com.nano.oj.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nano.oj.mapper.ContestProblemMapper;
import com.nano.oj.mapper.ContestRankingMapper;
import com.nano.oj.model.entity.*;
import com.nano.oj.service.ContestProblemService;
import com.nano.oj.service.ContestRankingService;
import com.nano.oj.service.UserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import cn.hutool.json.JSONUtil;
import com.nano.oj.model.dto.questionsubmit.JudgeInfo ;

import java.util.HashMap;
import java.util.Map;

/**
 * 比赛排行榜服务实现类
 */
@Slf4j
@Service
public class ContestRankingServiceImpl extends ServiceImpl<ContestRankingMapper, ContestRanking>
        implements ContestRankingService {

    @Resource
    private UserService userService;

    // ✅ 注入这个 Service，用来查题目在比赛中的设定分数
    @Resource
    private ContestProblemService contestProblemService;

    @Resource
    private ContestProblemMapper contestProblemMapper;

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

        // 🟢【通用逻辑】：查询当前题目的配置满分 (IOI 和 OI 都需要)
        int problemMaxScore = 0;
        if (submit.getScore() != null) {
            problemMaxScore = submit.getScore(); // 如果submit自带了分数(判题机算的)
        }
        // 查比赛配置的分数覆盖
        ContestProblem contestProblem = contestProblemService.getOne(
                new LambdaQueryWrapper<ContestProblem>()
                        .eq(ContestProblem::getContestId, contestId)
                        .eq(ContestProblem::getQuestionId, questionId)
                        .select(ContestProblem::getScore)
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
            if (problemInfo.getStatus() != 1) {
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
                }
            }
        } else if (contest.getType() == 1) {
            // ==================== IOI 赛制 ====================

            // 计算本次提交的实际得分
            int currentScore;

            // 1. 如果是 AC (Accepted)，直接给该题设定的满分 (忽略判题机可能返回的0分)
            if (isAccepted) {
                currentScore = problemMaxScore;
            }
            // 2. 如果没 AC，但判题机给了分数 (针对部分分场景，如通过了50%的用例)
            else if (submit.getScore() != null && submit.getScore() > 0) {
                currentScore = submit.getScore();
            }
            // 3. 既没 AC 也没分，那就是 0 分
            else {
                currentScore = 0;
            }
            log.info("🏁 [IOI评分] 题目: {}, 判题结果: {}, 配置满分: {}, 判题机返回分: {}, ==> 最终计分: {}",
                    questionId,
                    judgeInfo.getMessage(),
                    problemMaxScore,
                    submit.getScore(),
                    currentScore);
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
        } else {
            // -------------------- OI 赛制 (取最后一次) --------------------
            // 逻辑：不管考得怎么样，直接覆盖旧成绩 (Last Submission Strategy)
            // 配合前端/Controller层的"暗箱操作"，虽然这里存了，但用户看不见

            // 1. 计算本次得分
            int currentScore;
            if (isAccepted) {
                currentScore = problemMaxScore;
            } else if (submit.getScore() != null) {
                currentScore = submit.getScore();
            } else {
                currentScore = 0;
            }

            // 2. 核心：直接覆盖 (Overwrite Strategy)
            int oldScore = problemInfo.getScore() == null ? 0 : problemInfo.getScore();

            // 更新单题信息
            problemInfo.setScore(currentScore);
            problemInfo.setStatus(currentScore >= problemMaxScore ? 1 : 2);
            // OI 也可以记录一下最后一次提交的耗时
            long passTime = (submit.getCreateTime().getTime() - contest.getStartTime().getTime()) / 1000;
            problemInfo.setTime(passTime);

            // 更新总分 (先减去旧的，再加上新的)
            int oldTotal = ranking.getTotalScore() == null ? 0 : ranking.getTotalScore();
            ranking.setTotalScore(oldTotal - oldScore + currentScore);
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