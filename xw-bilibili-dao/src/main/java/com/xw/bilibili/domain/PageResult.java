package com.xw.bilibili.domain;

import java.util.List;

public class PageResult<T> {

    //查询数据的总数
    private Integer total;

    //分页查询出的当前页列表
    private List<T> list;

    public PageResult(Integer total, List list){
        this.total = total;
        this.list = list;
    }

    public Integer getTotal() {
        return total;
    }

    public void setTotal(Integer total) {
        this.total = total;
    }

    public List<T> getList() {
        return list;
    }

    public void setList(List<T> list) {
        this.list = list;
    }
}
