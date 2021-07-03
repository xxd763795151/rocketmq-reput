package com.xuxd.rocketmq.reput.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

/**
 * rocketmq-reput.
 *
 * @author xuxd
 * @date 2021-07-03 10:42:39
 **/
@Slf4j
public class ShellUtil {

    private ShellUtil() {
    }

    public static String md5(File file) {
        if (!OSUtil.isLinux()) {
            return null;
        }
//        String command = OSUtil.isLinux() ? "md5sum" : "md5";
        String command = "md5sum";
        try {
            Process process = Runtime.getRuntime().exec(command + " " + file.getAbsolutePath());
            return getStringResult(process);
        } catch (Exception e) {
            log.error("md5 compute error", e);
            return null;
        }
    }

    public static File zip(File file) {
        String zipName = file.getAbsolutePath() + ".zip";
        String command = "zip -r " + zipName + " " + file.getAbsolutePath();
        try {
            File zip = new File(zipName);
            if (zip.exists()) {
                FileUtils.forceDelete(zip);
            }
            Process process = Runtime.getRuntime().exec(command + " " + file.getAbsolutePath());

            int status = process.waitFor();
            if (status != 0 && !zip.exists()) {
                log.error("zip error, cause: {}", getError(process));
            } else {
                return zip;
            }
        } catch (Exception e) {
            log.error("zip error", e);
        }
        return null;
    }

    private static String getStringResult(Process process) throws Exception {
        try {
            int status = process.waitFor();
            if (status == 0) {
                try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    StringBuilder result = new StringBuilder();
                    String line;
                    while ((line = bufferedReader.readLine()) != null) {
                        result.append(line);
                    }
                    return result.toString();
                }
            } else {
                String error = getError(process);
                log.error("Shell exec error: {}", error);
            }

        } catch (InterruptedException ignore) {
            log.info("InterruptedException", ignore);
        }
        return null;
    }

    private static String getError(Process process) throws Exception {
        try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
            StringBuilder error = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                error.append(line);
            }
            return error.toString();
        }
    }
}
