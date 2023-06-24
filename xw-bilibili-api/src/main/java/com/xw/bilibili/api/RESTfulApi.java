package com.xw.bilibili.api;

import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@RestController
public class RESTfulApi {

    private final Map<Integer, Map<String, Object>> dataMap;

    public RESTfulApi(){
        dataMap = new HashMap<>();
        for(int i = 1; i < 3; i++){
            Map<String, Object> data = new HashMap<>();
            data.put("id", i);
            data.put("name", "name" + i);
            dataMap.put(i, data);
        }
    }

    @GetMapping("/objects/{id}")
    public Map<String, Object> getData(@PathVariable Integer id){
        return dataMap.get(id);
    }

    @DeleteMapping("/objects/{id}")
    public String deleteData(@PathVariable Integer id){
        dataMap.remove(id);
        return "delete success";
    }

    @PostMapping("/objects")
    public String postData(@RequestBody Map<String, Object> data){
        Integer[] idArray = dataMap.keySet().toArray(new Integer[0]);//获取key的集合
        Arrays.sort(idArray);//升序后可获取当前的最大id，我们后续添加的id只要在这个基础上加1即可
        int nextId = idArray[idArray.length - 1] + 1;
        dataMap.put(nextId, data);
        return "post success";
    }

    @PutMapping("/objects")
    public String putData(@RequestBody Map<String, Object> data){//put可新增可更新，所以要先确认操作
        Integer id = Integer.valueOf(String.valueOf(data.get("id")));//先获取data的id
        Map<String, Object> containedData = dataMap.get(id);//看原本dataMap中是否有这个id
        if(containedData == null){//如果没有这条数据，做新增操作，具体操作与post一样
            Integer[] idArray = dataMap.keySet().toArray(new Integer[0]);//获取key的集合
            Arrays.sort(idArray);//升序后可获取当前的最大id，我们后续添加的id只要在这个基础上加1即可
            int nextId = idArray[idArray.length - 1] + 1;
            dataMap.put(nextId, data);
        }else{//有数据的话就是更新
            dataMap.put(id, data);//直接替换id原本对应的数据
        }
        return "put success";

    }
}
