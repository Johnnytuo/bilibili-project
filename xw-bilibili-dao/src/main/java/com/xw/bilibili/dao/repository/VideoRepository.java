package com.xw.bilibili.dao.repository;

import com.xw.bilibili.domain.Video;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

// ElasticsearchRepository的泛型第一个是repository面向的数据类型，第二个是主键id对应的数据类型
public interface VideoRepository extends ElasticsearchRepository<Video, Long> {

    //父类中本身没有这个方法，springdata提供了一套命名逻辑，当以这种逻辑命名查询方法时，springdata会把方法名做关键词拆解
    //这里的方法可以拆解出find by, title, like这几个关键词，find by提示spring data是一个查询的方法，其次会根据Video中的title字段
    // 进行查询，like指进行模糊查询，spring data官方提供了关键词供命名使用
    Video findByTitleLike(String keyword);
}
