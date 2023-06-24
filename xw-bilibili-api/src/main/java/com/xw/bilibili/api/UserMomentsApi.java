package com.xw.bilibili.api;

import com.xw.bilibili.api.support.UserSupport;
import com.xw.bilibili.domain.JsonResponse;
import com.xw.bilibili.domain.UserMoment;
import com.xw.bilibili.domain.annotation.ApiLimitedRole;
import com.xw.bilibili.domain.annotation.DataLimited;
import com.xw.bilibili.domain.auth.AuthRole;
import com.xw.bilibili.domain.constant.AuthRoleConstant;
import com.xw.bilibili.service.UserMomentsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class UserMomentsApi {

    @Autowired
    private UserMomentsService userMomentsService;

    @Autowired
    private UserSupport userSupport;


    //limitedRoleCodeList中传入的角色的编码不被允许调用标注的api接口，对权限控制时只需要引入注解然后标明哪些角色要被限制即可
    @ApiLimitedRole(limitedRoleCodeList = {AuthRoleConstant.ROLE_LV0})
    @DataLimited
    @PostMapping("/user-moments")
    public JsonResponse<String> addUserMoments(@RequestBody UserMoment userMoment) throws Exception{
        Long userId = userSupport.getCurrentUserId();
        userMoment.setUserId(userId);
        userMomentsService.addUserMoments(userMoment);
        return JsonResponse.success();
    }

    //查询用户关注的用户的动态
    @GetMapping("/user-subscribed-moments")
    public JsonResponse<List<UserMoment>> getUserSubscribedMoments(){
        Long userId = userSupport.getCurrentUserId();
        List<UserMoment> list = userMomentsService.getUserSubscribedMoments(userId);
        return new JsonResponse<>(list);
    }
}
