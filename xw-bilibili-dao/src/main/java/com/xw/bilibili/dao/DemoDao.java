package com.xw.bilibili.dao;

import org.apache.ibatis.annotations.Mapper;

import java.util.Map;

@Mapper
public interface DemoDao {

    public Long query(Long id); //参数名与表中的项目名一致，在xml文件中写方法内容时使用这个名字就可以直接对应
}

