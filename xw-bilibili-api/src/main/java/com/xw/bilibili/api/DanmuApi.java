package com.xw.bilibili.api;

import com.xw.bilibili.domain.Danmu;
import com.xw.bilibili.domain.JsonResponse;
import com.xw.bilibili.service.DanmuService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.xw.bilibili.api.support.UserSupport;

import java.text.ParseException;
import java.util.List;

@RestController
public class DanmuApi {

    @Autowired
    private DanmuService danmuService;

    @Autowired
    private UserSupport userSupport;

    //startTime和endTime用于弹幕的时间段筛选，在登陆模式下，用户可以进行筛选，游客模式不能进行筛选
    @GetMapping("/danmus")
    public JsonResponse<List<Danmu>> getDanmus (@RequestParam Long videoId,
                                                String startTime,
                                                String endTime) throws ParseException {
        List<Danmu> list;
        try{
            //判断当前是游客模式还是用户登陆模式
            userSupport.getCurrentUserId();
            //用户登陆模式允许时间段筛选
            list = danmuService.getDanmus(videoId, startTime, endTime);
        }catch(Exception ignored){
            //游客模式不允许进行时间段筛选
            list = danmuService.getDanmus(videoId, null, null);
        }
        return new JsonResponse<>(list);
    }




}
