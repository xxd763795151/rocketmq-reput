package com.xuxd.rocketmq.reput.client;

import com.xuxd.rocketmq.reput.beans.ResponseData;
import com.xuxd.rocketmq.reput.config.ReputClientConfig;
import com.xuxd.rocketmq.reput.enumc.ResponseCode;
import com.xuxd.rocketmq.reput.utils.HttpClientUtil;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.AbstractFileFilter;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.io.filefilter.SizeFileFilter;

/**
 * rocketmq-reput. Scan commitlog, if commit log expired then bakup it.
 *
 * @author xuxd
 * @date 2021-07-02 16:30:23
 **/
@Slf4j
public class CommitlogScanService {

    private final ReputClientConfig config;

    private final File rootDir;

    private final String rootDirPath;

    private final BlockingQueue<File> fileQueue = new LinkedBlockingDeque<>();

    private final long _1M = 1024 * 1024;

    private final IOFileFilter sizeFilter = new SizeFileFilter(_1M * 3/* * 950*/);

    private final IOFileFilter expireFilter;

    private long lastTime = 0;

    private final String node;

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    public CommitlogScanService(ReputClientConfig config, String node, String rootDirPath) {
        this.config = config;
        this.node = node;
        this.rootDirPath = rootDirPath;
        this.rootDir = new File(rootDirPath);
        this.expireFilter = new ExpireFileFilter(config.getExpireTime());
        // load commit log to queue
//        load();
        executorService.execute(() -> {
            while (true) {
                try {
                    File file = fileQueue.take();
                    log.info("Start upload file: {}", file.getAbsolutePath());
                    upload(file);
                } catch (InterruptedException ignore) {
                    log.error("InterruptedException", ignore);
                }
            }
        });
    }

    private void upload(File file) {
        try {
            Map<String, String> params = new HashMap<>();
            params.put("fileName", file.getName());
            String preResult = HttpClientUtil.get(config.getServerAddr() + "/pre/upload", params);
            ResponseData responseData = ResponseData.parse(preResult);
            if (ResponseCode.EXIST_FILE.getCode() == responseData.getCode()) {
                log.error("stop upload, file already exist, message: {}", responseData.getMessage());
            } else {
//                HttpClientUtil.upload(file, config.getServerAddr() + "/upload", null);
            }
        } catch (IOException e) {
            log.error("Upload commit log error. file: " + file.getAbsolutePath(), e);
        }

    }

    public void scan() {
        load();
    }

    private void load() {
        if (!rootDir.exists()) {
            log.error("commit log directory do not exist: {}", rootDir.getAbsolutePath());
            return;
        }

        if (!rootDir.isDirectory()) {
            log.error("it is not a directory, can not scan commit log: {}", rootDir.getAbsolutePath());
            return;
        }

        // if there are one file in the directory, do not handle.
        File[] fileArr = rootDir.listFiles();
        if (fileArr == null || fileArr.length == 1) {
            log.warn("only one or no file, do not handle.");
            return;
        }

        Collection<File> files = FileUtils.listFiles(rootDir, sizeFilter, expireFilter);
        List<File> fileList = new ArrayList<>(files);
        // sort by last modify time.
        Collections.sort(fileList, (o1, o2) -> (int) (o1.lastModified() - o2.lastModified()));

        for (File file : fileList) {
            if (file.lastModified() > lastTime) {
                fileQueue.offer(file);
                lastTime = file.lastModified();
            }
        }
    }

    class ExpireFileFilter extends AbstractFileFilter {

        long expireTime;

        public ExpireFileFilter(int expireHour) {
            this.expireTime = expireHour * 3600;
        }

        @Override public boolean accept(File file) {
            return accept(file, file.getName());
        }

        @Override public boolean accept(File dir, String name) {
            return !dir.isDirectory() && (dir.lastModified() + expireTime < System.currentTimeMillis());
        }
    }
}
