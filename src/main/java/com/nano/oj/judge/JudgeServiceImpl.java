package com.nano.oj.judge;

import cn.hutool.json.JSONUtil;
import com.nano.oj.common.ErrorCode;
import com.nano.oj.exception.BusinessException;
import com.nano.oj.judge.codesandbox.CodeSandbox;
import com.nano.oj.judge.codesandbox.model.ExecuteCodeRequest;
import com.nano.oj.judge.codesandbox.model.ExecuteCodeResponse;
import com.nano.oj.model.dto.problem.JudgeCase;
import com.nano.oj.model.dto.problem.JudgeConfig;
import com.nano.oj.model.dto.problemsubmit.JudgeInfo;
import com.nano.oj.model.entity.Problem;
import com.nano.oj.model.entity.QuestionSubmit;
import com.nano.oj.service.ProblemService;
import com.nano.oj.service.QuestionSubmitService;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class JudgeServiceImpl implements JudgeService {

    @Resource
    private QuestionSubmitService questionSubmitService;

    @Resource
    private ProblemService problemService;

    @Resource
    private CodeSandbox dockerCodeSandbox;

    @Override
    public QuestionSubmit doJudge(long questionSubmitId) {
        QuestionSubmit questionSubmit = questionSubmitService.getById(questionSubmitId);
        if (questionSubmit == null) throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "提交信息不存在");

        Long problemId = questionSubmit.getQuestionId();
        Problem problem = problemService.getById(problemId);
        if (problem == null) throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "题目不存在");

        if (!questionSubmit.getStatus().equals(0)) throw new BusinessException(ErrorCode.OPERATION_ERROR, "题目正在判题中");

        QuestionSubmit updateQuestionSubmit = new QuestionSubmit();
        updateQuestionSubmit.setId(questionSubmitId);
        updateQuestionSubmit.setStatus(1);
        questionSubmitService.updateById(updateQuestionSubmit);

        String judgeCaseStr = problem.getJudgeCase();
        List<JudgeCase> judgeCaseList = JSONUtil.toList(judgeCaseStr, JudgeCase.class);
        List<String> inputList = judgeCaseList.stream().map(JudgeCase::getInput).collect(Collectors.toList());
        List<String> expectedOutputList = judgeCaseList.stream().map(JudgeCase::getOutput).collect(Collectors.toList());

        JudgeConfig judgeConfig = JSONUtil.toBean(problem.getJudgeConfig(), JudgeConfig.class);

        // 1. 处理时间限制 (ms)
        Long timeLimit = judgeConfig.getTimeLimit();
        if (timeLimit == null || timeLimit <= 0) {
            timeLimit = 1000L;
        }

        // 2. 处理内存限制 (KB)
        Long memoryLimit = judgeConfig.getMemoryLimit();
        if (memoryLimit == null || memoryLimit <= 0) {
            memoryLimit = 256 * 1024L; // 默认 256MB (KB)
        }

        // 注意：这里 memoryLimit 保持 KB 单位，不要乘 1024！

        String language = questionSubmit.getLanguage();

        // 3. 构建请求
        ExecuteCodeRequest executeCodeRequest = ExecuteCodeRequest.builder()
                .code(questionSubmit.getCode())
                .language(language)
                .inputList(inputList)
                .timeLimit(timeLimit)
                // ✨✨✨ 修正点：只在这里转为 Byte 传给沙箱，本地变量 memoryLimit 保持 KB
                .memoryLimit(memoryLimit * 1024L)
                .build();

        ExecuteCodeResponse executeCodeResponse = dockerCodeSandbox.executeCode(executeCodeRequest);

        List<String> outputList = executeCodeResponse.getOutputList();

        JudgeInfo judgeInfo = new JudgeInfo();
        Long memory = executeCodeResponse.getJudgeInfo().getMemory(); // 沙箱返回的是 KB
        Long time = executeCodeResponse.getJudgeInfo().getTime();
        judgeInfo.setMemory(memory);
        judgeInfo.setTime(time);

        // --- 判题逻辑 ---

        // 1. Compile Error
        if ("Compile Error".equals(executeCodeResponse.getMessage())) {
            judgeInfo.setMessage("Compile Error");
            judgeInfo.setDetail(executeCodeResponse.getJudgeInfo().getDetail());
            updateDatabase(questionSubmitId, 2, judgeInfo);
            return questionSubmitService.getById(questionSubmitId);
        }

        // 2. System Error
        if ("System Error".equals(executeCodeResponse.getMessage())) {
            judgeInfo.setMessage("System Error");
            judgeInfo.setDetail(executeCodeResponse.getJudgeInfo().getDetail());
            updateDatabase(questionSubmitId, 3, judgeInfo);
            return questionSubmitService.getById(questionSubmitId);
        }

        // 3. Time Limit Exceeded
        if (time > timeLimit) {
            judgeInfo.setMessage("TLE");
            updateDatabase(questionSubmitId, 2, judgeInfo);
            return questionSubmitService.getById(questionSubmitId);
        }

        // 4. Runtime Error
        if (executeCodeResponse.getStatus() != 1) {
            judgeInfo.setMessage("RE");
            judgeInfo.setDetail(executeCodeResponse.getMessage());
            updateDatabase(questionSubmitId, 2, judgeInfo);
            return questionSubmitService.getById(questionSubmitId);
        }

        // 5. Memory Limit Exceeded
        // ✨✨✨ 现在的比较：276480 (KB) > 256000 (KB) -> True -> MLE
        if (memory > memoryLimit) {
            judgeInfo.setMessage("MLE");
            updateDatabase(questionSubmitId, 2, judgeInfo);
            return questionSubmitService.getById(questionSubmitId);
        }

        // 6. Wrong Answer / Accepted
        judgeInfo.setMessage("AC");
        if (outputList == null || outputList.size() != inputList.size()) {
            judgeInfo.setMessage("WA");
            judgeInfo.setDetail("输出数量不匹配");
        } else {
            for (int i = 0; i < judgeCaseList.size(); i++) {
                if (!checkOutput(expectedOutputList.get(i), outputList.get(i))) {
                    judgeInfo.setMessage("WA");
                    break;
                }
            }
        }

        updateDatabase(questionSubmitId, 2, judgeInfo);
        return questionSubmitService.getById(questionSubmitId);
    }

    private void updateDatabase(Long submitId, Integer status, JudgeInfo judgeInfo) {
        QuestionSubmit updateQuestionSubmit = new QuestionSubmit();
        updateQuestionSubmit.setId(submitId);
        updateQuestionSubmit.setStatus(status);
        updateQuestionSubmit.setJudgeInfo(JSONUtil.toJsonStr(judgeInfo));
        questionSubmitService.updateById(updateQuestionSubmit);
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