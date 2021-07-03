package com.xuxd.rocketmq.reput.config;

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

    private String startMode = "CLIENT";
}
