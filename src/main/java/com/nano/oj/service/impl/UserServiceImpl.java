package com.nano.oj.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nano.oj.common.ErrorCode;
import com.nano.oj.exception.BusinessException;
import com.nano.oj.mapper.UserMapper;
import com.nano.oj.model.dto.user.UserUpdatePasswordRequest;
import com.nano.oj.model.entity.User;
import com.nano.oj.model.vo.UserVO;
import com.nano.oj.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils; // 如果爆红，看下面的提示
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Override
    public long userRegister(String userAccount, String userPassword, String checkPassword) {
        // 1. 校验参数是否为空
        if (StringUtils.isAnyBlank(userAccount, userPassword, checkPassword)) {
            throw new RuntimeException("参数为空");
        }
        // 2. 校验账号长度 (不小于4位)
        if (userAccount.length() < 4) {
            throw new RuntimeException("用户账号长度至少要4位!");
        }
        // 3. 校验密码长度 (不小于8位)
        if (userPassword.length() < 8) {
            throw new RuntimeException("用户密码长度至少要8位!");
        }
        // 4. 校验两次密码是否一致
        if (!userPassword.equals(checkPassword)) {
            throw new RuntimeException("两次输入的密码不一致");
        }

        // 5. 校验账号是否重复 (数据库查一下)
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_account", userAccount);
        long count = this.count(queryWrapper);
        if (count > 0) {
            throw new RuntimeException("账号重复");
        }

        // 6. 创建新用户
        User user = new User();
        user.setUserAccount(userAccount);
        user.setUserPassword(userPassword); // TODO: 以后这里要加 MD5 加密，现在先存明文方便测试
        user.setUserName("新用户" + userAccount);
        user.setUserRole("user");
        // 自动生成一个随机头像
        user.setUserAvatar("https://api.dicebear.com/7.x/avataaars/svg?seed=" + userAccount);

        // 7. 保存到数据库
        boolean saveResult = this.save(user);
        if (!saveResult) {
            throw new RuntimeException("注册失败，数据库错误");
        }

        return user.getId();
    }

    @Override
    public User userLogin(String userAccount, String userPassword, HttpServletRequest request) {
        // 1. 校验参数
        if (StringUtils.isAnyBlank(userAccount, userPassword)) {
            throw new RuntimeException("参数为空");
        }
        if (userAccount.length() < 4) {
            throw new RuntimeException("账号错误");
        }
        if (userPassword.length() < 8) {
            throw new RuntimeException("密码错误");
        }

        // 2. 查询用户是否存在
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_account", userAccount);
        User user = this.getOne(queryWrapper);

        // 用户不存在
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        // 3. 校验密码 (这里目前是明文比对，以后改加密)
        if (!user.getUserPassword().equals(userPassword)) {
            throw new RuntimeException("密码错误");
        }

        // 4. 记录用户的登录态 (Session)
        // 这里的 "user_login_state" 是一个 key，以后取用户信息就靠它
        request.getSession().setAttribute("user_login_state", user);

        // 5. 返回脱敏后的用户信息 (重要！去掉密码)
        User safetyUser = new User();
        safetyUser.setId(user.getId());
        safetyUser.setUserAccount(user.getUserAccount());
        safetyUser.setUserName(user.getUserName());
        safetyUser.setUserAvatar(user.getUserAvatar());
        safetyUser.setUserRole(user.getUserRole());
        safetyUser.setCreateTime(user.getCreateTime());
        // 注意：这里没有 setPassword，所以密码是 null，这就安全了

        return safetyUser;
    }

    @Override
    public User getLoginUser(HttpServletRequest request) {
        // 1. 先判断是否已登录
        Object userObj = request.getSession().getAttribute("user_login_state");
        User currentUser = (User) userObj;
        if (currentUser == null || currentUser.getId() == null) {
            throw new RuntimeException("未登录");
        }

        // 2. 从数据库查询（追求数据实时性）
        long userId = currentUser.getId();
        User user = this.getById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        // 3. 脱敏（还是那句老话，千万别把密码传出去）
        User safetyUser = new User();
        safetyUser.setId(user.getId());
        safetyUser.setUserAccount(user.getUserAccount());
        safetyUser.setUserName(user.getUserName());
        safetyUser.setUserAvatar(user.getUserAvatar());
        safetyUser.setUserRole(user.getUserRole());
        safetyUser.setUserProfile(user.getUserProfile());
        safetyUser.setCreateTime(user.getCreateTime());

        return safetyUser;
    }

    @Override
    public boolean userLogout(HttpServletRequest request) {
        // 移除登录态，就是这么简单
        request.getSession().removeAttribute("user_login_state");
        return true;
    }

    /**
     * 是否为管理员
     * @param user 用户信息
     * @return
     */
    @Override
    public boolean isAdmin(User user) {
        return user != null && "admin".equals(user.getUserRole());
    }

    /**
     * 获取脱敏后的登录用户信息
     * @param user 用户信息
     * @return
     */
    @Override
    public UserVO getUserVO(User user) {
        if (user == null) {
            return null;
        }
        UserVO UserVO = new UserVO();
        // 自动把 User 的属性复制给 UserVO (名字一样的字段)
        BeanUtils.copyProperties(user, UserVO);
        return UserVO;
    }

    /**
     * 修改密码
     * @param updatePasswordRequest
     * @param request
     * @return
     */
    @Override
    public boolean updatePassword(UserUpdatePasswordRequest updatePasswordRequest, HttpServletRequest request) {
        // 1. 校验参数
        String oldPassword = updatePasswordRequest.getOldPassword();
        String newPassword = updatePasswordRequest.getNewPassword();
        String checkPassword = updatePasswordRequest.getCheckPassword();

        if (StringUtils.isAnyBlank(oldPassword, newPassword, checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数不能为空");
        }
        if (newPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "新密码长度不能少于8位");
        }
        if (!newPassword.equals(checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "两次新密码输入不一致");
        }

        // 2. 获取当前登录用户
        User loginUser = getLoginUser(request);
        // 为了安全，重新查一次数据库获取最新密码
        User user = this.getById(loginUser.getId());

        // 3. 校验旧密码
        if (!user.getUserPassword().equals(oldPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "旧密码错误");
        }

        // 4. 更新密码
        user.setUserPassword(newPassword);
        return this.updateById(user);
    }
}