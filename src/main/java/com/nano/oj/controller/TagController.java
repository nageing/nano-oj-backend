package com.nano.oj.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.nano.oj.common.BaseResponse;
import com.nano.oj.common.ErrorCode;
import com.nano.oj.common.ResultUtils;
import com.nano.oj.exception.BusinessException;
import com.nano.oj.model.entity.Tag;
import com.nano.oj.model.entity.User;
import com.nano.oj.service.TagService;
import com.nano.oj.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 标签接口
 */
@RestController
@RequestMapping("/tag")
public class TagController {

    @Resource
    private TagService tagService;

    @Resource
    private UserService userService;

    /**
     * 获取所有标签列表
     */
    @GetMapping("/list")
    public BaseResponse<List<Tag>> listTag() {
        QueryWrapper<Tag> queryWrapper = new QueryWrapper<>();
        // 按创建时间升序，方便管理
        queryWrapper.orderByAsc("create_time");
        List<Tag> list = tagService.list(queryWrapper);
        return ResultUtils.success(list);
    }

    /**
     * 创建标签 (仅管理员)
     */
    @PostMapping("/add")
    public BaseResponse<Long> addTag(@RequestBody Tag tag, HttpServletRequest request) {
        if (tag == null || StringUtils.isBlank(tag.getName())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        // 鉴权：仅管理员可以创建标准标签
        if (!userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }

        // 校验标签是否已存在
        QueryWrapper<Tag> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("name", tag.getName());
        long count = tagService.count(queryWrapper);
        if (count > 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "标签已存在");
        }

        tag.setUserId(loginUser.getId());
        boolean result = tagService.save(tag);
        if (!result) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR);
        }
        return ResultUtils.success(tag.getId());
    }

    /**
     * 删除标签 (仅管理员)
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteTag(@RequestBody Tag tag, HttpServletRequest request) {
        if (tag == null || tag.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        if (!userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        boolean b = tagService.removeById(tag.getId());
        return ResultUtils.success(b);
    }

    /**
     * 修改标签 (仅管理员)
     */
    @PostMapping("/update")
    public BaseResponse<Boolean> updateTag(@RequestBody Tag tag, HttpServletRequest request) {
        if (tag == null || tag.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        // 仅管理员可修改
        if (!userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 也可以加一个校验：修改后的名字是否和别的标签重复
        boolean b = tagService.updateById(tag);
        return ResultUtils.success(b);
    }
}