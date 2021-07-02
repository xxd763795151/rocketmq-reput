package com.xuxd.rocketmq.reput.config;

import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * rocketmq-reput.
 *
 * @author xuxd
 * @date 2021-07-02 16:08:32
 **/
@Data
@Configuration
@ConfigurationProperties(prefix = "rocketmq.reput.client")
public class ReputClientConfig {

    private String serverAddr;

    private Map<String, String> commitlog = new HashMap<>();

    private int scanInterval = 1;

    /**
     * 过期时间，按小时为单位
     */
    private int expireTime = 48;
}
