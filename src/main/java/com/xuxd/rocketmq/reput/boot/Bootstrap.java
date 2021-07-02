package com.xuxd.rocketmq.reput.boot;

import com.xuxd.rocketmq.reput.config.ReputConfig;
import com.xuxd.rocketmq.reput.enumc.StartMode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;

/**
 * rocketmq-reput.
 *
 * @author xuxd
 * @date 2021-07-02 15:36:55
 **/
@Slf4j
public abstract class Bootstrap implements SmartInitializingSingleton {

    public abstract void start();

    public abstract void shutdown();

    public abstract StartMode startMode();

    private final StartMode mode;

    public Bootstrap(ReputConfig  reputConfig) {
        mode = StartMode.valueOf(reputConfig.getStartMode());
        log.info(reputConfig.toString());
    }

    @Override public void afterSingletonsInstantiated() {

        if (mode == startMode() || mode == StartMode.MIXED) {
            log.info("Starting {}", mode);
            start();
            Runtime.getRuntime().addShutdownHook(new Thread(()->{
                shutdown();
            }));
        }

    }
}
