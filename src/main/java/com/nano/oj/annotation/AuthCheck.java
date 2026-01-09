package com.nano.oj.annotation;

import java.lang.annotation.*;

/**
 * 权限校验注解
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuthCheck {

    /**
     * 必须具备的角色
     */
    String mustRole() default "";

}