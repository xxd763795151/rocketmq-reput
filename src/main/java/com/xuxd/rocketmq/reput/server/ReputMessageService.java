package com.xuxd.rocketmq.reput.server;

import com.xuxd.rocketmq.reput.config.ReputServerConfig;
import com.xuxd.rocketmq.reput.store.ReputMessageStore;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.BrokerConfig;
import org.apache.rocketmq.common.message.MessageDecoder;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.store.GetMessageResult;
import org.apache.rocketmq.store.config.MessageStoreConfig;

/**
 * daily-demo.
 *
 * @author xuxd
 * @date 2021-06-29 19:35:17
 * @description daily-demo
 **/
@Slf4j
public class ReputMessageService {

    private final ReputMessageStore messageStore;

    private final ReputServerConfig reputConfig;

    public ReputMessageService(final ReputServerConfig reputConfig, final BrokerConfig brokerConfig,
        final MessageStoreConfig messageStoreConfig) throws IOException {
        this.reputConfig = reputConfig;
        this.messageStore = new ReputMessageStore(brokerConfig, messageStoreConfig);
    }

    public void start() throws Exception {
        messageStore.start();
    }

    public void shutdown() {
        messageStore.shutdown();
    }

    public List<MessageExt> getMessageInQueueByTime(String topic, int queueId, long startTime, long endTime) {

        return getMessageInQueueByTime(topic, queueId, startTime, endTime, false);
    }

    public List<MessageExt> getMessageInQueueByTime(String topic, int queueId, long startTime, long endTime,
        boolean noMessageBody) {

        List<MessageExt> result = new ArrayList<>();

        long startOffset = messageStore.getOffsetInQueueByTime(topic, queueId, startTime);
        long endOffset = messageStore.getOffsetInQueueByTime(topic, queueId, endTime);
        long diff = endOffset - startOffset;
        if (diff == 0) {
            return result;
        }

        int nums = 0;
        for (long offset = startOffset; offset < endOffset && nums < reputConfig.getMaxNumsGetInQueue(); ) {
            int batchNums = diff >= reputConfig.getGetNumsBatchInQueue() ? reputConfig.getGetNumsBatchInQueue() : (int) diff;
            List<MessageExt> messageExtList = getMessageInQueueByOffset(topic, queueId, offset, batchNums, noMessageBody);
            offset += messageExtList.size();
            nums += messageExtList.size();
            result.addAll(messageExtList);
            diff -= batchNums;
        }

        return result;
    }

    public List<MessageExt> getMessageByTime(String topic, long startTime, long endTime) {
        List<MessageExt> result = new ArrayList<>();
        Set<Integer> queueSet = messageStore.getMessageQueueIdSet(topic);
        for (Integer id : queueSet) {
            result.addAll(getMessageInQueueByTime(topic, id, startTime, endTime));
            if (result.size() > reputConfig.getGetMessageMax()) {
                return result.subList(0, reputConfig.getGetMessageMax());
            }
        }
        return result;
    }

    /**
     * 返回消息列表，不包含消息体
     *
     * @param topic     topic
     * @param startTime 查询开始时间
     * @param endTime   查询结束时间
     * @return 没有消息体的消息列表
     */
    public List<MessageExt> viewMessageList(String topic, long startTime, long endTime) {
        List<MessageExt> result = new ArrayList<>();
        Set<Integer> queueSet = messageStore.getMessageQueueIdSet(topic);
        for (Integer id : queueSet) {
            result.addAll(getMessageInQueueByTime(topic, id, startTime, endTime, true));
            if (result.size() > reputConfig.getViewMessageMax()) {
                return result.subList(0, reputConfig.getViewMessageMax());
            }
        }

        return result;
    }

    public int getMessageTotalByTime(String topic, long startTime, long endTime) {
        Set<Integer> queueSet = messageStore.getMessageQueueIdSet(topic);
        int count = 0;
        for (Integer integer : queueSet) {
            long startOffset = messageStore.getOffsetInQueueByTime(topic, integer, startTime);
            long endOffset = messageStore.getOffsetInQueueByTime(topic, integer, endTime);
            long diff = endOffset - startOffset;
            count += diff;
        }
        return count;
    }

    public MessageExt queryMessageByMsgId(String topic, String msgId) {
        return messageStore.queryMessageByMsgId(topic, msgId);
    }

    public List<MessageExt> queryMessageByKey(String topic, String key, int maxNum) {
        return messageStore.queryMessageByKey(topic, key, reputConfig.getGetMessageMax());
    }

    private List<MessageExt> getMessageInQueueByOffset(String topic, int queueId, long offset, int maxMsgNums,
        boolean noBody) {
        List<MessageExt> result = new ArrayList<>();
        GetMessageResult getMessageResult = messageStore.getMessage(topic, queueId, offset, maxMsgNums);
        switch (getMessageResult.getStatus()) {
            case FOUND:
                if (noBody) {
                    List<MessageExt> list = MessageDecoder.decodes(readGetMessageResult(getMessageResult));
                    list.forEach(msg -> msg.setBody(null));
                    result.addAll(list);
                } else {
                    result.addAll(MessageDecoder.decodes(readGetMessageResult(getMessageResult)));
                }
                break;
            default:
                return result;
        }
        return result;
    }

    private ByteBuffer readGetMessageResult(final GetMessageResult getMessageResult) {
        final ByteBuffer byteBuffer = ByteBuffer.allocate(getMessageResult.getBufferTotalSize());

        try {
            List<ByteBuffer> messageBufferList = getMessageResult.getMessageBufferList();
            for (ByteBuffer bb : messageBufferList) {
                byteBuffer.put(bb);
            }
        } finally {
            getMessageResult.release();
        }

        byteBuffer.flip();
        return byteBuffer;
    }
}
