package com.xuxd.rocketmq.reput.controller;

import com.xuxd.rocketmq.reput.beans.ResponseData;
import com.xuxd.rocketmq.reput.server.CommitLogUploadService;
import java.util.Enumeration;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * rocketmq-reput. Multipart controller can support file upload in order to receive bakup file.
 *
 * @author xuxd
 * @date 2021-07-02 14:41:58
 **/
@RestController
@RequestMapping
public class MultipartController {

    @Autowired
    private CommitLogUploadService commitLogUploadService;

    @PostMapping("/upload")
    public Object upload(@RequestParam("file") MultipartFile file, HttpServletRequest request) {

        Enumeration<String> names = request.getHeaderNames();
//        while (names.hasMoreElements()) {
//            System.out.println(names.nextElement());
//        }
        return ResponseData.create().success();
    }

    @GetMapping("/pre/upload")
    public Object preUpload(@RequestParam String fileName) {
        return commitLogUploadService.preUpload(fileName);
    }
}
