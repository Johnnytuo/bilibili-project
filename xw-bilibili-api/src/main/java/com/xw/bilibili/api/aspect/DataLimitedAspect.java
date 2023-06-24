package com.xw.bilibili.api.aspect;

import com.xw.bilibili.api.support.UserSupport;
import com.xw.bilibili.domain.UserMoment;
import com.xw.bilibili.domain.annotation.ApiLimitedRole;
import com.xw.bilibili.domain.auth.UserRole;
import com.xw.bilibili.domain.constant.AuthRoleConstant;
import com.xw.bilibili.domain.exception.ConditionException;
import com.xw.bilibili.service.UserRoleService;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Aspect
@Order(1)
@Component
public class DataLimitedAspect {

    @Autowired
    private UserSupport userSupport;

    @Autowired
    private UserRoleService userRoleService;

    @Pointcut("@annotation(com.xw.bilibili.domain.annotation.DataLimited)")
    public void check(){
    }


    @Before("check() ")
    public void doBefore(JoinPoint joinPoint){
        //取得userId
        Long userId = userSupport.getCurrentUserId();
        //获取id所有的角色列表
        List<UserRole> userRoleList = userRoleService.getUserRoleByUserId(userId);
        //获取所有角色的角色编码
        Set<String> roleCodeSet = userRoleList.stream().map(UserRole :: getRoleCode).collect(Collectors.toSet());
        //获取切入的方法中的参数
        Object[] args = joinPoint.getArgs();
        for(Object arg : args){
            if(arg instanceof UserMoment){
                UserMoment userMoment = (UserMoment) arg;
                String type = userMoment.getType();
                if(roleCodeSet.contains(AuthRoleConstant.ROLE_LV0) && !"0".equals(type)){
                    throw new ConditionException("Parameter exception.");
                }
            }
        }
    }
}
