package com.nano.oj.judge;

import cn.hutool.json.JSONUtil;
import com.nano.oj.common.ErrorCode;
import com.nano.oj.exception.BusinessException;
import com.nano.oj.judge.codesandbox.CodeSandbox;
import com.nano.oj.judge.codesandbox.model.ExecuteCodeRequest;
import com.nano.oj.judge.codesandbox.model.ExecuteCodeResponse;
import com.nano.oj.model.dto.problem.JudgeCase;
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
        // 1. 获取提交记录
        QuestionSubmit questionSubmit = questionSubmitService.getById(questionSubmitId);
        if (questionSubmit == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "提交信息不存在");
        }

        Long problemId = questionSubmit.getQuestionId();
        Problem problem = problemService.getById(problemId);
        if (problem == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "题目不存在");
        }

        // 2. 如果不为“待判题”状态，就不用重复执行了
        if (!questionSubmit.getStatus().equals(0)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "题目正在判题中");
        }

        // 3. 更改状态为“判题中”
        QuestionSubmit updateQuestionSubmit = new QuestionSubmit();
        updateQuestionSubmit.setId(questionSubmitId);
        updateQuestionSubmit.setStatus(1); // 1-判题中
        boolean update = questionSubmitService.updateById(updateQuestionSubmit);
        if (!update) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "题目状态更新错误");
        }

        // 4. 调用沙箱
        String judgeCaseStr = problem.getJudgeCase();
        List<JudgeCase> judgeCaseList = JSONUtil.toList(judgeCaseStr, JudgeCase.class);
        List<String> inputList = judgeCaseList.stream().map(JudgeCase::getInput).collect(Collectors.toList());
        List<String> expectedOutputList = judgeCaseList.stream().map(JudgeCase::getOutput).collect(Collectors.toList());

        ExecuteCodeRequest executeCodeRequest = ExecuteCodeRequest.builder()
                .code(questionSubmit.getCode())
                .language(questionSubmit.getLanguage())
                .inputList(inputList)
                .build();

        ExecuteCodeResponse executeCodeResponse = dockerCodeSandbox.executeCode(executeCodeRequest);

        // 5. 根据沙箱结果处理判题逻辑
        List<String> outputList = executeCodeResponse.getOutputList();

        JudgeInfo judgeInfo = new JudgeInfo();
        judgeInfo.setMemory(executeCodeResponse.getJudgeInfo().getMemory());
        judgeInfo.setTime(executeCodeResponse.getJudgeInfo().getTime());

        // 如果沙箱执行本身失败（比如编译错误，或者容器挂了）
        if (executeCodeResponse.getStatus() != 1) { // 假设 1 表示运行正常结束
            judgeInfo.setMessage(executeCodeResponse.getMessage()); // 可能是 "Compile Error" 或 "Runtime Error"
            updateDatabase(questionSubmitId, 2, judgeInfo); // 2-流程结束
            return questionSubmitService.getById(questionSubmitId);
        }

        // ✨✨✨ 核心修改：使用 Codeforces 标准进行比对 ✨✨✨
        judgeInfo.setMessage("Accepted"); // 先假设是对的

        if (outputList == null || outputList.size() != inputList.size()) {
            judgeInfo.setMessage("Wrong Answer");
            // 可以记录下详情: "输出数量不一致"
        } else {
            for (int i = 0; i < judgeCaseList.size(); i++) {
                String expected = expectedOutputList.get(i);
                String actual = outputList.get(i);

                // 使用去空格逻辑判断
                if (!checkOutput(expected, actual)) {
                    judgeInfo.setMessage("Wrong Answer");
                    // 记录一下哪个用例错了，方便调试 (可选)
                    // System.out.println("Diff at case " + i + ": expect [" + expected + "], actual [" + actual + "]");
                    break;
                }
            }
        }

        // 6. 修改数据库状态
        updateDatabase(questionSubmitId, 2, judgeInfo);

        return questionSubmitService.getById(questionSubmitId);
    }

    /**
     * 辅助方法：更新数据库
     */
    private void updateDatabase(Long submitId, Integer status, JudgeInfo judgeInfo) {
        QuestionSubmit updateQuestionSubmit = new QuestionSubmit();
        updateQuestionSubmit.setId(submitId);
        updateQuestionSubmit.setStatus(status);
        updateQuestionSubmit.setJudgeInfo(JSONUtil.toJsonStr(judgeInfo));
        questionSubmitService.updateById(updateQuestionSubmit);
    }

    /**
     * ✨✨✨ 辅助方法：Codeforces 风格比对 ✨✨✨
     * 忽略行末空格、文末换行，将连续空白视为一个分隔符
     */
    private boolean checkOutput(String expected, String actual) {
        if (expected == null) expected = "";
        if (actual == null) actual = "";

        // 1. 去除首尾空白
        expected = expected.trim();
        actual = actual.trim();

        // 2. 按“空白字符”切割
        // "\\s+" 正则匹配：空格、Tab、换行符等任意连续空白
        String[] expectedTokens = expected.split("\\s+");
        String[] actualTokens = actual.split("\\s+");

        // 3. 比较 Token 数量
        if (expectedTokens.length != actualTokens.length) {
            return false;
        }

        // 4. 逐个 Token 比对
        for (int i = 0; i < expectedTokens.length; i++) {
            if (!expectedTokens[i].equals(actualTokens[i])) {
                return false;
            }
        }
        return true;
    }
}