package com.xuxd.rocketmq.reput.controller;

import com.xuxd.rocketmq.reput.beans.ResponseData;
import com.xuxd.rocketmq.reput.enumc.ResponseCode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * rocketmq-reput. Multipart controller can support file upload in order to receive bakup file.
 *
 * @author xuxd
 * @date 2021-07-02 14:41:58
 **/
@RestController
@RequestMapping
public class MultipartController {

    @PostMapping("/upload")
    public Object upload() {
        return "success";
    }

    @GetMapping("/pre/upload")
    public Object preUpload(@RequestParam String fileName) {
        return ResponseData.create().fail(ResponseCode.EXIST_FILE.getCode(), "file exist: " + fileName);
    }
}
