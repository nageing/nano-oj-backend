package com.nano.oj;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.LogContainerResultCallback;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;

import java.util.concurrent.TimeUnit;

@SpringBootTest
class NanoOjBackendApplicationTests {

    @Test
    void contextLoads() {
        // 这是 Spring Boot 自带的检查上下文能否启动的测试，保留即可
    }

    /**
     * 专门测试 Docker 是否连通，以及能否正常执行 Python 代码
     */
    @Test
    void testDockerRun() {
        // 1. 配置 Docker 连接
        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .maxConnections(100)
                .connectionTimeout(java.time.Duration.ofSeconds(30))
                .responseTimeout(java.time.Duration.ofSeconds(45))
                .build();
        DockerClient dockerClient = DockerClientBuilder.getInstance(config)
                .withDockerHttpClient(httpClient)
                .build();

        // 2. 准备代码 (注意：python -u)
        String image = "python:3.9";
        String code = "print('Hello from SpringBoot Test! 10+20=', 10+20)";

        // 3. 创建容器
        CreateContainerResponse container = dockerClient.createContainerCmd(image)
                .withCmd("python", "-u", "-c", code) // ⭐ 修改点1: 增加 -u 参数
                .withNetworkDisabled(true)
                .withHostConfig(HostConfig.newHostConfig()
                        .withMemory(100 * 1024 * 1024L)
                        .withCpuCount(1L))
                .exec();

        String containerId = container.getId();
        System.out.println("📦 容器已创建, ID: " + containerId);

        try {
            // 4. 启动容器
            dockerClient.startContainerCmd(containerId).exec();

            // 5. 获取日志
            StringBuilder resultLog = new StringBuilder();

            dockerClient.logContainerCmd(containerId)
                    .withStdOut(true)
                    .withStdErr(true)
                    .withFollowStream(true)
                    .withTail(100)
                    .exec(new LogContainerResultCallback() {
                        @Override
                        public void onNext(Frame item) {
                            System.out.println("日志流类型: " + item.getStreamType());
                            resultLog.append(new String(item.getPayload()));
                        }
                    })
                    .awaitCompletion(5, TimeUnit.SECONDS);

            System.out.println("============================================");
            System.out.println("🚀 程序运行输出: " + resultLog.toString().trim());
            System.out.println("============================================");

        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            dockerClient.removeContainerCmd(containerId).exec();
            System.out.println("🧹 容器已删除");
        }
    }
}