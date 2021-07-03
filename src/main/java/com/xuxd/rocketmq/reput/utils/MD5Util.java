package com.xuxd.rocketmq.reput.utils;

import java.io.File;

/**
 * rocketmq-reput.
 *
 * @author xuxd
 * @date 2021-07-03 11:11:06
 **/
public class MD5Util {

    private MD5Util() {
    }

    public static String md5(File file) {
        // use shell is more simple because of the time for development. I may modify it in the future when I am free.
        return ShellUtil.md5(file);
    }
}
