package com.nano.oj.judge.codesandbox.impl;

import com.nano.oj.judge.codesandbox.CodeSandbox;
import com.nano.oj.judge.codesandbox.model.ExecuteCodeRequest;
import com.nano.oj.judge.codesandbox.model.ExecuteCodeResponse;
import com.nano.oj.model.dto.problemsubmit.JudgeInfo;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 示例代码沙箱（仅为了跑通流程）
 */
@Slf4j
public class ExampleCodeSandbox implements CodeSandbox {
    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        List<String> inputList = executeCodeRequest.getInputList();
        System.out.println("示例代码沙箱接收到代码：" + executeCodeRequest.getCode());
        System.out.println("示例代码沙箱接收到输入：" + inputList);

        ExecuteCodeResponse response = new ExecuteCodeResponse();
        // 假装输出了和输入一样的东西（为了测试，或者你可以写死 output）
        response.setOutputList(inputList);
        response.setMessage("测试执行成功");
        response.setStatus(1); // 1: 成功

        JudgeInfo judgeInfo = new JudgeInfo();
        judgeInfo.setMessage("ACCEPT");
        judgeInfo.setMemory(100L);
        judgeInfo.setTime(100L);
        response.setJudgeInfo(judgeInfo);

        return response;
    }
}