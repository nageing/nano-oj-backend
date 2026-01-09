package com.nano.oj.aop;

import com.nano.oj.annotation.AuthCheck;
import com.nano.oj.common.ErrorCode;
import com.nano.oj.constant.UserConstant;
import com.nano.oj.exception.BusinessException;
import com.nano.oj.model.entity.User;
import com.nano.oj.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 权限校验 AOP
 */
@Aspect
@Component
public class AuthInterceptor {

    @Resource
    private UserService userService;

    /**
     * 执行拦截
     */
    @Around("@annotation(authCheck)")
    public Object doInterceptor(ProceedingJoinPoint joinPoint, AuthCheck authCheck) throws Throwable {
        String mustRole = authCheck.mustRole();
        RequestAttributes requestAttributes = RequestContextHolder.currentRequestAttributes();
        HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();

        // 获取当前登录用户
        User loginUser = userService.getLoginUser(request);

        // 如果不需要权限，直接放行
        if (StringUtils.isBlank(mustRole)) {
            return joinPoint.proceed();
        }

        // 获取当前用户角色
        String userRole = loginUser.getUserRole();

        // 如果被封号，直接拒绝
        if (UserConstant.BAN_ROLE.equals(userRole)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "账号已封禁");
        }

        // 如果必须是管理员，进行校验
        if (UserConstant.ADMIN_ROLE.equals(mustRole)) {
            if (!UserConstant.ADMIN_ROLE.equals(userRole)) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无管理员权限");
            }
        }

        // 通过权限校验，放行
        return joinPoint.proceed();
    }
}