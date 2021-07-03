package com.xuxd.rocketmq.reput.boot;

import com.google.common.base.Throwables;
import com.xuxd.rocketmq.reput.config.ReputConfig;
import com.xuxd.rocketmq.reput.config.ReputServerConfig;
import com.xuxd.rocketmq.reput.enumc.StartMode;
import com.xuxd.rocketmq.reput.utils.PathUtil;
import java.io.File;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.ObjectProvider;
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
    }

    private void checkStoreHome() {
        forceMkdirIfNot(rootPath);
        if (!rootDir.isDirectory()) {
            throw new IllegalStateException(rootDir + " is not a directory");
        }
        forceMkdirIfNot(PathUtil.getStoreDir(rootPath));
        forceMkdirIfNot(PathUtil.getCommitLogDir(rootPath));
        forceMkdirIfNot(PathUtil.getZipDir(rootPath));
    }

    @Override public void shutdown() {

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
