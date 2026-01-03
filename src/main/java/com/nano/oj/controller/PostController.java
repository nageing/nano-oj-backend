package com.nano.oj.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.google.gson.Gson;
import com.nano.oj.common.BaseResponse;
import com.nano.oj.common.ErrorCode;
import com.nano.oj.common.ResultUtils;
import com.nano.oj.exception.BusinessException;
import com.nano.oj.model.dto.post.PostAddRequest;
import com.nano.oj.model.dto.post.PostQueryRequest;
import com.nano.oj.model.entity.Post;
import com.nano.oj.model.entity.User;
import com.nano.oj.model.vo.PostVO;
import com.nano.oj.service.PostService;
import com.nano.oj.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 帖子接口
 */
@RestController
@RequestMapping("/post")
@Slf4j
public class PostController {

    @Resource
    private PostService postService;

    @Resource
    private UserService userService;

    private final static Gson GSON = new Gson();

    /**
     * 发帖
     */
    @PostMapping("/add")
    public BaseResponse<Long> addPost(@RequestBody PostAddRequest postAddRequest, HttpServletRequest request) {
        if (postAddRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        User loginUser = userService.getLoginUser(request);

        Post post = new Post();
        BeanUtils.copyProperties(postAddRequest, post);

        // 处理标签 List -> JSON String
        List<String> tags = postAddRequest.getTags();
        if (tags != null) {
            post.setTags(GSON.toJson(tags));
        }

        post.setUserId(loginUser.getId());
        post.setFavourNum(0);
        post.setThumbNum(0);

        boolean result = postService.save(post);
        if (!result) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "发帖失败");
        }
        return ResultUtils.success(post.getId());
    }

    /**
     * 分页查询帖子列表
     */
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<PostVO>> listPostVOByPage(@RequestBody PostQueryRequest postQueryRequest,
                                                       HttpServletRequest request) {
        if (postQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        long current = postQueryRequest.getCurrent();
        long size = postQueryRequest.getPageSize();

        // 限制
        if (size > 20) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        // 获取分页数据
        Page<Post> postPage = postService.page(new Page<>(current, size),
                postService.getQueryWrapper(postQueryRequest));

        // 转换为 VO
        return ResultUtils.success(postService.getPostVOPage(postPage));
    }
}