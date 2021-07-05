package com.xuxd.rocketmq.reput.utils;

import lombok.extern.slf4j.Slf4j;

/**
 * rocketmq-reput.
 *
 * @author xuxd
 * @date 2021-07-03 10:37:00
 **/
@Slf4j
public class OSUtil {

    private OSUtil() {
    }

    public static void platformCheck() {
        String os = System.getProperty("os.name");
        log.info("os: {}", os);
        if (os.toLowerCase().contains("win")) {
            throw new UnsupportedOperationException("do not support windows.");
        }
    }

    public static boolean isLinux() {
        String os = System.getProperty("os.name").toLowerCase();
        return os.contains("linux");
    }
}
