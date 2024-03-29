package com.xuxd.rocketmq.reput.enumc;

import lombok.Getter;
import lombok.Setter;

/**
 * rocketmq-reput.
 *
 * @author xuxd
 * @date 2021-07-02 20:02:09
 **/
public enum ResponseCode {

    SUCCESS(0, "success"),
    FAILED(-9999, "failed"),
    EXIST_FILE(1, "already exist file"),
    OFFSET_TOO_SMALL(2, "offset too small"),
    REJECT_UPLOAD(3, "reject upload"),
    UNZIP_FAIL(4, "unzip failed"),
    FILE_NOT_MATCH(5, "file not match");

    ResponseCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    @Setter
    @Getter
    private int code;

    @Setter
    @Getter
    private String message;
}
