package com.xuxd.rocketmq.reput.server;

import com.xuxd.rocketmq.reput.beans.ResponseData;
import com.xuxd.rocketmq.reput.config.ReputConfig;
import com.xuxd.rocketmq.reput.config.ReputServerConfig;
import com.xuxd.rocketmq.reput.enumc.ResponseCode;
import com.xuxd.rocketmq.reput.utils.PathUtil;
import java.io.File;
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

    private final String commitLogDirPath;

    private final File commitLogDir;


    @Autowired
    public CommitLogUploadService(final ReputConfig reputConfig, final ReputServerConfig serverConfig) {
        this.reputConfig = reputConfig;
        this.serverConfig = serverConfig;
        this.commitLogDirPath = PathUtil.getCommitLogDir(serverConfig.getRootDir());
        this.commitLogDir = new File(commitLogDirPath);
    }

    public ResponseData preUpload(String fileName) {

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
}
