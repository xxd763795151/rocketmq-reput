package com.xuxd.rocketmq.reput.server;

import com.xuxd.rocketmq.reput.beans.ResponseData;
import com.xuxd.rocketmq.reput.config.ReputConfig;
import com.xuxd.rocketmq.reput.config.ReputServerConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * rocketmq-reput.
 *
 * @author xuxd
 * @date 2021-07-02 20:58:38
 **/
@Component
public class CommitLogUploadService {

    @Autowired
    private ReputConfig reputConfig;

    @Autowired
    private ReputServerConfig serverConfig;

    public ResponseData preUpload(String fileName) {
        return null;
    }
}
