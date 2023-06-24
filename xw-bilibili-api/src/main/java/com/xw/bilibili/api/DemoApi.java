package com.xw.bilibili.api;

import com.xw.bilibili.domain.JsonResponse;
import com.xw.bilibili.domain.Video;
import com.xw.bilibili.service.DemoService;
import com.xw.bilibili.service.ElasticSearchService;
import com.xw.bilibili.service.util.FastDFSUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@Slf4j
public class DemoApi {
    @Autowired
    private DemoService demoService;
    @Autowired
    private FastDFSUtil fastDFSUtil;

    @Autowired
    private ElasticSearchService elasticSearchService;

    @GetMapping("/query")
    public Long query(Long id){
        return demoService.query(id);
    }

    @GetMapping("/es-videos")
    public JsonResponse<Video> getEsVideos(@RequestParam String keyword) {
       Video video = elasticSearchService.getVideos(keyword);
       return new JsonResponse<>(video);
    }

}
