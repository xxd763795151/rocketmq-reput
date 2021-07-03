package com.xuxd.rocketmq.reput;

import com.xuxd.rocketmq.reput.utils.OSUtil;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class RocketmqReputApplication {

    public static void main(String[] args) {
        OSUtil.platCheck();
        SpringApplication.run(RocketmqReputApplication.class, args);
    }

}
