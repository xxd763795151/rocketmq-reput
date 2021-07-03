package com.xuxd.rocketmq.reput.watch;

import java.io.File;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.monitor.FileAlterationListenerAdaptor;
import org.apache.commons.io.monitor.FileAlterationMonitor;
import org.apache.commons.io.monitor.FileAlterationObserver;

/**
 * daily-demo.
 * File watch.
 * @author xuxd
 * @date 2021-06-30 14:01:17
 * @description daily-demo
 **/
@Slf4j
public class FileWatcher {

    private final String rootDir;

    private final FileWatchListener listener;

    @Setter
    private volatile boolean canCreate;

    public FileWatcher(final String rootDir, final FileWatchListener listener) throws Exception{

        this(rootDir, listener, true);
    }

    public FileWatcher(final String rootDir, final FileWatchListener listener, final boolean canCreate) throws Exception{
        this.rootDir = rootDir;
        this.listener = listener;
        this.canCreate = canCreate;
        init();
    }

    private void init() throws Exception {
        FileAlterationObserver observer = new FileAlterationObserver(rootDir);
        observer.addListener(new FileAlterationListenerAdaptor() {
            @Override
            public void onFileCreate(File file) {
                if (!canCreate) {
                    return;
                }
                listener.onFileCreate(file);
            }

            @Override
            public void onFileChange(File file) {
                listener.onFileChange(file);
            }

            @Override
            public void onFileDelete(File file) {
                listener.onFileDelete(file);
            }
        });
        observer.checkAndNotify();
        FileAlterationMonitor monitor = new FileAlterationMonitor();
        monitor.addObserver(observer);
        monitor.start();
        log.info("Start file change monitor. Path: {}", rootDir);
    }
}
