package com.xw.bilibili.domain.annotation;

import org.springframework.stereotype.Component;

import java.lang.annotation.*;

//注解在运行阶段进行
@Retention(RetentionPolicy.RUNTIME)
//注解用于方法中
@Target({ElementType.METHOD})
@Documented
@Component
public @interface DataLimited {


}
