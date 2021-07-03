package com.xuxd.rocketmq.reput.boot;

import com.xuxd.rocketmq.reput.client.CommitlogScanService;
import com.xuxd.rocketmq.reput.config.ReputClientConfig;
import com.xuxd.rocketmq.reput.config.ReputConfig;
import com.xuxd.rocketmq.reput.enumc.StartMode;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * rocketmq-reput. Client start.
 *
 * @author xuxd
 * @date 2021-07-02 15:35:00
 **/
@Slf4j
@Component
public class ClientBootstrap extends Bootstrap {

    private final ScheduledExecutorService executorService;

    private final ReputConfig reputConfig;

    private final ReputClientConfig clientConfig;

    private final Map<String, CommitlogScanService> serviceCache = new HashMap<>();

    private ClientBootstrap(final ObjectProvider<ReputConfig> provider,
        final ObjectProvider<ReputClientConfig> clientConfigObjectProvider) {
        super(provider.getIfAvailable(ReputConfig::new));
        reputConfig = provider.getIfAvailable(ReputConfig::new);
        clientConfig = clientConfigObjectProvider.getIfAvailable(ReputClientConfig::new);

        Map<String, String> commitlog = clientConfig.getCommitlog();
        int nt = commitlog.isEmpty() ? 1 : commitlog.size() + 1;
        executorService = Executors.newScheduledThreadPool(nt, r -> {

            Thread t = new Thread(r, "client-commitlog");
            if (!t.isDaemon()) {
                t.setDaemon(true);
            }
            return t;
        });
    }

    @Override public void start() {
        // scan the directory that store commitlog for upload server.
        if (!clientConfig.getCommitlog().isEmpty()) {
            startScanTask();
        }
    }

    private void startScanTask() {

        Queue<String> queue = new LinkedList<>(clientConfig.getCommitlog().keySet());
        clientConfig.getCommitlog().forEach((k, v) -> {
            if (!serviceCache.containsKey(k)) {
                serviceCache.put(k, new CommitlogScanService(clientConfig, k, v));
            }
            executorService.scheduleWithFixedDelay(() -> {
                 serviceCache.get(k).scan();
            }, clientConfig.getScanInterval(), clientConfig.getScanInterval(), TimeUnit.MINUTES);
            queue.poll();
            while (!queue.isEmpty()) {
                // delay a while
                try {
                    TimeUnit.MILLISECONDS.sleep(10000);
                } catch (InterruptedException ignore) {
                }
            }
        });
    }

    @Override public void shutdown() {
        executorService.shutdown();
        serviceCache.clear();
    }

    @Override public StartMode startMode() {
        return StartMode.CLIENT;
    }

    @Override public void printConfig() {
        log.info(clientConfig.toString());
    }
}
