package com.xuxd.rocketmq.reput.config;

import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * ReputConfig.
 *
 * @author xuxd
 * @date 2021-06-29 19:54:56
 **/
@Data
@Configuration
@ConfigurationProperties(prefix = "rocketmq.reput")
public class ReputConfig {

    private int maxNumsGetInQueue = 10000;

    private int getNumsBatchInQueue = 32;

    private int viewMessageMax = 20000;

    private int getMessageMax = 10000;

    private String startMode = "CLIENT";
}
