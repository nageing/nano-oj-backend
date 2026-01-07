package com.nano.oj.judge.codesandbox.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.StatsCmd;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.github.dockerjava.core.command.LogContainerResultCallback;
import com.nano.oj.judge.codesandbox.CodeSandbox;
import com.nano.oj.judge.codesandbox.model.ExecuteCodeRequest;
import com.nano.oj.judge.codesandbox.model.ExecuteCodeResponse;
import com.nano.oj.model.dto.problemsubmit.JudgeInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class DockerCodeSandbox implements CodeSandbox {

    @Resource
    private DockerClient dockerClient;

    private static final long TIME_OUT = 15000L;

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        String language = executeCodeRequest.getLanguage();
        String code = executeCodeRequest.getCode();
        List<String> inputList = executeCodeRequest.getInputList();

        // 1. 准备宿主机工作目录
        String userDir = System.getProperty("user.dir");
        String globalCodePathName = userDir + File.separator + "tempCode";
        String parentPathName = globalCodePathName + File.separator + UUID.randomUUID();
        File parentPath = new File(parentPathName);
        if (!parentPath.exists()) {
            parentPath.mkdirs();
        }

        // 2. 语言配置
        String image = "";
        String fileName = "";
        String compileCmd = null;
        String runCmd = "";

        switch (language) {
            case "java":
                image = "eclipse-temurin:17-jdk";
                fileName = "Main.java";
                compileCmd = "javac -encoding utf-8 /app/Main.java";
                runCmd = "java -cp /app Main < %s";
                break;
            case "cpp":
                image = "gcc:latest";
                fileName = "main.cpp";
                compileCmd = "g++ -o /app/main /app/main.cpp";
                runCmd = "/app/main < %s";
                break;
            case "python":
                image = "python:3.9";
                fileName = "main.py";
                compileCmd = null;
                runCmd = "python3 /app/main.py < %s";
                break;
            default:
                FileUtil.del(parentPath);
                throw new RuntimeException("不支持的编程语言: " + language);
        }

        // 3. 写入文件
        File userCodeFile = new File(parentPath, fileName);
        FileUtil.writeString(code, userCodeFile, StandardCharsets.UTF_8);

        // 4. 【阶段一：编译】
        if (compileCmd != null) {
            try {
                String compileMessage = compileFile(image, parentPathName, compileCmd);
                if (compileMessage != null) {
                    return getErrorResponse("编译错误: " + compileMessage);
                }
            } catch (Exception e) {
                FileUtil.del(parentPath);
                return getErrorResponse("系统编译异常: " + e.getMessage());
            }
        }

        // 5. 【阶段二：运行】
        List<String> outputList = new ArrayList<>();
        long maxTime = 0;
        long maxMemory = 0;
        long memoryLimit = 100 * 1024 * 1024L;
        long defaultMemory = "java".equals(language) ? 30 * 1024 * 1024L : 1024L;

        try {
            for (int i = 0; i < inputList.size(); i++) {
                String input = inputList.get(i);
                String inputFileName = "input_" + i + ".txt";
                File inputFile = new File(parentPath, inputFileName);
                FileUtil.writeString(input, inputFile, StandardCharsets.UTF_8);

                String containerInputPath = "/app/" + inputFileName;
                String finalRunCmd = String.format(runCmd, containerInputPath);

                CreateContainerCmd containerCmd = dockerClient.createContainerCmd(image)
                        .withNetworkDisabled(true)
                        .withHostConfig(new HostConfig()
                                .withBinds(new Bind(parentPathName, new Volume("/app")))
                                .withMemory(memoryLimit)
                                .withCpuCount(1L))
                        .withAttachStdin(true)
                        .withAttachStdout(true)
                        .withAttachStderr(true)
                        .withTty(true)
                        .withCmd("/bin/sh", "-c", finalRunCmd);

                CreateContainerResponse containerResponse = containerCmd.exec();
                String containerId = containerResponse.getId();

                // 内存监控
                final long[] currentMaxMemory = {0L};
                StatsCmd statsCmd = dockerClient.statsCmd(containerId);
                ResultCallback<Statistics> statisticsCallback = statsCmd.exec(new ResultCallback<Statistics>() {
                    @Override
                    public void onNext(Statistics statistics) {
                        if (statistics != null && statistics.getMemoryStats() != null) {
                            Long usage = statistics.getMemoryStats().getUsage();
                            if (usage != null && usage < memoryLimit) {
                                currentMaxMemory[0] = Math.max(currentMaxMemory[0], usage);
                            }
                        }
                    }
                    @Override public void onStart(Closeable closeable) {}
                    @Override public void onError(Throwable throwable) {}
                    @Override public void onComplete() {}
                    @Override public void close() throws IOException {}
                });

                dockerClient.startContainerCmd(containerId).exec();

                StringBuilder resultLog = new StringBuilder();
                dockerClient.logContainerCmd(containerId)
                        .withStdOut(true)
                        .withStdErr(true)
                        .withFollowStream(true)
                        .exec(new LogContainerResultCallback() {
                            @Override
                            public void onNext(Frame item) {
                                resultLog.append(new String(item.getPayload()));
                            }
                        })
                        .awaitCompletion(TIME_OUT, TimeUnit.MILLISECONDS);

                statisticsCallback.close();

                // ✨✨✨ 核心修正点：使用 java.time.Instant 解析时间 ✨✨✨
                InspectContainerResponse inspectResponse = dockerClient.inspectContainerCmd(containerId).exec();
                String startedAt = inspectResponse.getState().getStartedAt();
                String finishedAt = inspectResponse.getState().getFinishedAt();

                // 使用 Instant.parse 直接解析 ISO-8601 格式 (包含 Z 和纳秒)
                long time = 0L;
                if (StrUtil.isNotBlank(startedAt) && StrUtil.isNotBlank(finishedAt)) {
                    Instant start = Instant.parse(startedAt);
                    Instant end = Instant.parse(finishedAt);
                    time = ChronoUnit.MILLIS.between(start, end);
                }

                long finalMemory = currentMaxMemory[0];
                if (finalMemory == 0) {
                    finalMemory = defaultMemory;
                }

                maxTime = Math.max(maxTime, time);
                maxMemory = Math.max(maxMemory, finalMemory);
                outputList.add(resultLog.toString().trim());

                dockerClient.removeContainerCmd(containerId).withForce(true).exec();
            }
        } catch (Exception e) {
            log.error("判题运行异常", e);
            throw new RuntimeException("判题运行异常: " + e.getMessage());
        } finally {
            if (FileUtil.exist(parentPath)) {
                FileUtil.del(parentPath);
            }
        }

        ExecuteCodeResponse response = new ExecuteCodeResponse();
        response.setOutputList(outputList);
        response.setMessage("执行成功");
        response.setStatus(1);
        JudgeInfo judgeInfo = new JudgeInfo();
        judgeInfo.setTime(maxTime);
        judgeInfo.setMemory(maxMemory);
        response.setJudgeInfo(judgeInfo);

        return response;
    }

    private String compileFile(String image, String parentPathName, String compileCmd) throws InterruptedException {
        CreateContainerCmd containerCmd = dockerClient.createContainerCmd(image)
                .withHostConfig(new HostConfig()
                        .withBinds(new Bind(parentPathName, new Volume("/app"))))
                .withCmd("/bin/sh", "-c", compileCmd)
                .withAttachStdout(true)
                .withAttachStderr(true);

        CreateContainerResponse response = containerCmd.exec();
        String containerId = response.getId();
        dockerClient.startContainerCmd(containerId).exec();

        StringBuilder compileLog = new StringBuilder();
        dockerClient.logContainerCmd(containerId)
                .withStdOut(true)
                .withStdErr(true)
                .withFollowStream(true)
                .exec(new LogContainerResultCallback() {
                    @Override
                    public void onNext(Frame item) {
                        compileLog.append(new String(item.getPayload()));
                    }
                })
                .awaitCompletion(10, TimeUnit.SECONDS);

        InspectContainerResponse inspectResponse = dockerClient.inspectContainerCmd(containerId).exec();
        Long exitCode = inspectResponse.getState().getExitCodeLong();

        dockerClient.removeContainerCmd(containerId).withForce(true).exec();

        if (exitCode != 0) {
            return compileLog.toString();
        }
        return null;
    }

    private ExecuteCodeResponse getErrorResponse(String message) {
        ExecuteCodeResponse response = new ExecuteCodeResponse();
        response.setOutputList(new ArrayList<>());
        response.setMessage(message);
        response.setStatus(2);
        JudgeInfo judgeInfo = new JudgeInfo();
        judgeInfo.setTime(0L);
        judgeInfo.setMemory(0L);
        response.setJudgeInfo(judgeInfo);
        return response;
    }
}