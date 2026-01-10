package com.nano.oj.common;

import com.nano.oj.model.entity.User;

/**
 * 存放当前线程登录用户的工具类
 */
public class UserHolder {
    private static final ThreadLocal<User> tl = new ThreadLocal<>();

    public static void saveUser(User user){
        tl.set(user);
    }

    public static User getUser(){
        return tl.get();
    }

    public static void removeUser(){
        tl.remove();
    }
}