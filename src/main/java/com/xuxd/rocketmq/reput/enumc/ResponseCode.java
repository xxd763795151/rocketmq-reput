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

    SUCCESS(0), FAILED(-9999), EXIST_FILE(1), REJECT_UPLOAD(2);

    ResponseCode(int code) {
        this.code = code;
    }

    @Setter
    @Getter
    private int code;
}
