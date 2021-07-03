package com.xuxd.rocketmq.reput.watch;

import java.io.File;

/**
 * daily-demo.
 *
 * @author xuxd
 * @date 2021-06-30 14:01:47
 * @description daily-demo
 **/
public interface FileWatchListener {

    default void onFileCreate(File file) {

    }

    default void onFileChange(File file) {

    }

    default void onFileDelete(File file) {

    }
}
