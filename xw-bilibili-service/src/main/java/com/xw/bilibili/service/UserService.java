package com.xw.bilibili.service;

import com.alibaba.fastjson.JSONObject;
import com.mysql.cj.util.StringUtils;
import com.xw.bilibili.dao.UserDao;
import com.xw.bilibili.domain.PageResult;
import com.xw.bilibili.domain.RefreshTokenDetail;
import com.xw.bilibili.domain.User;
import com.xw.bilibili.domain.UserInfo;
import com.xw.bilibili.domain.constant.UserConstant;
import com.xw.bilibili.domain.exception.ConditionException;
import com.xw.bilibili.service.util.MD5Util;
import com.xw.bilibili.service.util.RSAUtil;
import com.xw.bilibili.service.util.TokenUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class UserService {

    @Autowired
    private UserDao userDao;

    @Autowired
    private UserAuthService userAuthService;

    public void addUser(User user){
        String phone = user.getPhone();
        //先判断输入的手机号有没有问题
        if(StringUtils.isNullOrEmpty(phone)){
            throw new ConditionException("Phone should not be null.");
        }
        User dbUser = this.getUserByPhone(phone);
        //判断手机号是否已经存在
        if(dbUser != null){
            throw new ConditionException("This phone is already registered.");
        }
        Date now = new Date();//通过时间生成盐值给用户密码加密
        String salt = String.valueOf(now.getTime());
        String password = user.getPassword();//被前端经过RSA加密才传过来的，所以先要解密
        String rawPassword; //前端传过来的密码经解密后的明文密码
        try {
            rawPassword = RSAUtil.decrypt(password); //解密前端传过来的密码
        }catch(Exception e){
            throw new ConditionException("Decryption failed.");
        }
        String md5Password = MD5Util.sign(rawPassword, salt, "UTF-8"); //对密码进行MD5加密
        user.setSalt(salt);
        user.setPassword(md5Password);
        user.setCreateTime(now);
        userDao.addUser(user);//设置好user信息后添加，和xml文件中方法内容的关系？
        //拿到id后对应添加用户信息
        UserInfo userInfo = new UserInfo();
        userInfo.setUserId(user.getId());
        //初次注册用户只需填写手机号，所以有数据为空，可以预设一些默认值
        userInfo.setNick(UserConstant.DEFAULT_NICK);
        userInfo.setBirth(UserConstant.DEFAULT_BIRTH);
        userInfo.setGender(UserConstant.GENDER_FEMALE);
        userInfo.setCreateTime(now);
        userDao.addUserInfo(userInfo);
        //添加用户默认权限角色
        userAuthService.addUserDefaultRole(user.getId());
    }

    //需与数据库交互的方法要在Dao中实现
    public User getUserByPhone(String phone){
        return userDao.getUserByPhone(phone);
    }

    //postman必须传入RSAUtil加密后的password？
    //添加user时不会添加userinfo数据？
    public String login(User user) throws Exception{
        String phone = user.getPhone() == null? "" : user.getPhone();
        String email = user.getEmail() == null? "" : user.getEmail();
        if(StringUtils.isNullOrEmpty(user.getPhone()) && StringUtils.isNullOrEmpty((user.getEmail()))){
            throw new ConditionException("Invalid input.");
        }
        User dbUser = userDao.getUserByPhoneOrEmail(phone, email);
        if(dbUser == null){
            throw new ConditionException("This user does not exist.");
        }
        String password = user.getPassword();
        String rawPassword;
        try {
            rawPassword = RSAUtil.decrypt(password); //解密前端传过来的密码
        }catch(Exception e){
            throw new ConditionException("Decryption failed.");
        }
        String salt = dbUser.getSalt(); //注册时salt是生成的，这里需要获取
        String md5Password = MD5Util.sign(rawPassword, salt, "UTF-8");
        if(!md5Password.equals(dbUser.getPassword())){
            throw new ConditionException("Wrong password.");
        }
        return TokenUtil.generateToken(dbUser.getId());
    }

    public User getUserInfo(Long userId){
        User user = userDao.getUserById(userId);
        UserInfo userInfo = userDao.getUserInfoByUserId(userId);
        user.setUserInfo(userInfo);
        return user;
    }

    //update的逻辑是什么？需要前端传入id？postman中应该传入什么参数测试？
    public void updateUsers(User user) throws Exception{
        Long id = user.getId();
        User dbUser = userDao.getUserById(id);
        if(dbUser == null){
            throw new ConditionException("User does not exist.");
        }
        if(!StringUtils.isNullOrEmpty(user.getPassword())){
            String rawPassword = RSAUtil.decrypt(user.getPassword());
            String md5Password = MD5Util.sign(rawPassword, dbUser.getSalt(), "UTF-8");
            user.setPassword(md5Password);
        }
        user.setUpdateTime(new Date());
        userDao.updateUsers(user);
    }

    public void updateUserInfos(UserInfo userInfo) {
        userInfo.setUpdateTime(new Date());
        userDao.updateUserInfos(userInfo);
    }

    public User getUserById(Long followingId) {
        return userDao.getUserById(followingId);
    }

    public List<UserInfo> getUserInfoByUserIds(Set<Long> userIdList) {
        return userDao.getUserInfoByUserIds(userIdList);
    }

    public PageResult<UserInfo> pageListUserInfos(JSONObject params) {
        //这些方法都是阿里包中带的方法，比map好用，所以这里不用map
        Integer no = params.getInteger("no");
        Integer size = params.getInteger("size");
        //计算起始页，这里表示起始页是当前页码减1再乘size，因为系统中一般是0开始编号而不是1
        params.put("start", (no-1)*size);
        //要注明请求多少条数据，与size保持一致即可
        params.put("limit", size);
        //第一步先判断分页查询的总数量，在dao中加入方法，根据输入的条件判断符合条件的用户信息有多少条
        Integer total = userDao.pageCountUserInfos(params);
        List<UserInfo> list = new ArrayList<>();
        //total不大于0的话，直接返回一个空列表
        if(total > 0){
            //查询具体数据的方法
            list = userDao.pageListUserInfos(params);
        }
        return new PageResult<>(total, list);
    }

    public Map<String, Object> loginForDts(User user) throws Exception{
        String phone = user.getPhone() == null? "" : user.getPhone();
        String email = user.getEmail() == null? "" : user.getEmail();
        if(StringUtils.isNullOrEmpty(user.getPhone()) && StringUtils.isNullOrEmpty((user.getEmail()))){
            throw new ConditionException("Invalid input.");
        }
        User dbUser = userDao.getUserByPhoneOrEmail(phone, email);
        if(dbUser == null){
            throw new ConditionException("This user does not exist.");
        }
        String password = user.getPassword();
        String rawPassword;
        try {
            rawPassword = RSAUtil.decrypt(password); //解密前端传过来的密码
        }catch(Exception e){
            throw new ConditionException("Decryption failed.");
        }
        String salt = dbUser.getSalt(); //注册时salt是生成的，这里需要获取
        String md5Password = MD5Util.sign(rawPassword, salt, "UTF-8");
        if(!md5Password.equals(dbUser.getPassword())){
            throw new ConditionException("Wrong password.");
        }
        //生成两个token:接入token与刷新token
        //前面到这里是生成接入token
        Long userId = dbUser.getId();
        String accessToken = TokenUtil.generateToken(userId);
        //生成刷新token
        String refreshToken = TokenUtil.generateRefreshToken(userId);
        //把refreshToken和userId一起保存到数据库中，方便后续退出登陆和延迟接入token有效期时，查找refresh token
        //如果token依然存在，说明仍在有效期内，可以刷新，否则需要告诉前端重新登陆
        userDao.deleteRefreshToken(refreshToken, userId);
        userDao.addRefreshToken(refreshToken, userId, new Date());
        Map<String, Object> result = new HashMap<>();
        result.put("accessToken", accessToken);
        result.put("refreshToken", refreshToken);
        return result;
    }

    public void logout(String refreshToken, Long userId) {
        userDao.deleteRefreshToken(refreshToken, userId);
    }

    public String refreshAccessToken(String refreshToken) throws Exception {
        RefreshTokenDetail refreshTokenDetail = userDao.getRefreshTokenDetail(refreshToken);
        if(refreshTokenDetail == null){
            throw new ConditionException("555", "Token expired.");
        }
        //验证refreshToken合法性，即使数据库中有refreshToken，如果过期了也无效，不能刷新accessToken
        TokenUtil.verifyRefreshToken(refreshToken);
        Long userId = refreshTokenDetail.getUserId();
        return TokenUtil.generateToken(userId);
    }

    public List<UserInfo> batchGetUserInfoByUserIds(Set<Long> userIdList) {
        return userDao.batchGetUserInfoByUserIds(userIdList);
    }
}
