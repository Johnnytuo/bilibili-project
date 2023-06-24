package com.xw.bilibili.service;

import com.xw.bilibili.dao.repository.UserInfoRepository;
import com.xw.bilibili.dao.repository.VideoRepository;
import com.xw.bilibili.domain.UserInfo;
import com.xw.bilibili.domain.Video;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.text.Text;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.MultiMatchQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class ElasticSearchService {

    @Autowired
    private VideoRepository videoRepository;

    @Autowired
    private UserInfoRepository userInfoRepository;

    @Autowired
    private RestHighLevelClient restHighLevelClient;

    public void addUserInfo(UserInfo userInfo){
        userInfoRepository.save(userInfo);
    }

    //多类型的全文搜索+分页查询
    public List<Map<String, Object>> getContents(String keyword,
                                                 Integer pageNo,
                                                 Integer pageSize) throws IOException {
        //存放索引，用于创建searchrequest
        String[] indices = {"videos","user-infos"};
        SearchRequest searchRequest = new SearchRequest(indices);
        //与searchrequest搭配使用，写一些查询时候的配置
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        //从0开始分页
        sourceBuilder.from(pageNo - 1);
        //分页功能
        sourceBuilder.size(pageSize);
        //查询的配置，参数是关键词和查询使用的字段（包括要查询的所有类）
        MultiMatchQueryBuilder matchQueryBuilder = QueryBuilders.multiMatchQuery(keyword, "title", "nick", "description");
        //用sourcebuilder存储查询配置
        sourceBuilder.query(matchQueryBuilder);
        searchRequest.source(sourceBuilder);
        //给查询设一个超时，如果超过一定时间没有响应直接终止请求
        sourceBuilder.timeout(new TimeValue(60, TimeUnit.SECONDS));
        //高亮显示
        //数组记录要高亮显示的字段
        String[] array = {"title","nick", "description"};
        HighlightBuilder highLightBuilder = new HighlightBuilder();
        for(String key : array){
            //把要高亮的字段指定成字段名
            highLightBuilder.fields().add(new HighlightBuilder.Field(key));
        }
        //如果要设置多个字段高亮,需要把以下的值设为false
        highLightBuilder.requireFieldMatch(false);
        //设置高亮形式
        highLightBuilder.preTags("<span style=\"color:red\">");
        highLightBuilder.postTags("</span>");
        sourceBuilder.highlighter(highLightBuilder);
        //执行搜索
        SearchResponse searchResponse = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);
        List<Map<String, Object>> arrayList = new ArrayList<>();
        //遍历匹配到的条目
        for(SearchHit hit : searchResponse.getHits()){
            //处理高亮字段
            Map<String, HighlightField> highlightBuilderFields = hit.getHighlightFields();
            //获取查询内容
            Map<String, Object> sourceMap = hit.getSourceAsMap();
            //遍历之前存储的要高亮的关键字
            for(String key : array) {
                HighlightField field = highlightBuilderFields.get(key);
                //首先判断有没有，否则会报空指针异常
                if(field != null){
                    //查询到的可能是多条内容,所以用text数组获取
                    Text[] fragments = field.fragments();
                    String str = Arrays.toString(fragments);
                    //去掉头尾的中括号
                    str = str.substring(1, str.length() - 1);
                    sourceMap.put(key, str);
                }
            }
            arrayList.add(sourceMap);
        }
        return arrayList;
    }
    
    public void addVideo(Video video){
        videoRepository.save(video);
    }

    public Video getVideos(String keyword) {
        //videoRepository中没有单独查询的方法
        Video video = videoRepository.findByTitleLike(keyword);
        return video;
    }

    public void deleteAllVideos(){
        videoRepository.deleteAll();
    }
}
