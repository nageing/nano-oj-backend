package com.nano.oj.controller;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.PutObjectRequest;
import com.nano.oj.common.BaseResponse;
import com.nano.oj.common.ErrorCode;
import com.nano.oj.common.ResultUtils;
import com.nano.oj.exception.BusinessException;
import com.nano.oj.model.entity.User;
import com.nano.oj.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/file")
@Slf4j
public class FileController {

    @Resource
    private UserService userService;

    @Value("${aliyun.oss.endpoint}")
    private String endpoint;

    @Value("${aliyun.oss.access-key-id}")
    private String accessKeyId;

    @Value("${aliyun.oss.access-key-secret}")
    private String accessKeySecret;

    @Value("${aliyun.oss.bucket-name}")
    private String bucketName;

    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList("jpg", "jpeg", "png", "webp", "gif");
    private static final long MAX_FILE_SIZE = 2 * 1024 * 1024; // 2MB

    @PostMapping("/upload")
    public BaseResponse<String> uploadFile(@RequestPart("file") MultipartFile multipartFile,
                                           HttpServletRequest request) {
        // 1. 获取当前登录用户
        User loginUser = userService.getLoginUser(request);
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }

        // 2. 文件基础校验
        if (multipartFile == null || multipartFile.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件为空");
        }
        if (multipartFile.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件大小不能超过 2MB");
        }
        String originalFilename = multipartFile.getOriginalFilename();
        String suffix = FileUtil.getSuffix(originalFilename);
        if (StrUtil.isBlank(suffix) || !ALLOWED_EXTENSIONS.contains(suffix.toLowerCase())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "仅支持 jpg, png, webp, gif 格式");
        }

        // 3. ✨ 核心修改：使用用户 ID 命名，确保“一人一图”
        String filename = loginUser.getId() + "." + suffix;
        String objectName = "head-portrait/" + filename;

        OSS ossClient = null;
        try {
            ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);

            // 4. ✨ 核心修改：删除旧头像 (防止 id.jpg 和 id.png 同时存在)
            String oldAvatarUrl = loginUser.getUserAvatar();
            String ossPrefix = "https://" + bucketName + "." + endpoint + "/";

            // 只有当旧头像是本站 OSS 的资源时才执行删除操作
            if (StrUtil.isNotBlank(oldAvatarUrl) && oldAvatarUrl.startsWith(ossPrefix)) {
                String oldObjectName = oldAvatarUrl.substring(ossPrefix.length());
                // 如果旧文件名和新文件名不一样（例如后缀变了），删除旧的
                // 如果一样，OSS 上传会自动覆盖，不删也行，但删了更保险
                if (!oldObjectName.equals(objectName)) {
                    try {
                        ossClient.deleteObject(bucketName, oldObjectName);
                    } catch (Exception e) {
                        log.warn("删除旧头像失败: {}", oldObjectName);
                    }
                }
            }

            // 5. 上传新头像
            PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, objectName, multipartFile.getInputStream());
            ossClient.putObject(putObjectRequest);

            // 6. 构造访问链接
            String fileUrl = ossPrefix + objectName;

            // 7. ✨ 核心修改：立即更新数据库
            // 之前只返回 URL 没更新 DB，所以你刷新页面后头像又变回去了
            User updateUser = new User();
            updateUser.setId(loginUser.getId());
            updateUser.setUserAvatar(fileUrl);
            boolean updateResult = userService.updateById(updateUser);
            if (!updateResult) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "头像更新失败");
            }

            return ResultUtils.success(fileUrl);

        } catch (Exception e) {
            log.error("OSS上传失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "上传失败");
        } finally {
            if (ossClient != null) {
                ossClient.shutdown();
            }
        }
    }
}