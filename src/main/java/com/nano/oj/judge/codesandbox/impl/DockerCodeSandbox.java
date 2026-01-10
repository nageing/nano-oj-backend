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
import com.nano.oj.model.dto.questionsubmit.JudgeInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Docker 代码沙箱实现
 * 提供 Java, C++, Python 等语言的隔离运行环境
 */
@Component
@Slf4j
public class DockerCodeSandbox implements CodeSandbox {

    @Resource
    private DockerClient dockerClient;

    // 默认超时时间 30s
    private static final long DEFAULT_TIME_OUT = 30000L;

    // 输出日志最大长度限制 (防止恶意输出导致内存溢出)
    private static final int MAX_OUTPUT_LENGTH = 10000;

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        // ================== 1. 基础配置与参数准备 ==================
        String language = executeCodeRequest.getLanguage();
        String code = executeCodeRequest.getCode();
        List<String> inputList = executeCodeRequest.getInputList();

        // 设置时间限制 (给予 1秒 的 Buffer 防止刚到时间就被 kill 导致没有日志)
        Long requestTimeLimit = executeCodeRequest.getTimeLimit();
        long runTimeLimit = (requestTimeLimit == null) ? DEFAULT_TIME_OUT : requestTimeLimit;
        long maxAllowedTime = runTimeLimit + 1000L;

        // 设置内存限制
        Long requestMemoryLimit = executeCodeRequest.getMemoryLimit();
        long containerMemoryLimit;
        if (requestMemoryLimit == null) {
            containerMemoryLimit = 512 * 1024 * 1024L; // 默认 512MB
        } else {
            // Java 需要额外的 JVM 开销，给予更宽裕的内存
            if ("java".equals(language)) {
                containerMemoryLimit = requestMemoryLimit + 200 * 1024 * 1024L;
            } else {
                containerMemoryLimit = requestMemoryLimit + 20 * 1024 * 1024L;
            }
        }

        // 准备临时文件目录 (tempCode/UUID)
        String userDir = System.getProperty("user.dir");
        String globalCodePathName = userDir + File.separator + "tempCode";
        String parentPathName = globalCodePathName + File.separator + UUID.randomUUID();
        File parentPath = new File(parentPathName);

