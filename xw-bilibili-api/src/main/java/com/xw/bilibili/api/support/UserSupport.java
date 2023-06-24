package com.xw.bilibili.api.support;

import com.xw.bilibili.domain.exception.ConditionException;
import com.xw.bilibili.service.util.TokenUtil;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component //可让UserSupport在项目构建时即以依赖形式注入
public class UserSupport {

    //获取当前用户的id
    public Long getCurrentUserId(){
        //抓取前端请求
        ServletRequestAttributes requestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        String token = requestAttributes.getRequest().getHeader("token");
        Long userId = TokenUtil.verifyToken(token);
        if(userId < 0){
            throw new ConditionException("Invalid user."); //Id如果小于0，是非法的
        }
        return userId;
    }
}
