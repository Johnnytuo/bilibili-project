package com.xw.bilibili.service.handler;


import com.xw.bilibili.domain.JsonResponse;
import com.xw.bilibili.domain.exception.ConditionException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;

@ControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CommonGlobalExceptionHandler {

    @ExceptionHandler(value=Exception.class)
    @ResponseBody
    public JsonResponse<String> commonExceptionHanlder(HttpServletRequest request, Exception e){ //HttpServletRequest用于封装前端的请求
        String errorMsg = e.getMessage();
        if(e instanceof ConditionException){
            String errorCode = ((ConditionException)e).getCode();//ConditionException区别于RuntimeException的点就在于多了code
            return new JsonResponse<>(errorCode, errorMsg);
        }else{
            return new JsonResponse<>("500", errorMsg);
        }

    }

}
