package com.nano.oj.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.nano.oj.model.dto.user.UserUpdatePasswordRequest;
import com.nano.oj.model.entity.User;
import com.nano.oj.model.vo.LoginUserVO;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 用户服务接口
 */
public interface UserService extends IService<User> {
    // 返回值是新用户的 id
    /**
     * 用户注册
     *
     * @param userAccount   用户账号
     * @param userPassword  用户密码
     * @param checkPassword 校验密码
     * @return 新用户 ID
     */
    long userRegister(String userAccount, String userPassword, String checkPassword);

    /**
     * 用户登录
     *
     * @param userAccount  账号
     * @param userPassword 密码
     * @param request      请求对象（用来记录登录态）
     * @return 脱敏后的用户信息
     */
    User userLogin(String userAccount, String userPassword, HttpServletRequest request);

    /**
     * 获取当前登录用户
     *
     * @param request HTTP 请求对象 (我们需要靠它来获取 Session)
     * @return 脱敏后的用户信息
     */
    User getLoginUser(HttpServletRequest request);

    /**
     * 用户注销
     *
     * @param request HTTP 请求对象 (我们需要靠它来移除 Session)
     * @return 是否注销成功
     */
    boolean userLogout(HttpServletRequest request);

    /**
     * 是否为管理员
     *
     * @param user 用户信息
     * @return 是否为管理员
     */
    boolean isAdmin(User user);

    /**
     * 获取脱敏后的登录用户信息
     * @param user 用户信息
     */
    LoginUserVO getLoginUserVO(User user);

    /**
     * 修改密码
     */
    boolean updatePassword(UserUpdatePasswordRequest updatePasswordRequest, HttpServletRequest request);
}