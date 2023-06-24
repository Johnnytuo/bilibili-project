package com.xw.bilibili.service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.xw.bilibili.service.util.RocketMQUtil;
import org.apache.rocketmq.common.message.Message;
import com.xw.bilibili.dao.UserMomentsDao;
import com.xw.bilibili.domain.UserMoment;
import com.xw.bilibili.domain.constant.UserMomentsConstant;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

@Service
public class UserMomentsService {

    @Autowired
    private UserMomentsDao userMomentsDao;

    //引入ApplicationContext可以获取上下文，包括bean，就可以通过这个获取在mq的配置类中新增的producer和consumer
    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    public void addUserMoments(UserMoment userMoment) throws Exception{
        userMoment.setCreateTime(new Date());
        userMomentsDao.addUserMoments(userMoment);
        //需要向mq发送消息，需要producer和message两个参数
        //通过applicationContext获取RocketMQ的producer
        DefaultMQProducer producer = (DefaultMQProducer) applicationContext.getBean("momentsProducer");
        //message中需要传入主题和参数，传入新建的动态相关的主题（在常量中新设的），把传入的userMoment作为参数一起发送到mq，需要转成byte数组发送
        Message msg = new Message(UserMomentsConstant.TOPIC_MOMENTS, JSONObject.toJSONString(userMoment).getBytes(StandardCharsets.UTF_8));
        //把新增的userMoment发送到mq中去，告诉mq用户发布了动态
        RocketMQUtil.syncSendMsg(producer, msg);
    }

    public List<UserMoment> getUserSubscribedMoments(Long userId) {
        //数据存在redis中，需要从redis中获取
        //key与mq中的key保持一致
        String key = "subscribed-" + userId;
        String listStr = redisTemplate.opsForValue().get(key);
        return JSONArray.parseArray(listStr, UserMoment.class);
    }
}
