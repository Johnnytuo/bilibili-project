package com.xw.bilibili.service.config;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.alibaba.fastjson.support.config.FastJsonConfig;
import com.alibaba.fastjson.support.spring.FastJsonHttpMessageConverter;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class JsonHttpMessageConverterConfig {

    public static void main(String[] args){
        List<Object> list = new ArrayList<>();
        Object o = new Object();
        list.add(o);
        list.add(o);
        System.out.println(list.size());
        System.out.println(JSONObject.toJSONString(list));//把list类转换为符合Json格式的字符串
        System.out.println(JSONObject.toJSONString(list, SerializerFeature.DisableCircularReferenceDetect));
    }

    @Bean
    @Primary
    public HttpMessageConverters fastJsonHttpMessageConverter(){
        FastJsonHttpMessageConverter fastConverter = new FastJsonHttpMessageConverter();
        FastJsonConfig fastJsonConfig = new FastJsonConfig();
        fastJsonConfig.setDateFormat("yyyy-MM-dd HH:mm:ss"); //配置返回数据的时间格式
        fastJsonConfig.setSerializerFeatures(
                SerializerFeature.PrettyFormat, //Json数据需要一个格式化的输出
                SerializerFeature.WriteNullStringAsEmpty, //如果输出的Json数据中有的字段是null，正常情况下系统会直接去掉这个字段，这里将这样的字段转换为空字符串
                SerializerFeature.WriteNullListAsEmpty, //没有数据的列表转为空字符串
                SerializerFeature.WriteMapNullValue,
                SerializerFeature.MapSortField, //把map相关字段进行排序，默认升序
                SerializerFeature.DisableCircularReferenceDetect //禁用循环引用，
        );
        fastConverter.setFastJsonConfig(fastJsonConfig);
        return new HttpMessageConverters(fastConverter);
    }
}
