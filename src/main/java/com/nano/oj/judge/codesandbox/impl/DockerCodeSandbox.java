package com.nano.oj.judge.codesandbox.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.WaitContainerResultCallback;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.command.LogContainerResultCallback;
import com.nano.oj.judge.codesandbox.CodeSandbox;
import com.nano.oj.judge.codesandbox.model.ExecuteCodeRequest;
import com.nano.oj.judge.codesandbox.model.ExecuteCodeResponse;
import com.nano.oj.model.dto.problemsubmit.JudgeInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class DockerCodeSandbox implements CodeSandbox {

    @Resource
    private DockerClient dockerClient;

    private static final long DEFAULT_TIME_OUT = 30000L;

    // 输出日志最大长度限制 (字符数)
    private static final int MAX_OUTPUT_LENGTH = 10000;

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        String language = executeCodeRequest.getLanguage();
        String code = executeCodeRequest.getCode();
        List<String> inputList = executeCodeRequest.getInputList();

        Long requestTimeLimit = executeCodeRequest.getTimeLimit();
        long runTimeLimit = (requestTimeLimit == null) ? DEFAULT_TIME_OUT : requestTimeLimit;

        long killTimeBuffer = 1000L;
        long maxAllowedTime = runTimeLimit + killTimeBuffer;

        Long requestMemoryLimit = executeCodeRequest.getMemoryLimit();
        long containerMemoryLimit;
        if (requestMemoryLimit == null) {
            containerMemoryLimit = 512 * 1024 * 1024L;
        } else {
            if ("java".equals(language)) {
                containerMemoryLimit = requestMemoryLimit + 200 * 1024 * 1024L;
            } else {
                containerMemoryLimit = requestMemoryLimit + 20 * 1024 * 1024L;
            }
        }

        String userDir = System.getProperty("user.dir");
        String globalCodePathName = userDir + File.separator + "tempCode";
        String parentPathName = globalCodePathName + File.separator + UUID.randomUUID();
        File parentPath = new File(parentPathName);
        if (!parentPath.exists()) {
            parentPath.mkdirs();
        }

        String image = "";
        String fileName = "";
        String compileCmd = null;
        String runCmd = "";

        // 自动清洗代码：防止 Java package 导致 RE
        if ("java".equals(language) && StrUtil.isNotBlank(code)) {
            code = code.replaceAll("package\\s+[a-zA-Z0-9_\\.]+;", "");
        }

        String memoryCmd = "cat /sys/fs/cgroup/memory/memory.max_usage_in_bytes > /app/memory.txt 2>/dev/null || cat /sys/fs/cgroup/memory.peak > /app/memory.txt 2>/dev/null";

        switch (language) {
            case "java":
                image = "eclipse-temurin:17-jdk";
                fileName = "Main.java";
                compileCmd = "javac -encoding utf-8 /app/Main.java";
                runCmd = "java -Dfile.encoding=UTF-8 -cp /app Main < %s; ret=$?; " + memoryCmd + "; exit $ret";
                break;
            case "cpp":
                image = "gcc:latest";
                fileName = "main.cpp";
                compileCmd = "g++ -o /app/main /app/main.cpp";
                runCmd = "/app/main < %s; ret=$?; " + memoryCmd + "; exit $ret";
                break;
            case "python":
                image = "python:3.9";
                fileName = "main.py";
                compileCmd = null;
                runCmd = "PYTHONIOENCODING=utf-8 python3 /app/main.py < %s; ret=$?; " + memoryCmd + "; exit $ret";
                break;
            default:
                FileUtil.del(parentPath);
                throw new RuntimeException("不支持的编程语言: " + language);
        }

        File userCodeFile = new File(parentPath, fileName);
        FileUtil.writeString(code, userCodeFile, StandardCharsets.UTF_8);

        if (compileCmd != null) {
            try {
                String compileMessage = compileFile(image, parentPathName, compileCmd);
                if (compileMessage != null) {
                    return getErrorResponse("Compile Error", compileMessage);
                }
            } catch (Exception e) {
                FileUtil.del(parentPath);
                return getErrorResponse("System Error", "系统编译异常: " + e.getMessage());
            }
        }

        List<String> outputList = new ArrayList<>();
        long maxTime = 0;
        long maxMemory = 0;

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
                                .withMemory(containerMemoryLimit)
                                .withMemorySwap(containerMemoryLimit)
                                .withCpuCount(1L)
                                .withUlimits(new Ulimit[] { new Ulimit("stack", -1L, -1L) })
                                .withReadonlyRootfs(true)
                                .withTmpFs(Collections.singletonMap("/tmp", "rw,exec,nosuid,size=64m"))
                        )
                        .withEnv("LANG=C.UTF-8", "LC_ALL=C.UTF-8")
                        .withAttachStdin(true)
                        .withAttachStdout(true)
                        .withAttachStderr(true)
                        .withTty(true)
                        .withTty(false)
                        .withCmd("/bin/sh", "-c", finalRunCmd);

                CreateContainerResponse containerResponse = containerCmd.exec();
                String containerId = containerResponse.getId();

                dockerClient.startContainerCmd(containerId).exec();

                StringBuilder resultLog = new StringBuilder();
                // ✨✨✨ 稳定性加固：日志截断 ✨✨✨
                LogContainerResultCallback logCallback = new LogContainerResultCallback() {
                    @Override
                    public void onNext(Frame item) {
                        if (resultLog.length() > MAX_OUTPUT_LENGTH) {
                            return; // 超过长度直接丢弃
                        }
                        // 使用 UTF-8 防止中文乱码
                        resultLog.append(new String(item.getPayload(), StandardCharsets.UTF_8));

                        // 再次检查，如果刚才 append 后超了，就截断并提示
                        if (resultLog.length() > MAX_OUTPUT_LENGTH) {
                            resultLog.setLength(MAX_OUTPUT_LENGTH);
                            resultLog.append("...[Output too long]");
                        }
                    }
                };
                dockerClient.logContainerCmd(containerId)
                        .withStdOut(true)
                        .withStdErr(true)
                        .withFollowStream(true)
                        .exec(logCallback);

                WaitContainerResultCallback waitCallback = new WaitContainerResultCallback();
                dockerClient.waitContainerCmd(containerId).exec(waitCallback);

                boolean isTimeout = false;
                try {
                    boolean completed = waitCallback.awaitCompletion(maxAllowedTime, TimeUnit.MILLISECONDS);
                    if (!completed) {
                        isTimeout = true;
                        dockerClient.killContainerCmd(containerId).exec();
                    }
                } catch (InterruptedException e) {
                    isTimeout = true;
                    dockerClient.killContainerCmd(containerId).exec();
                } catch (Exception e) {
                    // ignore
                }

                logCallback.close();

                InspectContainerResponse inspectResponse = dockerClient.inspectContainerCmd(containerId).exec();
                Long exitCode = inspectResponse.getState().getExitCodeLong();
                boolean oomKilled = Boolean.TRUE.equals(inspectResponse.getState().getOOMKilled()) || (exitCode == 137);

                long timeCost;
                if (isTimeout) {
                    timeCost = runTimeLimit + 1;
                } else {
                    String startedAt = inspectResponse.getState().getStartedAt();
                    String finishedAt = inspectResponse.getState().getFinishedAt();
                    if (StrUtil.isNotBlank(startedAt) && StrUtil.isNotBlank(finishedAt)) {
                        try {
                            Instant start = Instant.parse(startedAt);
                            Instant end = Instant.parse(finishedAt);
                            timeCost = ChronoUnit.MILLIS.between(start, end);
                        } catch (Exception e) {
                            timeCost = 0;
                        }
                    } else {
                        timeCost = 0;
                    }
                }

                long memoryBytes = 0;
                if (oomKilled) {
                    memoryBytes = containerMemoryLimit;
                } else {
                    File memoryFile = new File(parentPath, "memory.txt");
                    if (memoryFile.exists()) {
                        String memoryStr = FileUtil.readString(memoryFile, StandardCharsets.UTF_8).trim();
                        if (StrUtil.isNotBlank(memoryStr)) {
                            try {
                                memoryBytes = Long.parseLong(memoryStr);
                            } catch (Exception e) {}
                        }
                    }
                }

                String logStr = resultLog.toString();
                if (!oomKilled && logStr.contains("java.lang.OutOfMemoryError")) {
                    oomKilled = true;
                    memoryBytes = containerMemoryLimit;
                }

                maxTime = Math.max(maxTime, timeCost);
                maxMemory = Math.max(maxMemory, memoryBytes);
                outputList.add(logStr.trim());

                if (!isTimeout && !oomKilled && exitCode != 0) {
                    dockerClient.removeContainerCmd(containerId).withForce(true).exec();
                    ExecuteCodeResponse response = new ExecuteCodeResponse();
                    response.setOutputList(new ArrayList<>());
                    response.setMessage("Runtime Error");
                    response.setStatus(2);
                    JudgeInfo judgeInfo = new JudgeInfo();
                    judgeInfo.setTime(maxTime);
                    judgeInfo.setMemory(maxMemory / 1024);
                    judgeInfo.setDetail(logStr);
                    response.setJudgeInfo(judgeInfo);
                    return response;
                }

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
        judgeInfo.setMemory(maxMemory / 1024);

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
        dockerClient.logContainerCmd(containerId).withStdOut(true).withStdErr(true).withFollowStream(true)
                .exec(new LogContainerResultCallback() {
                    @Override public void onNext(Frame item) {
                        // 编译日志也加一个限制，防止编译输出炸弹
                        if (compileLog.length() < MAX_OUTPUT_LENGTH) {
                            compileLog.append(new String(item.getPayload(), StandardCharsets.UTF_8));
                        }
                    }
                }).awaitCompletion(10, TimeUnit.SECONDS);
        InspectContainerResponse inspectResponse = dockerClient.inspectContainerCmd(containerId).exec();
        Long exitCode = inspectResponse.getState().getExitCodeLong();
        dockerClient.removeContainerCmd(containerId).withForce(true).exec();
        if (exitCode != 0) return compileLog.toString();
        return null;
    }

    private ExecuteCodeResponse getErrorResponse(String message, String detail) {
        ExecuteCodeResponse response = new ExecuteCodeResponse();
        response.setOutputList(new ArrayList<>());
        response.setMessage(message);
        response.setStatus(2);
        JudgeInfo judgeInfo = new JudgeInfo();
        judgeInfo.setTime(0L);
        judgeInfo.setMemory(0L);
        judgeInfo.setDetail(detail);
        response.setJudgeInfo(judgeInfo);
        return response;
    }
}