package com.xuxd.rocketmq.reput.server;

import com.xuxd.rocketmq.reput.beans.ResponseData;
import com.xuxd.rocketmq.reput.config.ReputConfig;
import com.xuxd.rocketmq.reput.config.ReputServerConfig;
import com.xuxd.rocketmq.reput.enumc.ResponseCode;
import com.xuxd.rocketmq.reput.utils.ArchiveUtil;
import com.xuxd.rocketmq.reput.utils.FileUtil;
import com.xuxd.rocketmq.reput.utils.MD5Util;
import com.xuxd.rocketmq.reput.utils.PathUtil;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * rocketmq-reput.
 *
 * @author xuxd
 * @date 2021-07-02 20:58:38
 **/
@Slf4j
@Component
public class CommitLogUploadService {

    private final ReputConfig reputConfig;

    private final ReputServerConfig serverConfig;

    @Autowired
    public CommitLogUploadService(final ReputConfig reputConfig, final ReputServerConfig serverConfig) {
        this.reputConfig = reputConfig;
        this.serverConfig = serverConfig;
    }

    public ResponseData preUpload(String node, String fileName) {

        String nodePath = serverConfig.getStore().get(node);
        File commitLogDir = new File(PathUtil.getCommitLogDir(nodePath));
        boolean exist = false;
        List<File> fileList = Arrays.asList(commitLogDir.listFiles());
        for (File f : fileList) {
            if (f.getName().equals(fileName)) {
                exist = true;
                break;
            }
        }
        if (exist) {
            return ResponseData.create().fail(ResponseCode.EXIST_FILE.getCode(), "already exist: " + fileName);
        }
        log.info("pre upload check, file {} can upload.", fileName);
        return ResponseData.create().success();
    }

    public ResponseData upload(File zip, String node, String fileName, long fileSize, String md5) {
        File commitlog = null;
        synchronized (serverConfig.getStore().get(node)) {
            File tmpDir = ArchiveUtil.unzip(zip);
            commitlog = FileUtil.findFirstLeaf(tmpDir);
            String logPath = PathUtil.merge(zip.getParent(), fileName);
            boolean success = FileUtil.mv(commitlog.getAbsolutePath(), logPath);
            if (success) {
                commitlog = new File(logPath);
            } else {
                return ResponseData.create().fail(ResponseCode.FAILED.getCode(), "commit log mv failed");
            }
            log.info("commit log: {}", commitlog.getAbsolutePath());

            try {
                FileUtils.forceDelete(zip);
                FileUtils.forceDelete(tmpDir);
            } catch (IOException e) {
                log.error("delete zip file error, file: " + zip.getAbsolutePath(), e);
            }
        }

        if (commitlog == null) {
            return ResponseData.create().fail(ResponseCode.UNZIP_FAIL.getCode(), ResponseCode.UNZIP_FAIL.getMessage());
        }
        if (commitlog.length() != fileSize) {
            log.error("commit log size not match, src:{}, dst:{}", fileSize, commitlog.length());
            return ResponseData.create().fail(ResponseCode.FILE_NOT_MATCH.getCode(), "file size is not match");
        }

        if (md5 != null) {
            String dstMd5 = MD5Util.md5(commitlog);
            if (dstMd5 != null) {
                log.error("commit log md5 check failed, src:{}, dst:{}", md5, dstMd5);
                return ResponseData.create().fail(ResponseCode.FILE_NOT_MATCH.getCode(), "file check failed");
            }
        }

        boolean done = FileUtil.mv(commitlog.getAbsolutePath(), PathUtil.merge(PathUtil.getCommitLogDir(serverConfig.getStore().get(node)), fileName));
        return done ? ResponseData.create().success() : ResponseData.create().fail(ResponseCode.FAILED.getCode(), "mv commit log failed");
    }
}
