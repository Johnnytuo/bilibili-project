package com.xw.bilibili.service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.xw.bilibili.dao.DanmuDao;
import com.xw.bilibili.domain.Danmu;
import io.netty.util.internal.StringUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

@Service
public class DanmuService {

    private static final String DANMU_KEY = "dm-video-";

    @Autowired
    private DanmuDao danmuDao;

    //不在websocketService中直接获取redistemplate，只通过applicationcontext将danmuservice引入，统一在danmuService里面引入redistemplate
    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    public void addDanmu(Danmu danmu) {
        danmuDao.addDanmu(danmu);
    }

    @Async
    public void asyncAddDanmu(Danmu danmu) {
        danmuDao.addDanmu(danmu);
    }

    public List<Danmu> getDanmus(Map<String, Object> params) {
        return danmuDao.getDanmus(params);
    }

    public void addDanmusToRedis(Danmu danmu) {
        String key = "danmu-video-" + danmu.getVideoId();
        //获取value
        String value = redisTemplate.opsForValue().get(key);
        List<Danmu> list = new ArrayList<>();
        //如果value有值，直接添加到列表中，转换成列表
        if(!StringUtil.isNullOrEmpty(value)){
            list = JSONArray.parseArray(value, Danmu.class);
        }
        list.add(danmu);
        redisTemplate.opsForValue().set(key, JSONObject.toJSONString(list));
    }


    //查询策略是优先查redis中的弹幕数据
    //如果没有的话查询数据库，然后把查询的数据写入redis当中
    public List<Danmu> getDanmus(Long videoId, String startTime, String endTime) throws ParseException {
        //设一个与视频相关的唯一的redis key
        String key = DANMU_KEY + videoId;
        String value = redisTemplate.opsForValue().get(key);
        List<Danmu> list;
        //如果value是null或空，说明redis中没有数据，直接走到数据库中操作，如果有值，将value转换为list
        if(!StringUtil.isNullOrEmpty(value)) {
            list = JSONArray.parseArray(value, Danmu.class);
            //将redis中的数据进行时间段筛选
            //判断startTime和endTime是否有值，如果没有值就是游客模式，直接返回数据即可
            if(!StringUtil.isNullOrEmpty(startTime) && !StringUtil.isNullOrEmpty(endTime)) {
                SimpleDateFormat sdf =  new SimpleDateFormat("yyyy-mm-dd HH:mm:ss");
                Date startDate = sdf.parse(startTime);
                Date endDate = sdf.parse(endTime);
                List<Danmu> childList = new ArrayList<>();
                for(Danmu danmu : list ){
                    Date createTime = danmu.getCreateTime();
                    if(createTime.after(startDate) && createTime.before(endDate)){
                        childList.add(danmu);
                    }
                }
                list = childList;
            }
        }else{
            Map<String, Object> params = new HashMap<>();
            params.put("videoId", videoId);
            params.put("startTime", startTime);
            params.put("endTime", endTime);
            list = danmuDao.getDanmus(params);
            //保存弹幕到redis
            redisTemplate.opsForValue().set(key,JSONObject.toJSONString(list));
        }
        return list;
    }
}
