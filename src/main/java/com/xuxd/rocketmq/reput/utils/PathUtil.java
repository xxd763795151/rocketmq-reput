package com.xuxd.rocketmq.reput.utils;

import java.io.File;
import java.util.Arrays;

/**
 * rocketmq-reput.
 *
 * @author xuxd
 * @date 2021-07-02 21:05:53
 **/
public final class PathUtil {

    private PathUtil() {
    }

    public static String getStoreDir(String rootDir) {
        return rootDir + File.separator + "store";
    }

    public static String getCommitLogDir(String rootDir) {
        return getStoreDir(rootDir) + File.separator + "commitlog";
    }

    public static String getZipDir(String rootDir) {
        return rootDir + File.separator + "zip";
    }

    public static String merge(String dir, String... path) {
        if (path == null || path.length == 0) {
            return dir;
        }

        StringBuilder dst = new StringBuilder(dir);
        Arrays.stream(path).forEach(p -> dst.append(File.separator).append(p));
        return dst.toString();
    }

    public static String getTmpDir(String dir) {
        return merge(dir, "tmp");
    }
}