        // ================== 2. 核心处理流程 (Try-Finally 保证清理) ==================
        try {
            // 2.1 创建代码隔离目录
            if (!parentPath.exists()) {
                parentPath.mkdirs();
            }

            String image = "";
            String fileName = "";
            String compileCmd = null;
            String runCmd = "";

            // 自动清洗 Java 代码：防止 package 声明导致运行错误
            if ("java".equals(language) && StrUtil.isNotBlank(code)) {
                code = code.replaceAll("package\\s+[a-zA-Z0-9_\\.]+;", "");
            }

            // 内存监控命令 (兼容不同 Linux 发行版路径)
            String memoryCmd = "cat /sys/fs/cgroup/memory/memory.max_usage_in_bytes > /app/memory.txt 2>/dev/null || cat /sys/fs/cgroup/memory.peak > /app/memory.txt 2>/dev/null";

            // 2.2 根据语言配置镜像和命令
            switch (language) {
                case "java":
                    image = "eclipse-temurin:17-jdk";
                    fileName = "Main.java";
                    // 编译并修改权限为 777，确保宿主机可以删除 root 创建的 class 文件
                    compileCmd = "javac -encoding utf-8 /app/Main.java && chmod -R 777 /app";
                    runCmd = "java -Dfile.encoding=UTF-8 -cp /app Main < %s; ret=$?; " + memoryCmd + "; exit $ret";
                    break;
                case "cpp":
                    image = "gcc:latest";
                    fileName = "main.cpp";
                    // 编译并修改权限
                    compileCmd = "g++ -o /app/main /app/main.cpp && chmod -R 777 /app";
                    runCmd = "/app/main < %s; ret=$?; " + memoryCmd + "; exit $ret";
                    break;
                case "python":
                    image = "python:3.9";
                    fileName = "main.py";
                    compileCmd = null; // Python 不需要编译
                    runCmd = "PYTHONIOENCODING=utf-8 python3 /app/main.py < %s; ret=$?; " + memoryCmd + "; exit $ret";
                    break;
                default:
                    throw new RuntimeException("不支持的编程语言: " + language);
            }

            // 2.3 将用户代码写入文件
            File userCodeFile = new File(parentPath, fileName);
            FileUtil.writeString(code, userCodeFile, StandardCharsets.UTF_8);

            // 2.4 编译代码 (如果需要)
            if (compileCmd != null) {
                try {
                    String compileMessage = compileFile(image, parentPathName, compileCmd);
                    if (compileMessage != null) {
                        // 🔴 编译失败：直接返回错误
                        // ✅ 注意：这里 return 前会自动执行 finally 里的 cleanup，解决 CE 不删文件的问题
                        return getErrorResponse("Compile Error", compileMessage);
                    }
                } catch (Exception e) {
                    return getErrorResponse("System Error", "系统编译异常: " + e.getMessage());
                }
            }

            // 2.5 执行代码 (遍历所有测试用例)
            List<String> outputList = new ArrayList<>();
            long maxTime = 0;
            long maxMemory = 0;

            for (int i = 0; i < inputList.size(); i++) {
                String input = inputList.get(i);
                String inputFileName = "input_" + i + ".txt";
                File inputFile = new File(parentPath, inputFileName);
                FileUtil.writeString(input, inputFile, StandardCharsets.UTF_8);

                String containerInputPath = "/app/" + inputFileName;
                String finalRunCmd = String.format(runCmd, containerInputPath);

                // 创建容器
                CreateContainerCmd containerCmd = dockerClient.createContainerCmd(image)
                        .withNetworkDisabled(true) // 禁用网络，防止恶意代码
                        .withHostConfig(new HostConfig()
                                .withBinds(new Bind(parentPathName, new Volume("/app"))) // 挂载代码目录
                                .withMemory(containerMemoryLimit)
                                .withMemorySwap(containerMemoryLimit) // 限制 swap 防止 OOM 逃逸
                                .withCpuCount(1L)
                                .withReadonlyRootfs(true) // 只读根文件系统，防止修改环境
                                // 挂载可写临时目录，部分语言运行时需要
                                .withTmpFs(Collections.singletonMap("/tmp", "rw,exec,nosuid,size=64m"))
                        )
                        .withEnv("LANG=C.UTF-8", "LC_ALL=C.UTF-8") // 防止中文乱码
                        .withAttachStdin(true)
                        .withAttachStdout(true)
                        .withAttachStderr(true)
                        .withTty(false) // 关闭 TTY，方便获取纯净输出
                        .withCmd("/bin/sh", "-c", finalRunCmd);

                CreateContainerResponse containerResponse = containerCmd.exec();
                String containerId = containerResponse.getId();

                // 启动容器
                dockerClient.startContainerCmd(containerId).exec();

                // 获取日志 (代码输出)
                StringBuilder resultLog = new StringBuilder();
                LogContainerResultCallback logCallback = new LogContainerResultCallback() {
                    @Override
                    public void onNext(Frame item) {
                        if (resultLog.length() > MAX_OUTPUT_LENGTH) return;
                        resultLog.append(new String(item.getPayload(), StandardCharsets.UTF_8));
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

                // 等待容器结束或超时
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
                }

                logCallback.close();

                // 获取容器运行状态 (退出码、内存、时间)
                InspectContainerResponse inspectResponse = dockerClient.inspectContainerCmd(containerId).exec();
                Long exitCode = inspectResponse.getState().getExitCodeLong();
                // 137 通常代表 OOM (Out Of Memory)
                boolean oomKilled = Boolean.TRUE.equals(inspectResponse.getState().getOOMKilled()) || (exitCode == 137);

                // 计算时间消耗
                long timeCost = 0;
                if (isTimeout) {
                    timeCost = runTimeLimit + 1; // 标记超时
                } else {
                    String startedAt = inspectResponse.getState().getStartedAt();
                    String finishedAt = inspectResponse.getState().getFinishedAt();
                    if (StrUtil.isNotBlank(startedAt) && StrUtil.isNotBlank(finishedAt)) {
                        Instant start = Instant.parse(startedAt);
                        Instant end = Instant.parse(finishedAt);
                        timeCost = ChronoUnit.MILLIS.between(start, end);
                    }
                }

                // 计算内存消耗
                long memoryBytes = 0;
                if (oomKilled) {
                    memoryBytes = containerMemoryLimit;
                } else {
                    // 从挂载的 memory.txt 读取内存峰值
                    File memoryFile = new File(parentPath, "memory.txt");
                    if (memoryFile.exists()) {
                        String memoryStr = FileUtil.readString(memoryFile, StandardCharsets.UTF_8).trim();
                        try {
                            memoryBytes = Long.parseLong(memoryStr);
                        } catch (Exception e) {}
                    }
                }

                // Java OOM 特殊判定 (有时 Docker 没 kill，但 JVM 抛出了 Error)
                String logStr = resultLog.toString();
                if (!oomKilled && logStr.contains("java.lang.OutOfMemoryError")) {
                    oomKilled = true;
                    memoryBytes = containerMemoryLimit;
                }

                maxTime = Math.max(maxTime, timeCost);
                maxMemory = Math.max(maxMemory, memoryBytes);
                outputList.add(logStr.trim());

                // 清理容器
                dockerClient.removeContainerCmd(containerId).withForce(true).exec();

                // 🔴 运行时错误处理 (Runtime Error)
                if (!isTimeout && !oomKilled && exitCode != 0) {
                    ExecuteCodeResponse response = new ExecuteCodeResponse();
                    response.setOutputList(new ArrayList<>());
                    response.setMessage("Runtime Error");
                    response.setStatus(2); // 2: 失败
                    JudgeInfo judgeInfo = new JudgeInfo();
                    judgeInfo.setTime(maxTime);
                    judgeInfo.setMemory(maxMemory / 1024);
                    judgeInfo.setDetail(logStr);
                    response.setJudgeInfo(judgeInfo);
                    // ✅ 同样会触发 finally 清理
                    return response;
                }
            } // end for loop

            // 2.6 构建成功响应
            ExecuteCodeResponse response = new ExecuteCodeResponse();
            response.setOutputList(outputList);
            response.setMessage("执行成功");
            response.setStatus(1); // 1: 成功
            JudgeInfo judgeInfo = new JudgeInfo();
            judgeInfo.setTime(maxTime);
            judgeInfo.setMemory(maxMemory / 1024);
            response.setJudgeInfo(judgeInfo);
            return response;

        } catch (Exception e) {
            log.error("判题运行异常", e);
            throw new RuntimeException("判题运行异常: " + e.getMessage());
        } finally {
            // ================== 3. 资源清理 (兜底逻辑) ==================
            if (parentPath == null) {
                log.warn("⚠️ parentPath 为 null，跳过清理");
            } else {
                String pathStr = parentPath.getAbsolutePath();
                log.info("🧹 开始清理临时目录: {}", pathStr);

                // 3.1 延时释放锁：Windows 下 Docker 释放文件句柄可能有延迟
                try {
                    TimeUnit.MILLISECONDS.sleep(1500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                // 3.2 尝试删除
                if (!parentPath.exists()) {
                    // 目录不存在，可能是已经被删除了
                } else {
                    boolean delSuccess = FileUtil.del(parentPath);
                    if (delSuccess) {
                        log.info("✅ Hutool 删除成功: {}", pathStr);
                    } else {
                        log.error("❌ Hutool 删除失败 (可能是权限或占用问题): {}", pathStr);
                        // 3.3 NIO 暴力删除 (捕获具体异常信息)
                        try {
                            log.info("🔧 尝试使用 NIO 强制删除...");
                            java.nio.file.Files.walkFileTree(parentPath.toPath(), new java.nio.file.SimpleFileVisitor<java.nio.file.Path>() {
                                @Override
                                public java.nio.file.FileVisitResult visitFile(java.nio.file.Path file, java.nio.file.attribute.BasicFileAttributes attrs) throws java.io.IOException {
                                    java.nio.file.Files.delete(file);
                                    return java.nio.file.FileVisitResult.CONTINUE;
                                }
                                @Override
                                public java.nio.file.FileVisitResult postVisitDirectory(java.nio.file.Path dir, java.io.IOException exc) throws java.io.IOException {
                                    java.nio.file.Files.delete(dir);
                                    return java.nio.file.FileVisitResult.CONTINUE;
                                }
                            });
                            log.info("✅ NIO 补刀删除成功");
                        } catch (Exception e) {
                            log.error("❌ NIO 删除也失败了，具体原因: ", e);
                        }
                    }
                }
            }
        }
    }

    /**
     * 辅助方法：编译代码
     */
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
        // 编译日志收集
        dockerClient.logContainerCmd(containerId).withStdOut(true).withStdErr(true).withFollowStream(true)
                .exec(new LogContainerResultCallback() {
                    @Override public void onNext(Frame item) {
                        if (compileLog.length() < MAX_OUTPUT_LENGTH) {
                            compileLog.append(new String(item.getPayload(), StandardCharsets.UTF_8));
                        }
                    }
                }).awaitCompletion(10, TimeUnit.SECONDS);

        InspectContainerResponse inspectResponse = dockerClient.inspectContainerCmd(containerId).exec();
        Long exitCode = inspectResponse.getState().getExitCodeLong();
        dockerClient.removeContainerCmd(containerId).withForce(true).exec();

        // 如果退出码不为0，说明编译失败，返回日志
        if (exitCode != 0) return compileLog.toString();
        return null;
    }

    /**
     * 辅助方法：构造错误响应
     */
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