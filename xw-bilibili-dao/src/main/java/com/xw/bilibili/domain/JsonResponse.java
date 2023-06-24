package com.xw.bilibili.domain;

public class JsonResponse<T> {
    private String code;

    private String msg;

    private T data;

    public JsonResponse(String code, String msg){
        this.code = code;
        this.msg = msg;
    }
    public JsonResponse(T data){
        this.data = data;
        msg = "success";
        code = "0";
    }

    //不需要返回给前端但是请求成功的场景
    public static JsonResponse<String> success(){
        return new JsonResponse<>(null);
    }

    //需要给前端返回一些数据的情况
    public static JsonResponse<String> success(String data){
        return new JsonResponse<>(data);
    }

    public static JsonResponse<String> fail(){
        return new JsonResponse<>("1", "fail");
    }

    public static JsonResponse<String> fail(String code, String msg){
        return new JsonResponse<>(code, msg);
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
