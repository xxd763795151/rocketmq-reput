package com.xuxd.rocketmq.reput.config;

import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * rocketmq-reput.
 *
 * @author xuxd
 * @date 2021-07-02 16:08:42
 **/
@Data
@Configuration
@ConfigurationProperties(prefix = "rocketmq.reput.server")
public class ReputServerConfig {

    private String rootDir;

    private Map<String, String> store;

    private int maxNumsGetInQueue = 10000;

    private int getNumsBatchInQueue = 32;

    private int viewMessageMax = 20000;

    private int getMessageMax = 10000;

    private int fileReservedTime;

    private String deleteWhen;

    private boolean enableDledger = false;

}
