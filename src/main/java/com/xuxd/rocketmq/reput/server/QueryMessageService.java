package com.xuxd.rocketmq.reput.server;

import com.xuxd.rocketmq.reput.config.ReputServerConfig;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * rocketmq-reput.
 *
 * @author xuxd
 * @date 2021-07-03 17:37:47
 **/
@Slf4j
@Component
public class QueryMessageService {

    private final ExecutorService executorService = Executors.newFixedThreadPool(20);

    private final Map<String, ReputMessageService> reputServiceCache = new HashMap<>();

    private final ReputServerConfig serverConfig;

    private final int nodeNums;

    @Autowired
    public QueryMessageService(ReputServerConfig serverConfig) {
        this.serverConfig = serverConfig;
        this.nodeNums = serverConfig.getStore().size();
    }

    public void registerReputService(Map<String, ReputMessageService> serviceMap) {
        reputServiceCache.putAll(serviceMap);
    }

    public int getMessageTotalByTime(final String topic, final long startTime, final long endTime) {
        CompletionService<Integer> completionService = new ExecutorCompletionService<>(executorService);
        reputServiceCache.forEach((k, v) -> {
            completionService.submit(() -> v.getMessageTotalByTime(topic, startTime, endTime));
        });
        int total = 0;
        for (int i = 0; i < nodeNums; i++) {
            try {
                total += completionService.take().get();
            } catch (Exception e) {
                log.error("getMessageTotalByTime error", e);
            }
        }

        return total;
    }

    public List<MessageExt> getMessageByTime(final String topic, final long startTime, final long endTime) {
        List<MessageExt> result = new ArrayList<>();
        CompletionService<List<MessageExt>> completionService = new ExecutorCompletionService<>(executorService);
        reputServiceCache.forEach((k, v) -> {
            completionService.submit(() -> v.getMessageByTime(topic, startTime, endTime));
        });
        for (int i = 0; i < nodeNums; i++) {
            try {
                result.addAll(completionService.take().get());
            } catch (Exception e) {
                log.error("getMessageByTime error", e);
            }
        }

        return result;
    }

    public List<MessageExt> viewMessageList(final String topic, final long startTime, final long endTime) {
        List<MessageExt> result = new ArrayList<>();
        CompletionService<List<MessageExt>> completionService = new ExecutorCompletionService<>(executorService);
        reputServiceCache.forEach((k, v) -> {
            completionService.submit(() -> v.viewMessageList(topic, startTime, endTime));
        });
        for (int i = 0; i < nodeNums; i++) {
            try {
                result.addAll(completionService.take().get());
            } catch (Exception e) {
                log.error("viewMessageList error", e);
            }
        }

        return result;
    }

    public MessageExt queryMessageByMsgId(final String topic, final String msgId) {
        CompletionService<MessageExt> completionService = new ExecutorCompletionService<>(executorService);
        reputServiceCache.forEach((k, v) -> {
            completionService.submit(() -> v.queryMessageByMsgId(topic, msgId));
        });
        for (int i = 0; i < nodeNums; i++) {
            try {
                MessageExt messageExt = completionService.take().get();
                if (messageExt != null) {
                    return messageExt;
                }
            } catch (Exception e) {
                log.error("queryMessageByMsgId error", e);
            }
        }

        return null;

    }

    public List<MessageExt> queryMessageByKey(final String topic, final String key) {
        List<MessageExt> result = new ArrayList<>();
        CompletionService<List<MessageExt>> completionService = new ExecutorCompletionService<>(executorService);
        reputServiceCache.forEach((k, v) -> {
            completionService.submit(() -> v.queryMessageByKey(topic, key, serverConfig.getGetMessageMax()));
        });
        for (int i = 0; i < nodeNums; i++) {
            try {
                result.addAll(completionService.take().get());
            } catch (Exception e) {
                log.error("queryMessageByKey error", e);
            }
        }

        return result;
    }
}
