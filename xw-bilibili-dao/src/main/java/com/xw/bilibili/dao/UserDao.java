package com.xw.bilibili.dao;

import com.alibaba.fastjson.JSONObject;
import com.xw.bilibili.domain.RefreshTokenDetail;
import com.xw.bilibili.domain.User;
import com.xw.bilibili.domain.UserInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Mapper
public interface UserDao {

    User getUserByPhone(String phone);
    Integer addUser(User user);//数据库插入后会返回一个整数类型，含义为数据数量，因此这里可以用integer，可能在其他方法中用到
    Integer addUserInfo(UserInfo userInfo);

    User getUserById(Long id);

    UserInfo getUserInfoByUserId(Long userId);

    Integer updateUserInfos(UserInfo userInfo);

    Integer updateUsers(User user);

    User getUserByPhoneOrEmail(String phone, String email);

    List<UserInfo> getUserInfoByUserIds(Set<Long> userIdList);

    //Service中传入的是JSONObject，其实是实现了map类的，这里改为map，其实是为了xml文件中好写parameterType，否则需要引到阿里巴巴的包
    Integer pageCountUserInfos(Map<String, Object> params);

    List<UserInfo> pageListUserInfos(Map<String, Object> params);

    void deleteRefreshToken(@Param("refreshToken") String refreshToken, @Param("userId") Long userId);

    void addRefreshToken(@Param("refreshToken") String refreshToken, @Param("userId") Long userId,
                         @Param("createTime") Date createTime);

    RefreshTokenDetail getRefreshTokenDetail(String refreshToken);

    List<UserInfo> batchGetUserInfoByUserIds(Set<Long> userIdList);
}
