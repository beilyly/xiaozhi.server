package com.xiaozhi.common.interceptor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记不需要登录验证的接口
 * 在控制器方法上使用此注解，可以跳过登录验证
 * @author wwtang5
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface UnLogin {
    /**
     * 是否跳过登录验证，默认为true
     */
    boolean value() default true;
} 