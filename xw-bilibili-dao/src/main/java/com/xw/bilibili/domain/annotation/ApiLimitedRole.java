package com.xw.bilibili.domain.annotation;

import org.springframework.stereotype.Component;

import java.lang.annotation.*;

//注解在运行阶段进行
@Retention(RetentionPolicy.RUNTIME)
//注解用于方法中
@Target({ElementType.METHOD})
@Documented
@Component
public @interface ApiLimitedRole {

    //要限制的角色编码的列表，默认是一个空列表
    String[] limitedRoleCodeList() default{};

}
