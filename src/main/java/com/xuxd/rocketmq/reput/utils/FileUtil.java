package com.xuxd.rocketmq.reput.utils;

import com.google.common.base.Throwables;
import java.io.File;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

/**
 * rocketmq-reput.
 *
 * @author xuxd
 * @date 2021-07-03 14:46:47
 **/
@Slf4j
public final class FileUtil {
    private FileUtil() {
    }

    public static boolean mv(String src, String dst) {
        return ShellUtil.mv(src, dst);
    }

    public static void forceMkdirIfNot(String path) {
        File dir = new File(path);
        if (!dir.exists()) {
            try {
                FileUtils.forceMkdir(dir);
                log.info("create dir: {}", dir.getAbsolutePath());
            } catch (IOException e) {
                Throwables.propagate(e);
            }
        }
    }

    public static File findFirstLeaf(File dir) {
        if (!dir.isDirectory()) {
            return dir;

        }
        File[] files = dir.listFiles();
        if (files != null && files.length > 0) {
            return findFirstLeaf(files[0]);
        }
        return null;
    }
}
