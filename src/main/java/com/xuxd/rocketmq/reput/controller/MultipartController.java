package com.xuxd.rocketmq.reput.controller;

import com.xuxd.rocketmq.reput.beans.RequestHeader;
import com.xuxd.rocketmq.reput.beans.ResponseData;
import com.xuxd.rocketmq.reput.config.ReputServerConfig;
import com.xuxd.rocketmq.reput.enumc.ResponseCode;
import com.xuxd.rocketmq.reput.server.CommitLogUploadService;
import com.xuxd.rocketmq.reput.utils.PathUtil;
import java.io.File;
import java.io.IOException;
import javax.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
@RestController
@RequestMapping
public class MultipartController {

    private final ReputServerConfig serverConfig;

    @Autowired
    public MultipartController(ReputServerConfig serverConfig) {
        this.serverConfig = serverConfig;
    }

    @Autowired
    private CommitLogUploadService commitLogUploadService;

    @PostMapping("/upload")
    public Object upload(@RequestParam("file") MultipartFile file, HttpServletRequest request) {

        String node = request.getHeader(RequestHeader.NODE);
        if (!serverConfig.getStore().containsKey(node)) {
            return ResponseData.create().fail(ResponseCode.FAILED.getCode(), "node no config");
        }
        String fileName = file.getOriginalFilename();
        File dest = new File(PathUtil.merge(PathUtil.getZipDir(serverConfig.getStore().get(node)), fileName));
        log.info("receive zip file: {}", dest.getAbsolutePath());
        try {
            file.transferTo(dest);
        } catch (IOException e) {
            log.error("save zip file error", e);
            return ResponseData.create().fail(ResponseCode.FAILED.getCode(), "save zip file error");
        }
        return commitLogUploadService.upload(dest, node, request.getHeader(RequestHeader.FILE_NAME), Long.valueOf(request.getHeader(RequestHeader.FILE_SIZE)), request.getHeader(RequestHeader.MD5));
    }

    @GetMapping("/pre/upload")
    public Object preUpload(@RequestParam String node, @RequestParam String fileName) {
        return commitLogUploadService.preUpload(node, fileName);
    }
}
