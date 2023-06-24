package com.xw.bilibili.service.websocket;

import com.alibaba.fastjson.JSONObject;
import com.sun.xml.internal.bind.api.impl.NameConverter;
import com.xw.bilibili.domain.Danmu;
import com.xw.bilibili.domain.constant.UserMomentsConstant;
import com.xw.bilibili.service.DanmuService;
import com.xw.bilibili.service.util.RocketMQUtil;
import com.xw.bilibili.service.util.TokenUtil;
import io.netty.util.internal.StringUtil;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@ServerEndpoint("/imserver/{token}")
public class WebSocketService {
    //进行websocket服务需要创建的变量

    //日志记录
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    //当前长链接的人数，有多少客户端通过websocket连接到了服务端
    //初始值是0，因为没有人连接
    //atomicInteger是java提供的原子性操作的类，为了操作数据的时候线程安全
    private static final AtomicInteger ONLINE_COUNT = new AtomicInteger(0);

    //能够保证线程安全的存储数据的类,保存每个客户端对应的websocketservice，当客户端进来之后，高效获取websocket服务
    //为什么每个客户端都需要一个单独的websocketservice
    // springboot中依赖注入默认单例模式，指只注入一个bean，所有实体类可以通用这个bean
    //但是websocketservice是多例模式，需要一个map类存储每个客户端生成的websocketservice，多例模式下不能用@autowired引入依赖
    public static final ConcurrentHashMap<String, WebSocketService> WEBSOCKET_MAP = new ConcurrentHashMap<>();

    //服务端和客户端进行服务的会话，用于进行长连接通信，有一个客户端进来了，就会保存跟这个客户端关联的session，下一次这个客户端进行信息传输的时候
    // 就可以用这个Session进行通信
    private Session session;

    private String sessionId;

    //通过前端把token发送给服务端，然后服务端通过解析token获取
    //在onopen方法中获取路径中的token
    private Long userId;

    private static ApplicationContext APPLICATION_CONTEXT;

    //这里传入的applicationContext就是启动类中的applicationContext
    public static void setApplicationContext(ApplicationContext applicationContext){
        WebSocketService.APPLICATION_CONTEXT = applicationContext;
    }


    //建立连接的方法,Websocket自动将客户端和服务端的会话建立起来，连接建立成功之后，传入的session就可以赋值给websocketservice中本地的这个变量
    //从而实现会话的保持和记录
    //OnOpen注解指连接成功的时候就调用这个标识相关的方法
    @OnOpen
    public void openConnection(Session session, @PathParam("token") String token) {
        //通过前端路径中传来的token获取userId，但是游客也可以查看视频，只不过获取不了userId
        try {
            this.userId = TokenUtil.verifyToken(token);
        }catch(Exception e) {}
        this.sessionId = session.getId();
        this.session = session;
        //如果websocketmap中已经存储过这个sessionId，就把原来存储的去掉，然后添加一个新的sessionId，并增加当前在线人数
        if(WEBSOCKET_MAP.contains(sessionId)){
            WEBSOCKET_MAP.remove(sessionId);
            WEBSOCKET_MAP.put(sessionId, this);
        }else{//没有的话直接添加，而且这里是客户端第一次连接服务端的情况，需要增加在线人数
            WEBSOCKET_MAP.put(sessionId, this);
            ONLINE_COUNT.getAndIncrement();
        }
        //写日志
        //ONLINE_COUNT.get()方法会返回一个int
        logger.info("User successfully connected: " + sessionId + ". Current online count is " + ONLINE_COUNT.get());
        try{
            this.sendMessage("0");//告诉前端连接成功了
        }catch(Exception e){
            logger.error("连接异常");
        }
    }

    //关闭链接的方法
    //OnClose注解表示关闭页面或者浏览器刷新
    //连接会自动断开，不需要更多操作
    @OnClose
    public void closeConnection(){
        //用websocketmap获取当前session
        if(WEBSOCKET_MAP.containsKey(sessionId)){
            //将session去除，然后在线人数减一
            WEBSOCKET_MAP.remove(sessionId);
            ONLINE_COUNT.getAndDecrement();
        }
        //记录日志
        logger.info("User exited: " + sessionId + ". Current online count is " + ONLINE_COUNT.get());
    }

    //前端发送消息来进行通信时使用onmessage注解
    @OnMessage
    public void onMessage(String message) {
        //打印日志
        logger.info("User-message: " + sessionId + ", content: " +message);
        if(!StringUtil.isNullOrEmpty(message)) {
            try{
                //服务端拿到某一个客户端的弹幕后，向所有连接的客户端群发消息
                for(Map.Entry<String, WebSocketService> entry : WEBSOCKET_MAP.entrySet()) {
                    //取得各个服务端的websocketservice
                    WebSocketService webSocketService = entry.getValue();
                    //获取弹幕的生产者
                    DefaultMQProducer danmuProducer = (DefaultMQProducer) APPLICATION_CONTEXT.getBean("danmuProducer");
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("message", message);
                    jsonObject.put("sessionId", webSocketService.getSessionId());
                    Message msg = new Message(UserMomentsConstant.TOPIC_DANMUS, jsonObject.toJSONString().getBytes(StandardCharsets.UTF_8));
                    RocketMQUtil.asyncSendMsg(danmuProducer, msg);
                }
                if(this.userId != null) {
                    //保存弹幕到数据库
                    //把message这个string转换成弹幕实体类
                    Danmu danmu = JSONObject.parseObject(message, Danmu.class);
                    danmu.setUserId(userId);
                    danmu.setCreateTime(new Date());
                    DanmuService danmuService = (DanmuService) APPLICATION_CONTEXT.getBean("danmuService");
                    danmuService.asyncAddDanmu(danmu);
                    //保存弹幕到redis
                    danmuService.addDanmusToRedis(danmu);
                }
            }catch(Exception e){
                logger.error("Danmu reception exception.");
                e.printStackTrace();
            }
        }
    }

    //发生错误后进行处理
    @OnError
    public void onError(Throwable error){


    }

    //用于实现定时任务的注解，或直接指定时间间隔，例如5秒
    @Scheduled(fixedRate=5000)
    private void noticeOnlineCount() throws IOException{
        //判断如果session开启，就构建一个JSON形式的字符串，包含当前在线人数和给前段的提示语（可有可无），然后发送
        for(Map.Entry<String, WebSocketService> entry : WEBSOCKET_MAP.entrySet()){
            WebSocketService webSocketService = entry.getValue();
            if(webSocketService.session.isOpen()){
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("onlineCount", ONLINE_COUNT.get());
                jsonObject.put("msg", "Current online count is " + ONLINE_COUNT.get());
                webSocketService.sendMessage(jsonObject.toJSONString());
            }
        }
        //TODO:区分视频发送在线人数
    }

    public void sendMessage(String message) throws IOException {
        this.session.getBasicRemote().sendText(message);
    }

    public Session getSession() {
        return session;
    }

    public String getSessionId() {
        return sessionId;
    }


}
