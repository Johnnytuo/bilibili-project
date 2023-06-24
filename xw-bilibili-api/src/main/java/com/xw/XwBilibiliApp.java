package com.xw;

import com.xw.bilibili.service.websocket.WebSocketService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableTransactionManagement
@EnableAsync
@EnableScheduling
public class XwBilibiliApp {

    public static void main(String[] args){
        ApplicationContext app = SpringApplication.run(XwBilibiliApp.class, args);
        WebSocketService.setApplicationContext(app);
    }
}

