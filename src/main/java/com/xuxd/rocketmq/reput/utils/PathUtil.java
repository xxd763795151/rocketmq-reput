package com.xuxd.rocketmq.reput.utils;

import java.io.File;

/**
 * rocketmq-reput.
 *
 * @author xuxd
 * @date 2021-07-02 21:05:53
 **/
public final class PathUtil {

    private PathUtil(){}

    public static String getStoreDir(String rootDir) {
        return rootDir + File.separator + "store";
    }

    public static String getCommitLogDir(String rootDir) {
        return getStoreDir(rootDir) + File.separator + "commitlog";
    }

    public static String getZipDir(String rootDir) {
        return rootDir + File.separator + "zip";
    }
}
