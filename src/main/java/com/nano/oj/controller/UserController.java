package com.nano.oj.controller;

import com.nano.oj.common.BaseResponse;
import com.nano.oj.common.ErrorCode;
import com.nano.oj.common.ResultUtils;
import com.nano.oj.exception.BusinessException;
import com.nano.oj.model.dto.user.UserLoginRequest;
import com.nano.oj.model.dto.user.UserRegisterRequest;
import com.nano.oj.model.dto.user.UserUpdateMyRequest;
import com.nano.oj.model.dto.user.UserUpdatePasswordRequest;
import com.nano.oj.model.entity.User;
import com.nano.oj.model.vo.UserVO;
import com.nano.oj.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;

/**
 * 用户接口
 */
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private UserService userService;

    // 根据 id 获取用户
    @GetMapping("/{id}")
    public BaseResponse getUserById(@PathVariable Long id) {
        User user = userService.getById(id);
        if (user == null) {
            return ResultUtils.error(404, "用户不存在");
        }
        return ResultUtils.success(user);
    }

    // 注册
    @PostMapping("/register")
    public BaseResponse<Long> userRegister(@RequestBody UserRegisterRequest userRegisterRequest) {
        if (userRegisterRequest == null) {
            throw new RuntimeException("请求参数为空");
        }
        String userAccount = userRegisterRequest.getUserAccount();
        String userPassword = userRegisterRequest.getUserPassword();
        String checkPassword = userRegisterRequest.getCheckPassword();

        // 调用 Service
        long result = userService.userRegister(userAccount, userPassword, checkPassword);

        return new BaseResponse<>(0, result, "注册成功");
    }

    // 登录
    @PostMapping("/login")
    public BaseResponse<User> userLogin(@RequestBody UserLoginRequest userLoginRequest, HttpServletRequest request) {
        if (userLoginRequest == null) {
            throw new RuntimeException("请求参数为空");
        }

        String userAccount = userLoginRequest.getUserAccount();
        String userPassword = userLoginRequest.getUserPassword();

        if (StringUtils.isAnyBlank(userAccount, userPassword)) {
            throw new RuntimeException("参数不能为空");
        }

        User user = userService.userLogin(userAccount, userPassword, request);

        return new BaseResponse<>(0, user, "登录成功");
    }

    /**
     * 获取当前登录用户 (兼容版)
     */
    @GetMapping("/get/login")
    public BaseResponse<UserVO> getLoginUser(HttpServletRequest request) {
        // 尝试获取用户，如果没登录，直接返回 null，不要报 500 错误
        try {
            User user = userService.getLoginUser(request);
            return ResultUtils.success(userService.getUserVO(user));
        } catch (Exception e) {
            // 捕获所有异常（包括未登录异常），返回 null 表示“没人登录”
            return ResultUtils.success(null);
        }
    }

    // 注销接口
    @PostMapping("/logout")
    public BaseResponse<Boolean> userLogout(HttpServletRequest request) {
        if (request == null) {
            throw new RuntimeException("请求错误");
        }
        boolean result = userService.userLogout(request);
        return new BaseResponse<>(0, result, "退出成功");
    }

    /**
     * 更新个人信息
     */
    @PostMapping("/update/my")
    public BaseResponse<Boolean> updateMyUser(@RequestBody UserUpdateMyRequest userUpdateMyRequest,
                                              HttpServletRequest request) {
        if (userUpdateMyRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        // 1. 获取当前登录用户
        User loginUser = userService.getLoginUser(request);

        // 2. 封装更新对象
        User user = new User();
        user.setId(loginUser.getId());
        user.setUserName(userUpdateMyRequest.getUserName());
        user.setUserAvatar(userUpdateMyRequest.getUserAvatar());
        user.setUserProfile(userUpdateMyRequest.getUserProfile());

        // 3. 更新数据库
        boolean result = userService.updateById(user);
        if (!result) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "更新失败");
        }

        return ResultUtils.success(true);
    }

    /**
     * 修改密码
     */
    @PostMapping("/update/password")
    public BaseResponse<Boolean> updatePassword(@RequestBody UserUpdatePasswordRequest updatePasswordRequest,
                                                HttpServletRequest request) {
        if (updatePasswordRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        boolean result = userService.updatePassword(updatePasswordRequest, request);
        return ResultUtils.success(result);
    }
}