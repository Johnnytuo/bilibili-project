package com.xw.bilibili.api;

import com.alibaba.fastjson.JSONObject;
import com.xw.bilibili.api.support.UserSupport;
import com.xw.bilibili.domain.JsonResponse;
import com.xw.bilibili.domain.PageResult;
import com.xw.bilibili.domain.User;
import com.xw.bilibili.domain.UserInfo;
import com.xw.bilibili.service.UserFollowingService;
import com.xw.bilibili.service.UserService;
import com.xw.bilibili.service.util.RSAUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

@RestController
public class UserApi {

    @Autowired
    private UserService userService;

    @Autowired
    private UserSupport userSupport;

    @Autowired
    private UserFollowingService userFollowingService;

    @GetMapping("/users")
    //通过header中的token指定用户，再看一下测试过程思考一下过程
    public JsonResponse<User> getUserInfo(){
        Long userId = userSupport.getCurrentUserId();
        User user = userService.getUserInfo(userId);
        return new JsonResponse<>(user);
    }

    @GetMapping("/rsa-pks")
    public JsonResponse<String> getRsaPublicKey(){
        String pk = RSAUtil.getPublicKeyStr();
        return new JsonResponse<>(pk); //返回一个字符串类型的数据，经过Json包装，内容是RSA的公钥
    }

    @PostMapping("/users") //新建一个用户
    public JsonResponse<String> addUser(@RequestBody User user){//RequestBody封装成Json类型提供给我们
        userService.addUser(user);
        return JsonResponse.success();//addUser方法中已经把可能失败的原因全部写好，因此直接用success即可
    }

    //登陆成功后返回一个令牌给前端
    @PostMapping("/user-tokens")
    public JsonResponse<String> login(@RequestBody User user) throws Exception{
        String token = userService.login(user);
        return new JsonResponse<>(token);
    }


    @PutMapping("/users")
    public JsonResponse<String> updatesUsers(@RequestBody User user) throws Exception{
        userService.updateUsers(user);
        return JsonResponse.success();
    }

    @PutMapping("/user-infos")
    public JsonResponse<String> updateUserInfos(@RequestBody UserInfo userInfo){
        Long userId = userSupport.getCurrentUserId(); //userId一定要从token中获取，不能从前端直接传入，容易被拦截仿造
        userInfo.setUserId(userId);
        userService.updateUserInfos(userInfo);
        return JsonResponse.success();
    }

    //用户分页查询
    @GetMapping("/user-infos")
    //在domain中新设PageResult实体类，用于封装分页查询的结果
    //@RequestParam注解表示这两个参数必传，最后一个nick不必须
    public JsonResponse<PageResult<UserInfo>> pageListUserInfos(@RequestParam Integer no, @RequestParam Integer size, String nick){
        Long userId = userSupport.getCurrentUserId();
        //JSONObject是阿里fastJson包中的，实现了map类，可以当做map使用
        JSONObject params = new JSONObject();
        params.put("no", no);
        params.put("size", size);
        params.put("nick", nick);
        params.put("userId", userId);
        PageResult<UserInfo> result = userService.pageListUserInfos(params);
        //判断result中的用户是否被当前用户关注过，如果关注过就不能再关注了
        if(result.getTotal() > 0){
            List<UserInfo> checkedUserInfoList = userFollowingService.checkFollowingStatus(result.getList(), userId);
            result.setList(checkedUserInfoList);
        }
        return new JsonResponse<>(result);
    }

    //双token登陆接口，返回双token
    @PostMapping("/user-dts")
    public JsonResponse<Map<String, Object>> loginForDts(@RequestBody User user) throws Exception{
        Map<String, Object> map = userService.loginForDts(user);
        return new JsonResponse<>(map);
    }

    //退出登陆时需要删除现有的refreshToken，删除refreshToken接口
    @DeleteMapping("/refresh-tokens")
    public JsonResponse<String> logout(HttpServletRequest request){
        //通过HttpServletRequest可以获取请求头等信息，以此获取token
        String refreshToken = request.getHeader("refreshToken");
        Long userId = userSupport.getCurrentUserId();
        userService.logout(refreshToken, userId);
        return JsonResponse.success();
    }

    //当用户拿token访问资源时，如果token过期，返回前端过期提示
    //如果refreshToken没有过期，就直接给前端返回一个新的accessToken，用户可以继续访问系统资源
    // 刷新accessToken接口
    @PostMapping("/access-tokens")
    public JsonResponse<String> refreshAccessToken(HttpServletRequest request) throws Exception{
        String refreshToken = request.getHeader("refreshToken");
        String accessToken = userService.refreshAccessToken(refreshToken);
        return new JsonResponse<>(accessToken);
    }

}
