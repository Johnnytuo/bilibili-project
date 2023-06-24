package com.xw.bilibili.service.config;


import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.xw.bilibili.domain.UserFollowing;
import com.xw.bilibili.domain.UserMoment;
import com.xw.bilibili.domain.constant.UserMomentsConstant;
import com.xw.bilibili.service.UserFollowingService;
import com.xw.bilibili.service.websocket.WebSocketService;
import io.netty.util.internal.StringUtil;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Configuration
public class RocketMQConfig {

    //配置broker和name server
    @Value("${rocketmq.name.server.address}")
    private String nameServerAddr;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private UserFollowingService userFollowingService;

    //增加消息的生产者和消息的消费者
    //创建与用户动态相关的生产者示例
    @Bean("momentsProducer")
    public DefaultMQProducer momentsProducer() throws Exception{
        //新建一个producer，后面括号中是producer的分组（这里是动态相关），新设的常量类中引入过来
        DefaultMQProducer producer = new DefaultMQProducer(UserMomentsConstant.GROUP_MOMENTS);
        //给producer设置nameServer的地址
        producer.setNamesrvAddr(nameServerAddr);
        //设置完producer后进行启动
        producer.start();
        //返回创建好的实例
        return producer;
    }

    //创建消费者实例，这里采取push推送方式
    @Bean("momentsConsumer")
    public DefaultMQPushConsumer momentsConsumer() throws Exception{
        //新建一个consumer实例，分组与producer一样
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(UserMomentsConstant.GROUP_MOMENTS);
        //给consumer设置nameServer地址
        consumer.setNamesrvAddr(nameServerAddr);
        //订阅发布模式，消费者需要订阅生产者，所以要进行订阅操作，新增常量定义订阅内容，第一个参数是主题，第二个参数是二级主题，*指主题下的所有内容
        consumer.subscribe(UserMomentsConstant.TOPIC_MOMENTS, "*");
        //给消费者添加监听器，当producer发布信息时，mq会推送给消费者，这时需要监听器抓取消息，并对消息进行下一步操作
        consumer.registerMessageListener(new MessageListenerConcurrently() {
            //需要override方法，进行处理结果的返回，参数是消息和处理的上下文
            @Override
            public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs, ConsumeConcurrentlyContext context) {
                // UserMomentsApi中用户上传动态后，会传到mq，这里会对producer传到mq的消息进行处理
                // UserMomentsApi中每次是上传一条信息，因此这里的msgs只有一个元素
                // 取出一条信息，如果信息为null，直接返回一个处理成功的提示，如果不为null，进行其他处理
                Message msg = msgs.get(0);
                if(msg == null){
                    return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
                }
                //如果msg不为null，要取出其中的userMoment实体类
                //msg.getBody()取出byte数组，这里直接转换成字符串
                String bodyStr = new String(msg.getBody());
                //再取出实体类，用JSONObject中自带的方法将字符串转换为usermoment实体类,参数中要传入想转换为的实体类的类型
                UserMoment userMoment = JSONObject.toJavaObject(JSONObject.parseObject(bodyStr), UserMoment.class);
                //获取userId用于获取所有订阅了该user的用户的id
                Long userId = userMoment.getUserId();
                //引入userFollowingService，用其中的getUserFans方法获取粉丝列表
                List<UserFollowing> fanList = userFollowingService.getUserFans(userId);
                //遍历粉丝列表，向他们发送推送，方法是发送到redis，用户再通过redis查询
                for(UserFollowing fan : fanList){
                    //加一个redis的key
                    String key = "subscribed-" + fan.getUserId();
                    //判断redis中有没有key对应的value
                    //用redisTemplate中的方法，opsForValue指要对RedisTemplate中的数据进行一些处理，后面接的就是具体的处理
                    String subscribedListStr = redisTemplate.opsForValue().get(key);
                    //把字符串转换为列表
                    List<UserMoment> subscribedList;
                    //如果字符串为空，需要新建一个空列表
                    if(StringUtil.isNullOrEmpty(subscribedListStr)){
                        subscribedList = new ArrayList<>();
                    }else{
                        //如果字符串不为空，转换为列表，用fastjason中的JSONArray自带的方法，参数中需要传入想转换为的类型
                        subscribedList = JSONArray.parseArray(subscribedListStr, UserMoment.class);
                    }
                    //把新获取的动态添加进去，这样用户就获取到了所订阅用户的信息
                    subscribedList.add(userMoment);
                    redisTemplate.opsForValue().set(key, JSONObject.toJSONString(subscribedList));
                }

                //返回消息处理成功的提示
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            }
        });
        //设置完成后启动
        consumer.start();
        return consumer;

    }

    //创建danmu生产者
    @Bean("danmuProducer")
    public DefaultMQProducer danmusProducer() throws Exception{
        //新建一个producer，后面括号中是producer的分组（这里是动态相关），新设的常量类中引入过来
        DefaultMQProducer producer = new DefaultMQProducer(UserMomentsConstant.GROUP_DANMUS);
        //给producer设置nameServer的地址
        producer.setNamesrvAddr(nameServerAddr);
        //设置完producer后进行启动
        producer.start();
        //返回创建好的实例
        return producer;
    }

    //创建danmu消费者
    @Bean("danmuConsumer")
    public DefaultMQPushConsumer danmusConsumer() throws Exception{
        //新建一个consumer实例，分组与producer一样
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(UserMomentsConstant.GROUP_DANMUS);
        //给consumer设置nameServer地址
        consumer.setNamesrvAddr(nameServerAddr);
        //订阅发布模式，消费者需要订阅生产者，所以要进行订阅操作，新增常量定义订阅内容，第一个参数是主题，第二个参数是二级主题，*指主题下的所有内容
        consumer.subscribe(UserMomentsConstant.TOPIC_DANMUS, "*");
        //给消费者添加监听器，当producer发布信息时，mq会推送给消费者，这时需要监听器抓取消息，并对消息进行下一步操作
        consumer.registerMessageListener(new MessageListenerConcurrently() {
            //需要override方法，进行处理结果的返回，参数是消息和处理的上下文
            @Override
            public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs, ConsumeConcurrentlyContext context) {
                //获取消息
                Message msg = msgs.get(0);
                if(msg == null){
                    return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
                }
                String bodyStr = new String(msg.getBody());
                JSONObject jsonObject = JSONObject.parseObject(bodyStr);
                //获取生产者传输的会话id和前端的弹幕消息
                String sessionId = jsonObject.getString("sessionId");
                String message = jsonObject.getString("message");
                WebSocketService webSocketService = WebSocketService.WEBSOCKET_MAP.get(sessionId);
                if(webSocketService.getSession().isOpen()){
                    try{
                        webSocketService.sendMessage(message);
                    }catch(IOException e) {
                        e.printStackTrace();
                    }
                }
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            }
        });
        //设置完成后启动
        consumer.start();
        return consumer;
    }

    // TODO:MQ削峰操作的producer和consumer

}
