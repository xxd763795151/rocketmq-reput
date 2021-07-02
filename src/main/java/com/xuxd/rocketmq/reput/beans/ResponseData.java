package com.xuxd.rocketmq.reput.beans;

import com.google.gson.Gson;
import com.xuxd.rocketmq.reput.enumc.ResponseCode;
import lombok.Data;

/**
 * rocketmq-reput.
 *
 * @author xuxd
 * @date 2021-07-02 20:00:50
 **/
@Data
public class ResponseData {

    private int code;

    private String message;

    private static final Gson GSON  = new Gson();

    public ResponseData success() {
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

    public static ResponseData create() {
        return new ResponseData();
    }

    public static ResponseData parse(String data) {
        return GSON.fromJson(data, ResponseData.class);
    }
}
