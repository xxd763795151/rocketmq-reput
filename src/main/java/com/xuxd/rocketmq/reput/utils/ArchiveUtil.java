package com.xuxd.rocketmq.reput.utils;

import java.io.File;

/**
 * rocketmq-reput.
 *
 * @author xuxd
 * @date 2021-07-03 11:30:02
 **/
public class ArchiveUtil {

    private ArchiveUtil() {
    }

    public static File zip(File src) {
        return ShellUtil.zip(src);
    }

    public static File unzip(File src) {
        return ShellUtil.unzip(src);
    }
}
