package com.nano.oj.controller;

import com.nano.oj.common.BaseResponse;
import com.nano.oj.common.ResultUtils;
import com.nano.oj.model.dto.user.UserLoginRequest;
import com.nano.oj.model.dto.user.UserRegisterRequest;
import com.nano.oj.model.entity.User;
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

    // 获取当前登录用户接口
    @GetMapping("/get/login")
    public BaseResponse<User> getLoginUser(HttpServletRequest request) {
        User user = userService.getLoginUser(request);
        return new BaseResponse<>(0, user, "获取成功");
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
}