package com.xuxd.rocketmq.reput.boot;

import com.google.common.base.Throwables;
import com.xuxd.rocketmq.reput.config.ReputConfig;
import com.xuxd.rocketmq.reput.config.ReputServerConfig;
import com.xuxd.rocketmq.reput.enumc.StartMode;
import com.xuxd.rocketmq.reput.server.QueryMessageService;
import com.xuxd.rocketmq.reput.server.ReputMessageService;
import com.xuxd.rocketmq.reput.utils.PathUtil;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.rocketmq.common.BrokerConfig;
import org.apache.rocketmq.store.config.MessageStoreConfig;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * rocketmq-reput. Server start.
 *
 * @author xuxd
 * @date 2021-07-02 15:31:19
 **/
@Slf4j
@Component
public class ServerBootstrap extends Bootstrap {

    private final ReputConfig reputConfig;

    private final ReputServerConfig serverConfig;

    private final File rootDir;

    private final String rootPath;

    private final Map<String, ReputMessageService> serviceCache = new HashMap<>();

    @Autowired
    private QueryMessageService messageService;

    public ServerBootstrap(final ObjectProvider<ReputConfig> provider,
        final ObjectProvider<ReputServerConfig> serverProvider) {
        super(provider.getIfAvailable(ReputConfig::new));

        reputConfig = provider.getIfAvailable(ReputConfig::new);
        serverConfig = serverProvider.getIfAvailable(ReputServerConfig::new);
        this.rootPath = serverConfig.getRootDir();
        rootDir = new File(serverConfig.getRootDir());
    }

    @Override public void start() {
        checkStoreHome();
        startReputService();
        messageService.registerReputService(serviceCache);
        clearDirtyData();
    }

    private void clearDirtyData() {
        log.info("clear dirty data.");
        serverConfig.getStore().forEach((k, v) -> {
            String zipPath = PathUtil.getZipDir(v);
            File zipDir = new File(zipPath);
            if (zipDir.exists()) {
                try {
                    FileUtils.forceDelete(zipDir);
                    log.info("delete dir: {}", zipDir.getAbsolutePath());
                } catch (IOException e) {
                    log.error("clear dirty data error, file: " + zipDir.getAbsolutePath(), e);
                }
            }
        });
    }

    private void startReputService() {
        serverConfig.getStore().forEach((node, path) -> {
            if (!serviceCache.containsKey(node)) {
                String home = PathUtil.merge(serverConfig.getRootDir(), node);

                BrokerConfig brokerConfig = new BrokerConfig();
                brokerConfig.setRocketmqHome(home);

                MessageStoreConfig messageStoreConfig = new MessageStoreConfig();
                messageStoreConfig.setStorePathRootDir(PathUtil.getStoreDir(home));
                messageStoreConfig.setStorePathCommitLog(PathUtil.getCommitLogDir(home));
                messageStoreConfig.setFileReservedTime(serverConfig.getFileReservedTime());
                messageStoreConfig.setDeleteWhen(serverConfig.getDeleteWhen());
                messageStoreConfig.setEnableDLegerCommitLog(serverConfig.isEnableDledger());

                try {
                    ReputMessageService service = new ReputMessageService(serverConfig, brokerConfig, messageStoreConfig);
                    serviceCache.put(node, service);
                } catch (IOException e) {
                    log.error("init service error, path: " + home, e);
                }
            }
            try {
                serviceCache.get(node).start();
            } catch (Exception e) {
                log.error("start service error, node: " + node, e);
            }
        });
    }

    private void checkStoreHome() {
        forceMkdirIfNot(rootPath);
        if (!rootDir.isDirectory()) {
            throw new IllegalStateException(rootDir + " is not a directory");
        }
        serverConfig.getStore().forEach((k, v) -> {
            forceMkdirIfNot(PathUtil.getStoreDir(v));
            forceMkdirIfNot(PathUtil.getCommitLogDir(v));
            forceMkdirIfNot(PathUtil.getZipDir(v));
        });

    }

    @Override public void shutdown() {
        serviceCache.values().stream().forEach(ReputMessageService::shutdown);
        serviceCache.clear();
    }

    @Override public StartMode startMode() {
        return StartMode.SERVER;
    }

    private void forceMkdirIfNot(String path) {
        File dir = new File(path);
        if (!dir.exists()) {
            try {
                FileUtils.forceMkdir(dir);
                log.info("create dir: {}", dir.getAbsolutePath());
            } catch (IOException e) {
                Throwables.propagate(e);
            }
        }
    }

    @Override public void printConfig() {
        log.info(serverConfig.toString());
    }
}
