package com.xw.bilibili.service;

import com.xw.bilibili.dao.UserFollowingDao;
import com.xw.bilibili.domain.FollowingGroup;
import com.xw.bilibili.domain.User;
import com.xw.bilibili.domain.UserFollowing;
import com.xw.bilibili.domain.UserInfo;
import com.xw.bilibili.domain.constant.UserConstant;
import com.xw.bilibili.domain.exception.ConditionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class UserFollowingService {

    @Autowired
    private UserFollowingDao userFollowingDao;

    @Autowired
    private FollowingGroupService followingGroupService;

    @Autowired
    private UserService userService;

    public void addUserFollowings(UserFollowing userFollowing){
        //第一次添加关注加入默认分组，之后在用户中心进行分组转移,或者新建分组后加入
        //所以要先看分组id有没有传入
        //第一步获取传入的分组id
        Long groupId = userFollowing.getGroupId();
        //如果没有传入分组id，添加到系统默认分组，对应数据表中的2类型
        if(groupId == null){
            //通过constant中存储的默认分组的id获取默认分组
            FollowingGroup followingGroup = followingGroupService.getByType(UserConstant.USER_FOLLOWING_GROUP_TYPE_DEFAULT);
            //把默认分组的主键id提取出来，设置给这个following，添加了默认分组
            userFollowing.setGroupId(followingGroup.getId());
        }else{
            //如果存在分组id，说明用户指定了分组，那就把id提取出来
            FollowingGroup followingGroup = followingGroupService.getById(groupId);
            if(followingGroup == null){
                throw new ConditionException("Group does not exist.");
            }
        }
        //下一步看用户关注的人是否存在，如果不存在就是垃圾信息了
        Long followingId = userFollowing.getFollowingId(); //查询关注对象的id
        //通过id获取用户信息，这里的getUserById原本值存在userDao中，service中需新设
        //service应与service进行交互，dao应只与自己对应的service交互，因此这里不能直接引用UserDao中的方法，不应引入UserDao，需要在service中单独添加方法
        //松耦合模式，只是service互相引用方法，不在意方法的具体变化
        User user = userService.getUserById(followingId);
        if(user == null){//关注的用户不存在
            throw new ConditionException("User does not exist.");
        }
        //关联关系更新时需要先删除再新增，就可以覆盖新增和更新两种操作
        userFollowingDao.deleteUserFollowing(userFollowing.getUserId(), followingId);
        userFollowing.setCreateTime(new Date());
        userFollowingDao.addUserFollowing(userFollowing);
    }

    //第一步：获取关注的用户列表
    //第二步：根据关注用户的id查询用户的基本信息
    //第三步：将关注用户按关注分组进行分类
    public List<FollowingGroup> getUserFollowings(Long userId){
        //用Dao中的方法根据userId字段找到它相关的所有字段
        List<UserFollowing> list = userFollowingDao.getUserFollowings(userId);
        //抽出其中的followingId，这里是lamda语句，stream()方法等于for each，循环list中的元素，map里面指的是最终形成set所遵循的方法
        //这里就是指根据UserFollowing中的getFollowingId方法抽出followingId，然后collect方法再将抽取出的信息形成set
        Set<Long> followingIdSet = list.stream().map(UserFollowing::getFollowingId).collect(Collectors.toSet());
        List<UserInfo> userInfoList = new ArrayList<>();
        //只有followingIdSet中有值时才会继续执行操作
        if(followingIdSet.size() > 0){
            //得到关注的用户的基本信息列表
            userInfoList = userService.getUserInfoByUserIds(followingIdSet);
        }
        //如果id匹配，就把用户信息添加进userfollowing中，为了接收这一信息，在userFollowing中新设一个成员变量和getset方法
        for(UserFollowing userFollowing:list){
            for(UserInfo userInfo : userInfoList){
                if(userFollowing.getFollowingId().equals(userInfo.getUserId())){
                    userFollowing.setUserInfo(userInfo);
                }
            }
        }
        //分组
        //先获取这个用户id对应的分组有哪些，包括用户自定义的分组和系统默认的分组0、1、2
        List<FollowingGroup> groupList = followingGroupService.getByUserId(userId);
        //添加一个全部关注分组，不需要存在数据库中，只是把所有的关注全部拼在一起
        FollowingGroup allGroup = new FollowingGroup();
        allGroup.setName(UserConstant.USER_FOLLOWING_GROUP_ALL_NAME);
        allGroup.setFollowingUserInfoList(userInfoList);
        //返回的数据
        List<FollowingGroup> result = new ArrayList<>();
        result.add(allGroup);
        for(FollowingGroup group:groupList){
            List<UserInfo> infoList = new ArrayList<>();
            for(UserFollowing userFollowing:list){
                //如果group表的id与following表的groupid相同，就可以匹配起来
                if(group.getId().equals(userFollowing.getGroupId())){
                    //匹配时把userInfo放到这个group对应的infoList中
                    infoList.add(userFollowing.getUserInfo());
                }
            }
            //循环完这个list对应的关注用户之后，就可以加入这个group中，然后把这个group信息加入到result中
            group.setFollowingUserInfoList(infoList);
            result.add(group);
        }
        return result;
    }

    //第一步：获取当前用户的粉丝列表
    //第二步：根据粉丝的用户id查询基本信息
    //第三步：查询当前用户是否已经关注该粉丝
    public List<UserFollowing> getUserFans(Long userId){
        //获取粉丝列表
        List<UserFollowing> fanList = userFollowingDao.getUserFans(userId);
        //同样的方法把粉丝的id抽取出来
        Set<Long> fanIdSet = fanList.stream().map(UserFollowing::getUserId).collect(Collectors.toSet());
        List<UserInfo> userInfoList = new ArrayList<>();
        if(fanIdSet.size() > 0){
            userInfoList = userService.getUserInfoByUserIds(fanIdSet);
        }
        //获取当前用户关注的所有用户，与它的粉丝列表对比，看是否互关
        List<UserFollowing> followingList = userFollowingDao.getUserFollowings(userId);
        for(UserFollowing fan : fanList){
            for(UserInfo userInfo:userInfoList){
                //在验证互关同时将对应的userInfo赋值给各个粉丝
                if(fan.getUserId().equals(userInfo.getUserId())){
                    //把followed字段先设为默认值false
                    userInfo.setFollowed(false);
                    fan.setUserInfo(userInfo);
                }
            }
            //验证互关，如果互关，followed改为true
            for(UserFollowing following : followingList){
                if(following.getFollowingId().equals(fan.getUserId())){
                    fan.getUserInfo().setFollowed(true);
                }
            }
        }
        return fanList;
    }

    public Long addUserFollowingGroups(FollowingGroup followingGroup) {
        followingGroup.setCreateTime(new Date());
        //设置分组类型，这里因为是用户自建分组才会用到，所以一定是自定义分组，不是0、1、2三个系统自带分组
        //在UserConstant中新增常量作为用户自建分组的类别
        followingGroup.setType(UserConstant.USER_FOLLOWING_GROUP_TYPE_USER);
        followingGroupService.addFollowingGroups(followingGroup);
        //xml文件中注明了返回主键id，因此这里可以直接getId
        return followingGroup.getId();
    }


    public List<FollowingGroup> getUserFollowingGroups(Long userId) {
        return followingGroupService.getUserFollowingGroups(userId);
    }

    public List<UserInfo> checkFollowingStatus(List<UserInfo> userInfoList, Long userId) {
        //查询到用户关注的用户信息列表
        List<UserFollowing> userFollowingList = userFollowingDao.getUserFollowings(userId);

        for(UserInfo userInfo : userInfoList){
            //先把传入的查询到的列表中的用户关注属性全部设为默认值false
            userInfo.setFollowed(false);
            for(UserFollowing userFollowing : userFollowingList){
                //如果查询到的列表和用户关注列表信息可以对的上，关注情况就可以设置为true了
                if(userFollowing.getFollowingId().equals(userInfo.getUserId())){
                    userInfo.setFollowed(true);
                }
            }
        }
        return userInfoList;
    }
}
