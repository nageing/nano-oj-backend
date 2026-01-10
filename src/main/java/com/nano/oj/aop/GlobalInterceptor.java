package com.nano.oj.aop; // 或者放在 interceptor 包

import com.nano.oj.common.UserHolder;
import com.nano.oj.model.entity.User;
import com.nano.oj.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class GlobalInterceptor implements HandlerInterceptor {

    @Resource
    private UserService userService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. 尝试获取登录用户
        // 你的 userService.getLoginUser 可能会抛异常(未登录)，这里我们要 catch 住
        // 因为对于公开接口(如排行榜)，未登录也应该允许访问，只是身份是 null
        try {
            User loginUser = userService.getLoginUser(request);
            if (loginUser != null) {
                // ✅ 存入 ThreadLocal
                UserHolder.saveUser(loginUser);
            }
        } catch (Exception e) {
            // 未登录，忽略即可，UserHolder 里就是 null
        }
        return true; // 永远放行，只负责存人，不负责拦截
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // ✅ 请求结束，清理 ThreadLocal
        UserHolder.removeUser();
    }
}