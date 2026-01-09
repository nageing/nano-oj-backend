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
import com.nano.oj.model.entity.Problem;
import com.nano.oj.model.entity.QuestionSubmit;
import com.nano.oj.service.ProblemService;
import com.nano.oj.service.QuestionSubmitService;
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

        // 3. 校验状态 (防止重复判题)
        // 假设 0 = 待判题, 1 = 判题中, 2 = 成功, 3 = 失败
        if (!questionSubmit.getStatus().equals(0)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "题目正在判题中");
        }

        // 4. 更新状态为“判题中” (status = 1)
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
                .memoryLimit(memoryLimit * 1024L) // KB 转 Byte
                .build();

        ExecuteCodeResponse executeCodeResponse = dockerCodeSandbox.executeCode(executeCodeRequest);

        // 7. 初始化判题结果
        JudgeInfo judgeInfo = new JudgeInfo();
        if (executeCodeResponse.getJudgeInfo() != null) {
            judgeInfo.setMemory(executeCodeResponse.getJudgeInfo().getMemory());
            judgeInfo.setTime(executeCodeResponse.getJudgeInfo().getTime());
        } else {
            judgeInfo.setMemory(0L);
            judgeInfo.setTime(0L);
        }
        judgeInfo.setScore(0); // 默认为0分

        // ==================== 8. 开始核心判题逻辑 ====================

        // A. 编译错误
        if ("Compile Error".equals(executeCodeResponse.getMessage())) {
            judgeInfo.setMessage("Compile Error");
            judgeInfo.setDetail(executeCodeResponse.getJudgeInfo() != null ? executeCodeResponse.getJudgeInfo().getDetail() : "编译错误");
            // 状态 2 表示判题流程结束（虽然结果是错的）
            updateAndNotify(questionSubmitId, 2, judgeInfo);
            return questionSubmitService.getById(questionSubmitId);
        }

        // B. 系统错误
        if ("System Error".equals(executeCodeResponse.getMessage())) {
            judgeInfo.setMessage("System Error");
            judgeInfo.setDetail(executeCodeResponse.getJudgeInfo() != null ? executeCodeResponse.getJudgeInfo().getDetail() : "系统错误");
            // 状态 3 表示系统异常
            updateAndNotify(questionSubmitId, 3, judgeInfo);
            return questionSubmitService.getById(questionSubmitId);
        }

        // C. 运行错误 (Runtime Error)
        if (executeCodeResponse.getStatus() != 1) { // 假设 1 代表运行正常
            judgeInfo.setMessage("Runtime Error");
            judgeInfo.setDetail(executeCodeResponse.getMessage());
            updateAndNotify(questionSubmitId, 2, judgeInfo);
            return questionSubmitService.getById(questionSubmitId);
        }

        // D. 超时 (TLE)
        Long runTime = judgeInfo.getTime();
        if (timeLimit > 0 && runTime > timeLimit) {
            judgeInfo.setMessage("Time Limit Exceeded");
            updateAndNotify(questionSubmitId, 2, judgeInfo);
            return questionSubmitService.getById(questionSubmitId);
        }

        // E. 超内存 (MLE)
        Long runMemory = judgeInfo.getMemory();
        if (memoryLimit > 0 && runMemory > memoryLimit) {
            judgeInfo.setMessage("Memory Limit Exceeded");
            updateAndNotify(questionSubmitId, 2, judgeInfo);
            return questionSubmitService.getById(questionSubmitId);
        }

        // F. 答案比对 (AC / WA)
        List<String> outputList = executeCodeResponse.getOutputList();
        if (outputList == null || outputList.size() != inputList.size()) {
            judgeInfo.setMessage("Wrong Answer");
            judgeInfo.setDetail("输出结果数量不匹配");
            updateAndNotify(questionSubmitId, 2, judgeInfo);
            return questionSubmitService.getById(questionSubmitId);
        }

        // 统计通过数
        int passCount = 0;
        int totalCount = judgeCaseList.size();
        for (int i = 0; i < totalCount; i++) {
            if (checkOutput(expectedOutputList.get(i), outputList.get(i))) {
                passCount++;
            }
        }

        // 计算分数 (OI)
        int score = (int) ((double) passCount / totalCount * 100);
        judgeInfo.setScore(score);

        // 最终判定
        if (passCount == totalCount) {
            judgeInfo.setMessage("Accepted");
        } else {
            judgeInfo.setMessage("Wrong Answer");
            judgeInfo.setDetail("Passed: " + passCount + "/" + totalCount);
        }

        updateAndNotify(questionSubmitId, 2, judgeInfo);
        return questionSubmitService.getById(questionSubmitId);
    }

    /**
     * 封装：更新数据库 + 发送 MQ 消息
     */
    private void updateAndNotify(Long submitId, Integer status, JudgeInfo judgeInfo) {
        // 1. 更新数据库
        QuestionSubmit updateQuestionSubmit = new QuestionSubmit();
        updateQuestionSubmit.setId(submitId);
        updateQuestionSubmit.setStatus(status);
        updateQuestionSubmit.setJudgeInfo(JSONUtil.toJsonStr(judgeInfo));

        if (judgeInfo.getScore() != null) {
            updateQuestionSubmit.setScore(judgeInfo.getScore());
        } else {
            updateQuestionSubmit.setScore(0);
        }

        boolean update = questionSubmitService.updateById(updateQuestionSubmit);

        // 2. 发送 MQ 消息 (通知排行榜)
        if (update) {
            // 防止 MQ 发送失败影响判题流程，加个 try-catch
            try {
                rabbitTemplate.convertAndSend(
                        MqConfig.JUDGE_EXCHANGE,
                        MqConfig.JUDGE_ROUTING_KEY,
                        String.valueOf(submitId)
                );
                log.info("判题完成，MQ消息已发送，submitId: {}", submitId);
            } catch (Exception e) {
                log.error("判题完成，MQ消息发送失败，submitId: {}", submitId, e);
            }
        }
    }

    private boolean checkOutput(String expected, String actual) {
        if (expected == null) expected = "";
        if (actual == null) actual = "";
        expected = expected.trim();
        actual = actual.trim();
        String[] expectedTokens = expected.split("\\s+");
        String[] actualTokens = actual.split("\\s+");
        if (expectedTokens.length != actualTokens.length) return false;
        for (int i = 0; i < expectedTokens.length; i++) {
            if (!expectedTokens[i].equals(actualTokens[i])) return false;
        }
        return true;
    }
}