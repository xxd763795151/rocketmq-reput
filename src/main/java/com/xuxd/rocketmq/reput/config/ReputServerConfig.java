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
}
