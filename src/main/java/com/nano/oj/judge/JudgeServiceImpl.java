package com.nano.oj.judge;

import cn.hutool.json.JSONUtil;
import com.nano.oj.common.ErrorCode;
import com.nano.oj.config.MqConfig;
import com.nano.oj.exception.BusinessException;
import com.nano.oj.judge.codesandbox.CodeSandbox;
import com.nano.oj.judge.codesandbox.model.ExecuteCodeRequest;
import com.nano.oj.judge.codesandbox.model.ExecuteCodeResponse;
import com.nano.oj.model.dto.problem.JudgeCase;
import com.nano.oj.model.dto.problem.JudgeConfig;
import com.nano.oj.model.dto.questionsubmit.JudgeInfo;
import com.nano.oj.model.entity.ContestProblem; // 引入实体
import com.nano.oj.model.entity.Problem;
import com.nano.oj.model.entity.QuestionSubmit;
import com.nano.oj.service.ContestProblemService; // 引入服务
import com.nano.oj.service.ProblemService;
import com.nano.oj.service.QuestionSubmitService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper; // 引入 MP Wrapper
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class JudgeServiceImpl implements JudgeService {

    @Resource
    private QuestionSubmitService questionSubmitService;

    @Resource
    private ProblemService problemService;

    @Resource
    private ContestProblemService contestProblemService; // ✅ 1. 注入比赛题目服务

    @Resource
    private CodeSandbox dockerCodeSandbox;

    @Resource
    private RabbitTemplate rabbitTemplate;

    @Override
    public QuestionSubmit doJudge(long questionSubmitId) {
        // 1. 获取提交记录
        QuestionSubmit questionSubmit = questionSubmitService.getById(questionSubmitId);
        if (questionSubmit == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "提交信息不存在");
        }

        // 2. 获取题目信息
        Long problemId = questionSubmit.getQuestionId();
        Problem problem = problemService.getById(problemId);
        if (problem == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "题目不存在");
        }

        // 3. 校验状态
        if (!questionSubmit.getStatus().equals(0)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "题目正在判题中");
        }

        // 4. 更新状态为“判题中”
        QuestionSubmit updateQuestionSubmit = new QuestionSubmit();
        updateQuestionSubmit.setId(questionSubmitId);
        updateQuestionSubmit.setStatus(1);
        boolean update = questionSubmitService.updateById(updateQuestionSubmit);
        if (!update) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "更新题目状态失败");
        }

        // 5. 准备判题参数
        String judgeCaseStr = problem.getJudgeCase();
        List<JudgeCase> judgeCaseList = JSONUtil.toList(judgeCaseStr, JudgeCase.class);
        // 如果题目没有判题用例，防御性处理
        if (judgeCaseList == null || judgeCaseList.isEmpty()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "题目判题用例缺失");
        }

        List<String> inputList = judgeCaseList.stream().map(JudgeCase::getInput).collect(Collectors.toList());
        List<String> expectedOutputList = judgeCaseList.stream().map(JudgeCase::getOutput).toList();

        JudgeConfig judgeConfig = JSONUtil.toBean(problem.getJudgeConfig(), JudgeConfig.class);
        Long timeLimit = judgeConfig.getTimeLimit();
        Long memoryLimit = judgeConfig.getMemoryLimit();

        // 6. 调用沙箱
        ExecuteCodeRequest executeCodeRequest = ExecuteCodeRequest.builder()
                .code(questionSubmit.getCode())
                .language(questionSubmit.getLanguage())
                .inputList(inputList)
                .timeLimit(timeLimit)
                .memoryLimit(memoryLimit * 1024L)
                .build();

        ExecuteCodeResponse executeCodeResponse = dockerCodeSandbox.executeCode(executeCodeRequest);

        // 7. 初始化判题结果
        JudgeInfo judgeInfo = new JudgeInfo();
        // 处理沙箱返回的资源消耗，防止空指针
        if (executeCodeResponse.getJudgeInfo() != null) {
            judgeInfo.setMemory(executeCodeResponse.getJudgeInfo().getMemory());
            judgeInfo.setTime(executeCodeResponse.getJudgeInfo().getTime());
        } else {
            judgeInfo.setMemory(0L);
            judgeInfo.setTime(0L);
        }
        judgeInfo.setScore(0); // 默认为0

        // ==================== 异常情况处理 (直接返回) ====================

        // A. 编译错误
        if ("Compile Error".equals(executeCodeResponse.getMessage())) {
            judgeInfo.setMessage("Compile Error");
            judgeInfo.setDetail(executeCodeResponse.getJudgeInfo() != null ? executeCodeResponse.getJudgeInfo().getDetail() : "编译错误");
            updateAndNotify(questionSubmitId, 2, judgeInfo);
            return questionSubmitService.getById(questionSubmitId);
        }

        // B. 系统错误
        if ("System Error".equals(executeCodeResponse.getMessage())) {
            judgeInfo.setMessage("System Error");
            judgeInfo.setDetail(executeCodeResponse.getJudgeInfo() != null ? executeCodeResponse.getJudgeInfo().getDetail() : "系统错误");
            updateAndNotify(questionSubmitId, 3, judgeInfo);
            return questionSubmitService.getById(questionSubmitId);
        }

        // C. 运行错误
        if (executeCodeResponse.getStatus() != 1) {
            judgeInfo.setMessage("Runtime Error");
            judgeInfo.setDetail(executeCodeResponse.getMessage());
            updateAndNotify(questionSubmitId, 2, judgeInfo);
            return questionSubmitService.getById(questionSubmitId);
        }

        // D. 超时
        if (timeLimit > 0 && judgeInfo.getTime() > timeLimit) {
            judgeInfo.setMessage("Time Limit Exceeded");
            updateAndNotify(questionSubmitId, 2, judgeInfo);
            return questionSubmitService.getById(questionSubmitId);
        }

        // E. 超内存
        if (memoryLimit > 0 && judgeInfo.getMemory() > memoryLimit) {
            judgeInfo.setMessage("Memory Limit Exceeded");
            updateAndNotify(questionSubmitId, 2, judgeInfo);
            return questionSubmitService.getById(questionSubmitId);
        }

        // F. 输出数量检查
        List<String> outputList = executeCodeResponse.getOutputList();
        if (outputList == null || outputList.size() != inputList.size()) {
            judgeInfo.setMessage("Wrong Answer");
            judgeInfo.setDetail("输出结果数量不匹配");
            updateAndNotify(questionSubmitId, 2, judgeInfo);
            return questionSubmitService.getById(questionSubmitId);
        }

        // ==================== ✅ 核心判题逻辑修正 ====================

        // 1. 统计通过数
        int passCount = 0;
        int totalCount = judgeCaseList.size();
        for (int i = 0; i < totalCount; i++) {
            if (checkOutput(expectedOutputList.get(i), outputList.get(i))) {
                passCount++;
            }
        }

        // 2. ✅ 获取题目满分配置 (关键修复)
        int maxScore = 100; // 默认满分 100
        Long contestId = questionSubmit.getContestId();

        // 如果是比赛提交，尝试去查比赛题目关联表里的自定义分数
        if (contestId != null && contestId > 0) {
            ContestProblem contestProblem = contestProblemService.getOne(
                    new LambdaQueryWrapper<ContestProblem>()
                            .eq(ContestProblem::getContestId, contestId)
                            .eq(ContestProblem::getQuestionId, problemId)
                            .select(ContestProblem::getScore) // 优化性能，只查 score
            );
            if (contestProblem != null && contestProblem.getScore() != null) {
                maxScore = contestProblem.getScore();
            }
        }

        // 3. ✅ 计算加权分数
        // 逻辑：(通过数 / 总数) * 满分
        // 注意：先转 double 计算比例，再乘 maxScore，最后转 int
        int score = (int) ((double) passCount / totalCount * maxScore);
        judgeInfo.setScore(score);

        // 4. 判定最终状态
        if (passCount == totalCount) {
            judgeInfo.setMessage("Accepted");
        } else {
            judgeInfo.setMessage("Wrong Answer");
            judgeInfo.setDetail("Passed: " + passCount + "/" + totalCount);
        }

        updateAndNotify(questionSubmitId, 2, judgeInfo);
        return questionSubmitService.getById(questionSubmitId);
    }

    private void updateAndNotify(Long submitId, Integer status, JudgeInfo judgeInfo) {
        QuestionSubmit updateQuestionSubmit = new QuestionSubmit();
        updateQuestionSubmit.setId(submitId);
        updateQuestionSubmit.setStatus(status);
        updateQuestionSubmit.setJudgeInfo(JSONUtil.toJsonStr(judgeInfo));
        // 将计算出的分数同步到 submit 表的 score 字段，方便排行榜直接读取
        updateQuestionSubmit.setScore(judgeInfo.getScore() != null ? judgeInfo.getScore() : 0);

        boolean update = questionSubmitService.updateById(updateQuestionSubmit);

        if (update) {
            try {
                rabbitTemplate.convertAndSend(MqConfig.JUDGE_EXCHANGE, MqConfig.JUDGE_ROUTING_KEY, String.valueOf(submitId));
            } catch (Exception e) {
                log.error("判题完成，MQ消息发送失败，submitId: {}", submitId, e);
            }
        }
    }

    private boolean checkOutput(String expected, String actual) {
        if (expected == null) expected = "";
        if (actual == null) actual = "";

        // 移除首尾空白字符 (trim)
        // 更加稳健的比较：把所有连续空白字符(空格、制表符、换行)都视为一个分隔符
        // 这样 "1 2" 和 "1   2" 或者 "1\n2" 都会被视为相等，符合大多数 OJ 规范
        String[] expectedTokens = expected.trim().split("\\s+");
        String[] actualTokens = actual.trim().split("\\s+");

        if (expectedTokens.length != actualTokens.length) return false;
        for (int i = 0; i < expectedTokens.length; i++) {
            if (!expectedTokens[i].equals(actualTokens[i])) return false;
        }
        return true;
    }
}