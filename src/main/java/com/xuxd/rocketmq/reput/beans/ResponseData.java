package com.xuxd.rocketmq.reput.beans;

import com.google.gson.Gson;
import com.xuxd.rocketmq.reput.enumc.ResponseCode;
import java.util.List;
import lombok.Data;

/**
 * rocketmq-reput.
 *
 * @author xuxd
 * @date 2021-07-02 20:00:50
 **/
@Data
public class ResponseData<T> {

    private int code;

    private String message;

    private T data;

    private List<T> dataList;

    private static final Gson GSON  = new Gson();

    public ResponseData<T> success() {
        this.code = ResponseCode.SUCCESS.getCode();
        return this;
    }

    public ResponseData success(String message) {
        this.message = message;
        this.code = ResponseCode.SUCCESS.getCode();
        return this;
    }

    public ResponseData fail(int code, String message) {
        this.message = message;
        this.code = code;
        return this;
    }

    public ResponseData<T> data(T t) {
        this.data = t;
        return this;
    }

    public ResponseData<T> dataList(List<T> list) {
        this.dataList = list;
        return this;
    }

    public static ResponseData create() {
        return new ResponseData();
    }

    public static <T> ResponseData create(Class<T> cls) {
        return new ResponseData<T>();
    }

    public static ResponseData parse(String data) {
        return GSON.fromJson(data, ResponseData.class);
    }
}
