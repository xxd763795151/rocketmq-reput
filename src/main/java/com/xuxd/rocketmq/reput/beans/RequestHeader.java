package com.xuxd.rocketmq.reput.beans;

/**
 * rocketmq-reput.
 *
 * @author xuxd
 * @date 2021-07-03 11:14:24
 **/
public interface RequestHeader {

    String FILE_NAME = "X-filename";
    String MD5 = "X-md5";
    String FILE_SIZE = "X-file-size";
}
