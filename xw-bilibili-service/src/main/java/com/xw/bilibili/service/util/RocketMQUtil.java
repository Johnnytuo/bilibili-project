package com.xw.bilibili.service.util;

import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.CountDownLatch2;
import org.apache.rocketmq.common.message.Message;

import java.util.concurrent.TimeUnit;

public class RocketMQUtil {

    //mq中发送信息有同步发送和异步发送两种方式
    //返回值也区分为有返回状态提醒和无返回状态提醒
    //这里先写同步发送
    public static void syncSendMsg(DefaultMQProducer producer, Message msg) throws Exception{
        SendResult result = producer.send(msg);
        System.out.println(result);
    }

    public static void asyncSendMsg(DefaultMQProducer producer, Message msg) throws Exception{
        //这里假设对消息进行2次发送
        //添加计数器，发送两次
        int messageCount = 2;
        //引入倒计时器
        CountDownLatch2 countDownLatch = new CountDownLatch2(messageCount);
        //循环发送2次
        for(int i = 0; i < messageCount; i++){
            //添加SendCallback方法，发送成功的回调或发送失败的提醒
            producer.send(msg, new SendCallback() {
                @Override
                public void onSuccess(SendResult sendResult) {
                    //发送成功计数器减1
                    countDownLatch.countDown();
                    //打印成功的消息
                    System.out.println(sendResult.getMsgId());
                }

                @Override
                public void onException(Throwable e) {
                    //失败也要计数器减1
                    countDownLatch.countDown();
                    //提示语提醒异常
                    System.out.println("Exception in sending message." + e);
                    e.printStackTrace();
                }
            });
        }
        //让计数器停留5秒
        countDownLatch.await(5, TimeUnit.SECONDS);
    }
}
