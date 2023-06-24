package com.xw.bilibili.api.aspect;

import com.xw.bilibili.api.support.UserSupport;
import com.xw.bilibili.domain.annotation.ApiLimitedRole;
import com.xw.bilibili.domain.auth.UserRole;
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
public class ApiLimitedRoleAspect {

    @Autowired
    private UserSupport userSupport;

    @Autowired
    private UserRoleService userRoleService;

    @Pointcut("@annotation(com.xw.bilibili.domain.annotation.ApiLimitedRole)")
    public void check(){
    }

    //在给角色编码列表传值时，需要传入哪些角色会被限制，因此在切面中需要获取这个注解，以此获取需要被传入的角色再比对
    @Before("check() && @annotation(apiLimitedRole)")
    public void doBefore(JoinPoint joinPoint, ApiLimitedRole apiLimitedRole){
        //取得userId
        Long userId = userSupport.getCurrentUserId();
        //获取id所有的角色列表
        List<UserRole> userRoleList = userRoleService.getUserRoleByUserId(userId);
        //通过注解中的方法获取需要被限制的角色编码
        String[] limitedRoleCodeList = apiLimitedRole.limitedRoleCodeList();
        //对比上面两个列表即可
        //把两个列表先转换为set
        Set<String> limitedRoleCodeSet = Arrays.stream(limitedRoleCodeList).collect(Collectors.toSet());
        Set<String> roleCodeSet = userRoleList.stream().map(UserRole :: getRoleCode).collect(Collectors.toSet());
        //取交集，roleCodeSet里面剩余的值就是交集
        roleCodeSet.retainAll(limitedRoleCodeSet);
        //如果用户当前关联的角色编码跟限制编码列表有重合，就认定这个用户不能进行这个接口的调用
        if(roleCodeSet.size() > 0){
            throw new ConditionException("Insufficient Permissions.");
        }
    }
}
