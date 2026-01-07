package com.nano.oj.judge;

import cn.hutool.json.JSONUtil;
import com.nano.oj.common.ErrorCode;
import com.nano.oj.exception.BusinessException;
import com.nano.oj.judge.codesandbox.CodeSandbox;
import com.nano.oj.judge.codesandbox.impl.DockerCodeSandbox;
import com.nano.oj.judge.codesandbox.impl.ExampleCodeSandbox;
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

    // ✨ 注入 Docker 实现
    @Resource
    private DockerCodeSandbox dockerCodeSandbox;

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

        // 3. 更改状态为“判题中”，防止重复执行
        QuestionSubmit updateQuestionSubmit = new QuestionSubmit();
        updateQuestionSubmit.setId(questionSubmitId);
        updateQuestionSubmit.setStatus(1); // 1-判题中
        boolean update = questionSubmitService.updateById(updateQuestionSubmit);
        if (!update) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "题目状态更新错误");
        }

        // 4. 调用沙箱
        // 获取输入用例
        String judgeCaseStr = problem.getJudgeCase();
        List<JudgeCase> judgeCaseList = JSONUtil.toList(judgeCaseStr, JudgeCase.class);
        List<String> inputList = judgeCaseList.stream().map(JudgeCase::getInput).collect(Collectors.toList());

        // 使用注入的 Docker 沙箱
        CodeSandbox codeSandbox = dockerCodeSandbox;

        ExecuteCodeRequest executeCodeRequest = ExecuteCodeRequest.builder()
                .code(questionSubmit.getCode())
                .language(questionSubmit.getLanguage())
                .inputList(inputList)
                .build();

        ExecuteCodeResponse executeCodeResponse = codeSandbox.executeCode(executeCodeRequest);

        // 5. 根据沙箱的执行结果，设置题目的判题状态和信息
        // 这里只是简单的逻辑：如果沙箱输出的数量和预期不一样，那就是错的
        List<String> outputList = executeCodeResponse.getOutputList();
        // 预期输出
        List<String> expectedOutputList = judgeCaseList.stream().map(JudgeCase::getOutput).collect(Collectors.toList());

        JudgeInfo judgeInfo = new JudgeInfo();
        // 默认设置为 Accept，下面去“找茬”
        judgeInfo.setMessage("Accepted");
        judgeInfo.setMemory(executeCodeResponse.getJudgeInfo().getMemory());
        judgeInfo.setTime(executeCodeResponse.getJudgeInfo().getTime());

        // 开始比对
        if (outputList.size() != inputList.size()) {
            judgeInfo.setMessage("Wrong Answer"); // 数量都不对，肯定错了
        } else {
            for (int i = 0; i < judgeCaseList.size(); i++) {
                if (!outputList.get(i).equals(expectedOutputList.get(i))) {
                    judgeInfo.setMessage("Wrong Answer");
                    break; // 只要有一个不对，就直接判错
                }
            }
        }

        // 6. 修改数据库中的判题结果
        updateQuestionSubmit = new QuestionSubmit();
        updateQuestionSubmit.setId(questionSubmitId);
        updateQuestionSubmit.setStatus(2); // 2-成功（指判题流程结束，不是指题目做对了）
        updateQuestionSubmit.setJudgeInfo(JSONUtil.toJsonStr(judgeInfo));
        questionSubmitService.updateById(updateQuestionSubmit);

        return questionSubmitService.getById(questionSubmitId);
    }
}