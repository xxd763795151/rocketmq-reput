package com.xuxd.rocketmq.reput.store;

import com.xuxd.rocketmq.reput.store.commitlog.ReputCommitLog;
import com.xuxd.rocketmq.reput.store.commitlog.ReputDLedgerCommitLog;
import com.xuxd.rocketmq.reput.store.index.IndexService;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileLock;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.BrokerConfig;
import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.common.ServiceThread;
import org.apache.rocketmq.common.SystemClock;
import org.apache.rocketmq.common.ThreadFactoryImpl;
import org.apache.rocketmq.common.UtilAll;
import org.apache.rocketmq.common.message.MessageClientIDSetter;
import org.apache.rocketmq.common.message.MessageDecoder;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.message.MessageExtBatch;
import org.apache.rocketmq.common.message.MessageId;
import org.apache.rocketmq.common.sysflag.MessageSysFlag;
import org.apache.rocketmq.store.CommitLogDispatcher;
import org.apache.rocketmq.store.ConsumeQueue;
import org.apache.rocketmq.store.DispatchRequest;
import org.apache.rocketmq.store.GetMessageResult;
import org.apache.rocketmq.store.GetMessageStatus;
import org.apache.rocketmq.store.MappedFile;
import org.apache.rocketmq.store.MessageExtBrokerInner;
import org.apache.rocketmq.store.MessageFilter;
import org.apache.rocketmq.store.MessageStore;
import org.apache.rocketmq.store.PutMessageResult;
import org.apache.rocketmq.store.QueryMessageResult;
import org.apache.rocketmq.store.SelectMappedBufferResult;
import org.apache.rocketmq.store.config.BrokerRole;
import org.apache.rocketmq.store.config.MessageStoreConfig;
import org.apache.rocketmq.store.config.StorePathConfigHelper;
import org.apache.rocketmq.store.index.QueryOffsetResult;
import org.apache.rocketmq.store.stats.BrokerStatsManager;

/**
 * daily-demo. Only support reput message from commitlog. Do not support consume message from store. See {@link
 * org.apache.rocketmq.store.DefaultMessageStore}
 *
 * @author xuxd
 * @date 2021-06-28 10:08:49
 * @description daily-demo
 **/
@Slf4j
@Getter
public class ReputMessageStore implements MessageStore {

    private final BrokerConfig brokerConfig;

    private final MessageStoreConfig messageStoreConfig;

    private final ReputCommitLog commitLog;

    private final ConcurrentMap<String/* topic */, ConcurrentMap<Integer/* queueId */, ReputConsumeQueue>> consumeQueueTable;

    private final IndexService indexService;

    private final LinkedList<CommitLogDispatcher> dispatcherList;

    private RandomAccessFile lockFile;

    private FileLock lock;

    private final ReputMessageService reputMessageService;

    private volatile boolean shutdown = true;

    private final FlushConsumeQueueService flushConsumeQueueService;

    private final ScheduledExecutorService scheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor(new ThreadFactoryImpl("StoreScheduledThread"));

    private final ScheduledExecutorService diskCheckScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor(new ThreadFactoryImpl("DiskCheckScheduledThread"));

    private final CleanCommitLogService cleanCommitLogService;

    private final CleanConsumeQueueService cleanConsumeQueueService;

    private final SystemClock systemClock = new SystemClock();

    public ReputMessageStore(final BrokerConfig brokerConfig,
        final MessageStoreConfig messageStoreConfig) throws IOException {
        this.brokerConfig = brokerConfig;
        this.messageStoreConfig = messageStoreConfig;
        if (messageStoreConfig.isEnableDLegerCommitLog()) {
            this.commitLog = new ReputDLedgerCommitLog(this);
        } else {
            this.commitLog = new ReputCommitLog(this);
        }
        this.consumeQueueTable = new ConcurrentHashMap<>(32);
        this.indexService = new IndexService(this);
        this.dispatcherList = new LinkedList<>();
        this.dispatcherList.addLast(new CommitLogDispatcherBuildConsumeQueue());
        this.dispatcherList.addLast(new CommitLogDispatcherBuildIndex());

        File file = new File(StorePathConfigHelper.getLockFile(messageStoreConfig.getStorePathRootDir()));
        MappedFile.ensureDirOK(file.getParent());
        lockFile = new RandomAccessFile(file, "rw");

        this.reputMessageService = new ReputMessageService();
        this.flushConsumeQueueService = new FlushConsumeQueueService();
        this.cleanCommitLogService = new CleanCommitLogService();
        this.cleanConsumeQueueService = new CleanConsumeQueueService();
    }

    @Override public boolean load() {
        boolean result = true;

        try {

            // load Commit Log
            result = result && this.commitLog.load();

            // load Consume Queue
            result = result && this.loadConsumeQueue();

            if (result) {

                this.indexService.load();

                this.recover();

                log.info("load over, and the max phy offset = {}", this.getMaxPhyOffset());
            }
        } catch (Exception e) {
            log.error("load exception", e);
            result = false;
        }

        return result;
    }

    @Override public void start() throws Exception {

        this.indexService.start(); // no logic
        // first, load and initialize
        load();

        lock = lockFile.getChannel().tryLock(0, 1, false);
        if (lock == null || lock.isShared() || !lock.isValid()) {
            throw new RuntimeException("Lock failed,MQ already started");
        }
        lockFile.getChannel().write(ByteBuffer.wrap("lock".getBytes()));
        lockFile.getChannel().force(true);
        {
            long maxPhysicalPosInLogicQueue = commitLog.getMinOffset();
            // 这个for循环是为了从ConsumeQueue（逻辑消费队列）中计算出目前的已经reput的最大物理偏移
            for (ConcurrentMap<Integer, ReputConsumeQueue> maps : this.consumeQueueTable.values()) {
                for (ReputConsumeQueue logic : maps.values()) {
                    if (logic.getMaxPhysicOffset() > maxPhysicalPosInLogicQueue) {
                        maxPhysicalPosInLogicQueue = logic.getMaxPhysicOffset();
                    }
                }
            }
            if (maxPhysicalPosInLogicQueue < 0) {
                maxPhysicalPosInLogicQueue = 0;
            }
            if (maxPhysicalPosInLogicQueue < this.commitLog.getMinOffset()) {
                maxPhysicalPosInLogicQueue = this.commitLog.getMinOffset();
                /**
                 * This happens in following conditions:
                 * 1. If someone removes all the consumequeue files or the disk get damaged.
                 * 2. Launch a new broker, and copy the commitlog from other brokers.
                 *
                 * All the conditions has the same in common that the maxPhysicalPosInLogicQueue should be 0.
                 * If the maxPhysicalPosInLogicQueue is gt 0, there maybe something wrong.
                 */
                log.warn("[TooSmallCqOffset] maxPhysicalPosInLogicQueue={} clMinOffset={}", maxPhysicalPosInLogicQueue, this.commitLog.getMinOffset());
            }
            log.info("[SetReputOffset] maxPhysicalPosInLogicQueue={} clMinOffset={} clMaxOffset={} clConfirmedOffset={}",
                maxPhysicalPosInLogicQueue, this.commitLog.getMinOffset(), this.commitLog.getMaxOffset(), this.commitLog.getConfirmOffset());
            this.reputMessageService.setReputFromOffset(maxPhysicalPosInLogicQueue);
            this.reputMessageService.start();
            /**
             *  1. Finish dispatching the messages fall behind, then to start other services.
             *  2. DLedger committedPos may be missing, so here just require dispatchBehindBytes <= 0
             */
            while (true) {
                if (dispatchBehindBytes() <= 0) { // commitlog的最大位置竟然小于逻辑队列里的偏移，等他们分发位置跟上来，再继续后面的代码运行
                    break;
                }
                Thread.sleep(1000);
                log.info("Try to finish doing reput the messages fall behind during the starting, reputOffset={} maxOffset={} behind={}", this.reputMessageService.getReputFromOffset(), this.getMaxPhyOffset(), this.dispatchBehindBytes());
            }
            this.recoverTopicQueueTable();
        }

        this.flushConsumeQueueService.start();
        this.commitLog.start();

        this.addScheduleTask();
        this.shutdown = false;
    }

    @Override public void shutdown() {
        if (!this.shutdown) {
            this.shutdown = true;

            this.scheduledExecutorService.shutdown();
            this.diskCheckScheduledExecutorService.shutdown();
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                log.error("shutdown Exception, ", e);
            }

            this.indexService.shutdown();
            this.commitLog.shutdown();
            this.reputMessageService.shutdown();
            this.flushConsumeQueueService.shutdown();

        }
        if (lockFile != null && lock != null) {
            try {
                lock.release();
                lockFile.close();
            } catch (IOException e) {
            }
        }
    }

    @Override public void destroy() {
        this.destroyLogics();
        this.commitLog.destroy();
        this.indexService.destroy();
    }

    @Override public PutMessageResult putMessage(MessageExtBrokerInner msg) {
        return null;
    }

    @Override public PutMessageResult putMessages(MessageExtBatch messageExtBatch) {
        return null;
    }

    @Override public GetMessageResult getMessage(String group, String topic, int queueId, long offset, int maxMsgNums,
        MessageFilter messageFilter) {
        return null;
    }

    @Override public long getMaxOffsetInQueue(String topic, int queueId) {
        return 0;
    }

    @Override public long getMinOffsetInQueue(String topic, int queueId) {
        return 0;
    }

    @Override public long getCommitLogOffsetInQueue(String topic, int queueId, long consumeQueueOffset) {
        return 0;
    }

    @Override public long getOffsetInQueueByTime(String topic, int queueId, long timestamp) {
        ReputConsumeQueue logic = this.findConsumeQueue(topic, queueId);
        if (logic != null) {
            return logic.getOffsetInQueueByTime(timestamp);
        }

        return 0;
    }

    @Override public MessageExt lookMessageByOffset(long commitLogOffset) {
        SelectMappedBufferResult sbr = this.commitLog.getMessage(commitLogOffset, 4);
        if (null != sbr) {
            try {
                // 1 TOTALSIZE
                int size = sbr.getByteBuffer().getInt();
                return lookMessageByOffset(commitLogOffset, size);
            } finally {
                sbr.release();
            }
        }

        return null;
    }

    public MessageExt lookMessageByOffset(long commitLogOffset, int size) {
        SelectMappedBufferResult sbr = this.commitLog.getMessage(commitLogOffset, size);
        if (null != sbr) {
            try {
                return MessageDecoder.decode(sbr.getByteBuffer(), true, false);
            } finally {
                sbr.release();
            }
        }

        return null;
    }

    @Override public SelectMappedBufferResult selectOneMessageByOffset(long commitLogOffset) {
        SelectMappedBufferResult sbr = this.commitLog.getMessage(commitLogOffset, 4);
        if (null != sbr) {
            try {
                // 1 TOTALSIZE
                int size = sbr.getByteBuffer().getInt();
                return this.commitLog.getMessage(commitLogOffset, size);
            } finally {
                sbr.release();
            }
        }

        return null;
    }

    @Override public SelectMappedBufferResult selectOneMessageByOffset(long commitLogOffset, int msgSize) {
        return this.commitLog.getMessage(commitLogOffset, msgSize);
    }

    @Override public String getRunningDataInfo() {
        return null;
    }

    @Override public HashMap<String, String> getRuntimeInfo() {
        return null;
    }

    @Override public long getMaxPhyOffset() {
        return this.commitLog.getMaxOffset();
    }

    @Override public long getMinPhyOffset() {
        return this.commitLog.getMinOffset();
    }

    @Override public long getEarliestMessageTime(String topic, int queueId) {
        return 0;
    }

    @Override public long getEarliestMessageTime() {
        return 0;
    }

    @Override public long getMessageStoreTimeStamp(String topic, int queueId, long consumeQueueOffset) {
        ReputConsumeQueue logicQueue = this.findConsumeQueue(topic, queueId);
        if (logicQueue != null) {
            SelectMappedBufferResult result = logicQueue.getIndexBuffer(consumeQueueOffset);
            return getStoreTime(result);
        }

        return -1;
    }

    @Override public long getMessageTotalInQueue(String topic, int queueId) {
        return 0;
    }

    @Override public SelectMappedBufferResult getCommitLogData(long offset) {
        return null;
    }

    @Override public boolean appendToCommitLog(long startOffset, byte[] data) {
        return false;
    }

    @Override public void executeDeleteFilesManually() {

    }

    @Override public QueryMessageResult queryMessage(String topic, String key, int maxNum, long begin, long end) {
        QueryMessageResult queryMessageResult = new QueryMessageResult();

        long lastQueryMsgTime = end;

        for (int i = 0; i < 3; i++) {
            QueryOffsetResult queryOffsetResult = this.indexService.queryOffset(topic, key, maxNum, begin, lastQueryMsgTime);//在索引中根据时间，比对key找到该消息的偏移
            if (queryOffsetResult.getPhyOffsets().isEmpty()) {
                break;
            }

            Collections.sort(queryOffsetResult.getPhyOffsets());

            queryMessageResult.setIndexLastUpdatePhyoffset(queryOffsetResult.getIndexLastUpdatePhyoffset());
            queryMessageResult.setIndexLastUpdateTimestamp(queryOffsetResult.getIndexLastUpdateTimestamp());

            for (int m = 0; m < queryOffsetResult.getPhyOffsets().size(); m++) {
                long offset = queryOffsetResult.getPhyOffsets().get(m);

                try {

                    boolean match = true;
                    MessageExt msg = this.lookMessageByOffset(offset);
                    if (0 == m) {
                        lastQueryMsgTime = msg.getStoreTimestamp();
                    }
                    if (match) {
                        SelectMappedBufferResult result = this.commitLog.getData(offset, false);
                        if (result != null) {
                            int size = result.getByteBuffer().getInt(0);
                            result.getByteBuffer().limit(size);
                            result.setSize(size);
                            queryMessageResult.addMessage(result);
                        }
                    } else {
                        log.warn("queryMessage hash duplicate, {} {}", topic, key);
                    }
                } catch (Exception e) {
                    log.error("queryMessage exception", e);
                }
            }

            if (queryMessageResult.getBufferTotalSize() > 0) {
                break;
            }

            if (lastQueryMsgTime < begin) {
                break;
            }
        }

        return queryMessageResult;
    }

    @Override public void updateHaMasterAddress(String newAddr) {

    }

    @Override public long slaveFallBehindMuch() {
        return 0;
    }

    @Override public long now() {
        return this.systemClock.now();
    }

    @Override public int cleanUnusedTopic(Set<String> topics) {
        return 0;
    }

    @Override public void cleanExpiredConsumerQueue() {

    }

    @Override public boolean checkInDiskByConsumeOffset(String topic, int queueId, long consumeOffset) {
        return false;
    }

    @Override public long dispatchBehindBytes() {
        return 0;
    }

    @Override public long flush() {
        return 0;
    }

    @Override public boolean resetWriteOffset(long phyOffset) {
        return false;
    }

    @Override public long getConfirmOffset() {
        return 0;
    }

    @Override public void setConfirmOffset(long phyOffset) {

    }

    @Override public boolean isOSPageCacheBusy() {
        return false;
    }

    @Override public long lockTimeMills() {
        return 0;
    }

    @Override public boolean isTransientStorePoolDeficient() {
        return false;
    }

    @Override public LinkedList<CommitLogDispatcher> getDispatcherList() {
        return null;
    }

    @Override public ConsumeQueue getConsumeQueue(String topic, int queueId) {
        return null;
    }

    @Override public BrokerStatsManager getBrokerStatsManager() {
        return null;
    }

    @Override public void handleScheduleMessageService(BrokerRole brokerRole) {

    }

    public GetMessageResult getMessage(String topic, int queueId, long offset, int maxMsgNums) {
        if (this.shutdown) {
            log.warn("message store has shutdown, so getMessage is forbidden");
            return null;
        }
        GetMessageStatus status = GetMessageStatus.NO_MESSAGE_IN_QUEUE;
        long nextBeginOffset = offset;
        long minOffset = 0;
        long maxOffset = 0;
        GetMessageResult getResult = new GetMessageResult();

        ReputConsumeQueue consumeQueue = findConsumeQueue(topic, queueId);
        if (consumeQueue != null) {
            minOffset = consumeQueue.getMinOffsetInQueue();
            maxOffset = consumeQueue.getMaxOffsetInQueue();
            if (maxOffset == 0) {
                status = GetMessageStatus.NO_MESSAGE_IN_QUEUE;
                nextBeginOffset = 0;
            } else if (offset < minOffset) {
                status = GetMessageStatus.OFFSET_TOO_SMALL;
                nextBeginOffset = minOffset;
            } else if (offset == maxOffset) {
                status = GetMessageStatus.OFFSET_OVERFLOW_ONE;
                nextBeginOffset = offset;
            } else if (offset > maxOffset) {
                status = GetMessageStatus.OFFSET_OVERFLOW_BADLY;
                if (0 == minOffset) {
                    nextBeginOffset = minOffset;
                } else {
                    nextBeginOffset = maxOffset;
                }
            } else {
                SelectMappedBufferResult bufferConsumeQueue = consumeQueue.getIndexBuffer(offset);
                if (bufferConsumeQueue != null) {

                    try {
                        status = GetMessageStatus.NO_MATCHED_MESSAGE;

                        long nextPhyFileStartOffset = Long.MIN_VALUE;

                        int i = 0;
                        final int maxFilterMessageCount = Math.max(16000, maxMsgNums * ConsumeQueue.CQ_STORE_UNIT_SIZE);
                        for (; i < bufferConsumeQueue.getSize() && i < maxFilterMessageCount; i += ConsumeQueue.CQ_STORE_UNIT_SIZE) {
                            long offsetPy = bufferConsumeQueue.getByteBuffer().getLong();
                            int sizePy = bufferConsumeQueue.getByteBuffer().getInt();
                            long tagsCode = bufferConsumeQueue.getByteBuffer().getLong();

                            if (nextPhyFileStartOffset != Long.MIN_VALUE) {
                                if (offsetPy < nextPhyFileStartOffset)
                                    continue;
                            }

                            if (this.isTheBatchFull(maxMsgNums, getResult.getBufferTotalSize(), getResult.getMessageCount())) {
                                break;
                            }

                            SelectMappedBufferResult selectResult = this.commitLog.getMessage(offsetPy, sizePy);
                            if (null == selectResult) {
                                if (getResult.getBufferTotalSize() == 0) {
                                    status = GetMessageStatus.MESSAGE_WAS_REMOVING;
                                }

                                nextPhyFileStartOffset = this.commitLog.rollNextFile(offsetPy);
                                continue;
                            }

                            getResult.addMessage(selectResult);
                            status = GetMessageStatus.FOUND;
                            nextPhyFileStartOffset = Long.MIN_VALUE;
                        }

                        nextBeginOffset = offset + (i / ConsumeQueue.CQ_STORE_UNIT_SIZE);
                    } finally {

                        bufferConsumeQueue.release();
                    }

                } else {
                    status = GetMessageStatus.OFFSET_FOUND_NULL;
                    nextBeginOffset = consumeQueue.rollNextFile(offset);
                    log.warn("consumer request topic: " + topic + "offset: " + offset + " minOffset: " + minOffset + " maxOffset: "
                        + maxOffset + ", but access logic queue failed.");
                }
            }
        }

        getResult.setStatus(status);
        getResult.setNextBeginOffset(nextBeginOffset);
        getResult.setMaxOffset(maxOffset);
        getResult.setMinOffset(minOffset);
        return getResult;
    }

    private boolean isTheBatchFull(int maxMsgNums, int bufferTotal, int messageTotal) {

        if (0 == bufferTotal || 0 == messageTotal) {
            return false;
        }

        if (maxMsgNums <= messageTotal) {
            return true;
        }
        return false;
    }

    private long getStoreTime(SelectMappedBufferResult result) {
        if (result != null) {
            try {
                final long phyOffset = result.getByteBuffer().getLong();
                final int size = result.getByteBuffer().getInt();
                long storeTime = this.getCommitLog().pickupStoreTimestamp(phyOffset, size);
                return storeTime;
            } catch (Exception e) {
            } finally {
                result.release();
            }
        }
        return -1;
    }

    public void recoverTopicQueueTable() {
        HashMap<String/* topic-queueid */, Long/* offset */> table = new HashMap<String, Long>(1024);
        long minPhyOffset = this.commitLog.getMinOffset();
        for (ConcurrentMap<Integer, ReputConsumeQueue> maps : this.consumeQueueTable.values()) {
            for (ReputConsumeQueue logic : maps.values()) {
                String key = logic.getTopic() + "-" + logic.getQueueId();
                table.put(key, logic.getMaxOffsetInQueue());
                logic.correctMinOffset(minPhyOffset);
            }
        }

        this.commitLog.setTopicQueueTable(table);
    }

    public void truncateDirtyLogicFiles(long phyOffset) {
        ConcurrentMap<String, ConcurrentMap<Integer, ReputConsumeQueue>> tables = this.consumeQueueTable;

        for (ConcurrentMap<Integer, ReputConsumeQueue> maps : tables.values()) {
            for (ReputConsumeQueue logic : maps.values()) {
                logic.truncateDirtyLogicFiles(phyOffset);
            }
        }
    }

    public void destroyLogics() {
        for (ConcurrentMap<Integer, ReputConsumeQueue> maps : this.consumeQueueTable.values()) {
            for (ReputConsumeQueue logic : maps.values()) {
                logic.destroy();
            }
        }
    }

    public void putMessagePositionInfo(DispatchRequest dispatchRequest) {
        ReputConsumeQueue cq = this.findConsumeQueue(dispatchRequest.getTopic(), dispatchRequest.getQueueId());
        cq.putMessagePositionInfoWrapper(dispatchRequest);
    }

    public ReputConsumeQueue findConsumeQueue(String topic, int queueId) {
        ConcurrentMap<Integer, ReputConsumeQueue> map = consumeQueueTable.get(topic);
        if (null == map) {
            ConcurrentMap<Integer, ReputConsumeQueue> newMap = new ConcurrentHashMap<Integer, ReputConsumeQueue>(128);
            ConcurrentMap<Integer, ReputConsumeQueue> oldMap = consumeQueueTable.putIfAbsent(topic, newMap);
            if (oldMap != null) {
                map = oldMap;
            } else {
                map = newMap;
            }
        }

        ReputConsumeQueue logic = map.get(queueId);
        if (null == logic) {
            ReputConsumeQueue newLogic = new ReputConsumeQueue(
                topic,
                queueId,
                StorePathConfigHelper.getStorePathConsumeQueue(this.messageStoreConfig.getStorePathRootDir()),
                this.getMessageStoreConfig().getMappedFileSizeConsumeQueue(),
                this);
            ReputConsumeQueue oldLogic = map.putIfAbsent(queueId, newLogic);
            if (oldLogic != null) {
                logic = oldLogic;
            } else {
                logic = newLogic;
            }
        }

        return logic;
    }

    public void doDispatch(DispatchRequest req) {
        for (CommitLogDispatcher dispatcher : this.dispatcherList) {
            dispatcher.dispatch(req);
        }
    }

    public Set<Integer> getMessageQueueIdSet(String topic) {
        return consumeQueueTable.get(topic).keySet();
    }

    public MessageExt queryMessageByMsgId(String topic, String msgId) {
        try {
            MessageId messageId = MessageDecoder.decodeMessageId(msgId);
            SelectMappedBufferResult bufferResult = this.selectOneMessageByOffset(messageId.getOffset());
            try {
                ByteBuffer buffer = bufferResult.getByteBuffer();
                MessageExt messageExt = MessageDecoder.decode(buffer);
                if (messageExt != null) {
                    return messageExt;
                }
            }finally {
                if (bufferResult != null) {
                    bufferResult.release();
                }
            }
        }catch (Exception ignore) {

        }
        QueryMessageResult result = this.queryMessage(topic, msgId, 32,
            MessageClientIDSetter.getNearlyTimeFromID(msgId).getTime() - 1000, Long.MAX_VALUE);
        List<MessageExt> messageExtList = decodes(result);
        if (messageExtList != null && !messageExtList.isEmpty()) {
            return messageExtList.get(0);
        }
        return null;
    }

    public List<MessageExt> queryMessageByKey(String topic, String key, int maxNum) {
        QueryMessageResult result = this.queryMessage(topic, key, maxNum, 0, System.currentTimeMillis());
        return decodes(result);
    }

    private List<MessageExt> decodes(final QueryMessageResult result) {
        if (result.getBufferTotalSize() == 0) {
            return null;
        }

        final ByteBuffer buffer = ByteBuffer.allocate(result.getBufferTotalSize());
        try {
            result.getMessageBufferList().forEach(b -> buffer.put(b));
        } finally {
            result.release();
        }
        buffer.flip();
        return MessageDecoder.decodes(buffer);
    }

    private boolean loadConsumeQueue() {
        File dirLogic = new File(StorePathConfigHelper.getStorePathConsumeQueue(this.messageStoreConfig.getStorePathRootDir()));
        File[] fileTopicList = dirLogic.listFiles();
        if (fileTopicList != null) {

            for (File fileTopic : fileTopicList) {
                String topic = fileTopic.getName();

                File[] fileQueueIdList = fileTopic.listFiles();
                if (fileQueueIdList != null) {
                    for (File fileQueueId : fileQueueIdList) {
                        int queueId;
                        try {
                            queueId = Integer.parseInt(fileQueueId.getName());
                        } catch (NumberFormatException e) {
                            continue;
                        }
                        ReputConsumeQueue logic = new ReputConsumeQueue(
                            topic,
                            queueId,
                            StorePathConfigHelper.getStorePathConsumeQueue(this.messageStoreConfig.getStorePathRootDir()),
                            this.getMessageStoreConfig().getMappedFileSizeConsumeQueue(),
                            this);
                        this.putConsumeQueue(topic, queueId, logic);
                        if (!logic.load()) {
                            return false;
                        }
                    }
                }
            }
        }

        log.info("load logics queue all over, OK");

        return true;
    }

    private void putConsumeQueue(final String topic, final int queueId, final ReputConsumeQueue consumeQueue) {
        ConcurrentMap<Integer/* queueId */, ReputConsumeQueue> map = this.consumeQueueTable.get(topic);
        if (null == map) {
            map = new ConcurrentHashMap<Integer/* queueId */, ReputConsumeQueue>();
            map.put(queueId, consumeQueue);
            this.consumeQueueTable.put(topic, map);
        } else {
            map.put(queueId, consumeQueue);
        }
    }

    private void recover() {
        long maxPhyOffsetOfConsumeQueue = this.recoverConsumeQueue();

        this.commitLog.recoverNormally(maxPhyOffsetOfConsumeQueue);

        this.recoverTopicQueueTable();
    }

    private long recoverConsumeQueue() {
        long maxPhysicOffset = -1;
        for (ConcurrentMap<Integer, ReputConsumeQueue> maps : this.consumeQueueTable.values()) {
            for (ReputConsumeQueue logic : maps.values()) {
                logic.recover();
                if (logic.getMaxPhysicOffset() > maxPhysicOffset) {
                    maxPhysicOffset = logic.getMaxPhysicOffset();
                }
            }
        }

        return maxPhysicOffset;
    }

    private void addScheduleTask() {

        // 定时删除过期文件：commitlog和consumer queue
        this.scheduledExecutorService.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                ReputMessageStore.this.cleanFilesPeriodically();
            }
        }, 1000 * 60, this.messageStoreConfig.getCleanResourceInterval(), TimeUnit.MILLISECONDS);

        // 检查磁盘空间是不是快满了，也就是commitlog所在分区的空间使用率，默认达到90%就认为满了，这里只是检查并打印相关日志
        this.diskCheckScheduledExecutorService.scheduleAtFixedRate(new Runnable() {
            public void run() {
                ReputMessageStore.this.cleanCommitLogService.isSpaceFull();
            }
        }, 1000L, 10000L, TimeUnit.MILLISECONDS);
    }

    private void cleanFilesPeriodically() {
        this.cleanCommitLogService.run();
        this.cleanConsumeQueueService.run();
    }

    private String getStorePathPhysic() {
        return ReputMessageStore.this.getMessageStoreConfig().getStorePathCommitLog();
    }

    class CommitLogDispatcherBuildConsumeQueue implements CommitLogDispatcher {

        @Override
        public void dispatch(DispatchRequest request) {
            final int tranType = MessageSysFlag.getTransactionValue(request.getSysFlag());
            switch (tranType) {
                case MessageSysFlag.TRANSACTION_NOT_TYPE:
                case MessageSysFlag.TRANSACTION_COMMIT_TYPE:
                    ReputMessageStore.this.putMessagePositionInfo(request);
                    break;
                case MessageSysFlag.TRANSACTION_PREPARED_TYPE:
                case MessageSysFlag.TRANSACTION_ROLLBACK_TYPE:
                    break;
            }
        }
    }

    class CommitLogDispatcherBuildIndex implements CommitLogDispatcher {

        @Override
        public void dispatch(DispatchRequest request) {
            if (ReputMessageStore.this.messageStoreConfig.isMessageIndexEnable()) {
                ReputMessageStore.this.indexService.buildIndex(request);
            }
        }
    }

    class ReputMessageService extends ServiceThread {

        private volatile long reputFromOffset = 0;

        public long getReputFromOffset() {
            return reputFromOffset;
        }

        public void setReputFromOffset(long reputFromOffset) {
            this.reputFromOffset = reputFromOffset;
        }

        @Override
        public void shutdown() {
            for (int i = 0; i < 50 && this.isCommitLogAvailable(); i++) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ignored) {
                }
            }

            if (this.isCommitLogAvailable()) {
                log.warn("shutdown ReputMessageService, but commitlog have not finish to be dispatched, CL: {} reputFromOffset: {}",
                    ReputMessageStore.this.commitLog.getMaxOffset(), this.reputFromOffset);
            }

            super.shutdown();
        }

        public long behind() {
            return ReputMessageStore.this.commitLog.getMaxOffset() - this.reputFromOffset;
        }

        private boolean isCommitLogAvailable() {
            return this.reputFromOffset < ReputMessageStore.this.commitLog.getMaxOffset();
        }

        private void doReput() {
            // 逻辑队列的最大偏移小于当前commitlog的最小偏移，说明消息滞后太多，只能从commitlog最小偏移开始，中间那部分消息肯定都没了，不过也不重要了，因为commitlog都没了
            if (this.reputFromOffset < ReputMessageStore.this.commitLog.getMinOffset()) {
                log.warn("The reputFromOffset={} is smaller than minPyOffset={}, this usually indicate that the dispatch behind too much and the commitlog has expired.",
                    this.reputFromOffset, ReputMessageStore.this.commitLog.getMinOffset());
                this.reputFromOffset = ReputMessageStore.this.commitLog.getMinOffset();
            }
            for (boolean doNext = true; this.isCommitLogAvailable() && doNext; ) {   // this.reputFromOffset < DefaultMessageStore.this.commitLog.getMaxOffset()

                if (ReputMessageStore.this.getMessageStoreConfig().isDuplicationEnable() // 是否允许重复复制，默认false
                    && this.reputFromOffset >= ReputMessageStore.this.getConfirmOffset()) {
                    break;
                }

                // 根据reputFromOffset找到对应的MappedFile，并返回对应的尚未分发的数据
                SelectMappedBufferResult result = ReputMessageStore.this.commitLog.getData(reputFromOffset);
                if (result != null) {
                    try {
                        this.reputFromOffset = result.getStartOffset();// 这里重新赋值是因为，可能reputFromOffset太小的，逻辑队列滞后太多，commitlog里之前的那部分数据已经清，从当前最小物理位置开始

                        for (int readSize = 0; readSize < result.getSize() && doNext; ) { // 依次读取每条消息请求分发
                            DispatchRequest dispatchRequest =
                                ReputMessageStore.this.commitLog.checkMessageAndReturnSize(result.getByteBuffer(), false, false);
                            int size = dispatchRequest.getBufferSize() == -1 ? dispatchRequest.getMsgSize() : dispatchRequest.getBufferSize();

                            if (dispatchRequest.isSuccess()) {
                                if (size > 0) {
                                    // 分发消息，构建到消费队列和索引里
                                    ReputMessageStore.this.doDispatch(dispatchRequest);
                                    this.reputFromOffset += size;
                                    readSize += size;
                                } else if (size == 0) {
                                    this.reputFromOffset = ReputMessageStore.this.commitLog.rollNextFile(this.reputFromOffset);
                                    readSize = result.getSize();
                                }
                            } else if (!dispatchRequest.isSuccess()) {

                                if (size > 0) {
                                    log.error("[BUG]read total count not equals msg total size. reputFromOffset={}", reputFromOffset);
                                    this.reputFromOffset += size;
                                } else {
                                    doNext = false;
                                    // If user open the dledger pattern or the broker is master node,
                                    // it will not ignore the exception and fix the reputFromOffset variable
                                    if (ReputMessageStore.this.getMessageStoreConfig().isEnableDLegerCommitLog() ||
                                        ReputMessageStore.this.brokerConfig.getBrokerId() == MixAll.MASTER_ID) {
                                        log.error("[BUG]dispatch message to consume queue error, COMMITLOG OFFSET: {}",
                                            this.reputFromOffset);
                                        this.reputFromOffset += result.getSize() - readSize;
                                    }
                                }
                            }
                        }
                    } finally {
                        result.release();
                    }
                } else {
                    doNext = false;
                }
            }
        }

        @Override
        public void run() {
            log.info(this.getServiceName() + " service started");

            while (!this.isStopped()) {
                try {
                    Thread.sleep(1);
                    this.doReput();
                } catch (Exception e) {
                    log.warn(this.getServiceName() + " service has exception. ", e);
                }
            }

            log.info(this.getServiceName() + " service end");
        }

        @Override
        public String getServiceName() {
            return ReputMessageService.class.getSimpleName();
        }

    }

    class FlushConsumeQueueService extends ServiceThread {
        private static final int RETRY_TIMES_OVER = 3;
        private long lastFlushTimestamp = 0;

        private void doFlush(int retryTimes) {
            int flushConsumeQueueLeastPages = ReputMessageStore.this.getMessageStoreConfig().getFlushConsumeQueueLeastPages();

            if (retryTimes == RETRY_TIMES_OVER) {
                flushConsumeQueueLeastPages = 0;
            }

            int flushConsumeQueueThoroughInterval = ReputMessageStore.this.getMessageStoreConfig().getFlushConsumeQueueThoroughInterval();
            long currentTimeMillis = System.currentTimeMillis();
            if (currentTimeMillis >= (this.lastFlushTimestamp + flushConsumeQueueThoroughInterval)) {
                this.lastFlushTimestamp = currentTimeMillis;
                flushConsumeQueueLeastPages = 0;
            }

            ConcurrentMap<String, ConcurrentMap<Integer, ReputConsumeQueue>> tables = ReputMessageStore.this.consumeQueueTable;

            for (ConcurrentMap<Integer, ReputConsumeQueue> maps : tables.values()) {
                for (ReputConsumeQueue cq : maps.values()) {
                    boolean result = false;
                    for (int i = 0; i < retryTimes && !result; i++) {
                        result = cq.flush(flushConsumeQueueLeastPages);
                    }
                }
            }
        }

        public void run() {
            log.info(this.getServiceName() + " service started");

            while (!this.isStopped()) {
                try {
                    int interval = ReputMessageStore.this.getMessageStoreConfig().getFlushIntervalConsumeQueue();
                    this.waitForRunning(interval);
                    this.doFlush(1);
                } catch (Exception e) {
                    log.warn(this.getServiceName() + " service has exception. ", e);
                }
            }

            this.doFlush(RETRY_TIMES_OVER);

            log.info(this.getServiceName() + " service end");
        }

        @Override
        public String getServiceName() {
            return FlushConsumeQueueService.class.getSimpleName();
        }

        @Override
        public long getJointime() {
            return 1000 * 60;
        }
    }

    class CleanCommitLogService {

        private final static int MAX_MANUAL_DELETE_FILE_TIMES = 20;
        private final double diskSpaceWarningLevelRatio =
            Double.parseDouble(System.getProperty("rocketmq.broker.diskSpaceWarningLevelRatio", "0.90"));

        private final double diskSpaceCleanForciblyRatio =
            Double.parseDouble(System.getProperty("rocketmq.broker.diskSpaceCleanForciblyRatio", "0.85"));
        private long lastRedeleteTimestamp = 0;

        private volatile int manualDeleteFileSeveralTimes = 0;

        private volatile boolean cleanImmediately = false;

        public void excuteDeleteFilesManualy() {
            this.manualDeleteFileSeveralTimes = MAX_MANUAL_DELETE_FILE_TIMES;
            log.info("executeDeleteFilesManually was invoked");
        }

        public void run() {
            try {
                this.deleteExpiredFiles();

                this.redeleteHangedFile();
            } catch (Throwable e) {
                log.warn(this.getServiceName() + " service has exception. ", e);
            }
        }

        private void deleteExpiredFiles() {
            int deleteCount = 0;
            long fileReservedTime = ReputMessageStore.this.getMessageStoreConfig().getFileReservedTime();
            int deletePhysicFilesInterval = ReputMessageStore.this.getMessageStoreConfig().getDeleteCommitLogFilesInterval();
            int destroyMapedFileIntervalForcibly = ReputMessageStore.this.getMessageStoreConfig().getDestroyMapedFileIntervalForcibly();

            boolean timeup = this.isTimeToDelete();
            boolean spacefull = this.isSpaceToDelete();
            boolean manualDelete = this.manualDeleteFileSeveralTimes > 0;

            if (timeup || spacefull || manualDelete) {

                if (manualDelete)
                    this.manualDeleteFileSeveralTimes--;

                boolean cleanAtOnce = ReputMessageStore.this.getMessageStoreConfig().isCleanFileForciblyEnable() && this.cleanImmediately;

                log.info("begin to delete before {} hours file. timeup: {} spacefull: {} manualDeleteFileSeveralTimes: {} cleanAtOnce: {}",
                    fileReservedTime,
                    timeup,
                    spacefull,
                    manualDeleteFileSeveralTimes,
                    cleanAtOnce);

                fileReservedTime *= 60 * 60 * 1000;

                deleteCount = ReputMessageStore.this.commitLog.deleteExpiredFile(fileReservedTime, deletePhysicFilesInterval,
                    destroyMapedFileIntervalForcibly, cleanAtOnce);
                if (deleteCount > 0) {
                    log.info("delete expired files: {}", deleteCount);
                } else if (spacefull) {
                    log.warn("disk space will be full soon, but delete file failed.");
                }
            }
        }

        private void redeleteHangedFile() {
            int interval = ReputMessageStore.this.getMessageStoreConfig().getRedeleteHangedFileInterval();
            long currentTimestamp = System.currentTimeMillis();
            if ((currentTimestamp - this.lastRedeleteTimestamp) > interval) {
                this.lastRedeleteTimestamp = currentTimestamp;
                int destroyMapedFileIntervalForcibly =
                    ReputMessageStore.this.getMessageStoreConfig().getDestroyMapedFileIntervalForcibly();
                if (ReputMessageStore.this.commitLog.retryDeleteFirstFile(destroyMapedFileIntervalForcibly)) {
                }
            }
        }

        public String getServiceName() {
            return CleanCommitLogService.class.getSimpleName();
        }

        private boolean isTimeToDelete() {
            String when = ReputMessageStore.this.getMessageStoreConfig().getDeleteWhen();
            if (UtilAll.isItTimeToDo(when)) {
                log.info("it's time to reclaim disk space, " + when);
                return true;
            }

            return false;
        }

        private boolean isSpaceToDelete() {
            double ratio = ReputMessageStore.this.getMessageStoreConfig().getDiskMaxUsedSpaceRatio() / 100.0;

            cleanImmediately = false;

            {
                double physicRatio = UtilAll.getDiskPartitionSpaceUsedPercent(getStorePathPhysic());
                if (physicRatio > diskSpaceWarningLevelRatio) {
                    log.error("physic disk maybe full soon " + physicRatio + ", so mark disk full");
                    cleanImmediately = true;
                } else if (physicRatio > diskSpaceCleanForciblyRatio) {
                    cleanImmediately = true;
                }

                if (physicRatio < 0 || physicRatio > ratio) {
                    log.info("physic disk maybe full soon, so reclaim space, " + physicRatio);
                    return true;
                }
            }
            return false;
        }

        public int getManualDeleteFileSeveralTimes() {
            return manualDeleteFileSeveralTimes;
        }

        public void setManualDeleteFileSeveralTimes(int manualDeleteFileSeveralTimes) {
            this.manualDeleteFileSeveralTimes = manualDeleteFileSeveralTimes;
        }

        public boolean isSpaceFull() {
            double physicRatio = UtilAll.getDiskPartitionSpaceUsedPercent(getStorePathPhysic());
            double ratio = ReputMessageStore.this.getMessageStoreConfig().getDiskMaxUsedSpaceRatio() / 100.0;
            if (physicRatio > ratio) {
                log.info("physic disk of commitLog used: " + physicRatio);
            }
            if (physicRatio > this.diskSpaceWarningLevelRatio) {
                log.error("physic disk of commitLog maybe full soon, used " + physicRatio + ", so mark disk full");

                return true;
            } else {
                return false;
            }
        }
    }

    class CleanConsumeQueueService {
        private long lastPhysicalMinOffset = 0;

        public void run() {
            try {
                this.deleteExpiredFiles();
            } catch (Throwable e) {
                log.warn(this.getServiceName() + " service has exception. ", e);
            }
        }

        private void deleteExpiredFiles() {
            int deleteLogicsFilesInterval = ReputMessageStore.this.getMessageStoreConfig().getDeleteConsumeQueueFilesInterval();

            long minOffset = ReputMessageStore.this.commitLog.getMinOffset();
            if (minOffset > this.lastPhysicalMinOffset) {
                this.lastPhysicalMinOffset = minOffset;

                ConcurrentMap<String, ConcurrentMap<Integer, ReputConsumeQueue>> tables = ReputMessageStore.this.consumeQueueTable;

                for (ConcurrentMap<Integer, ReputConsumeQueue> maps : tables.values()) {
                    for (ReputConsumeQueue logic : maps.values()) {
                        int deleteCount = logic.deleteExpiredFile(minOffset);

                        if (deleteCount > 0 && deleteLogicsFilesInterval > 0) {
                            try {
                                Thread.sleep(deleteLogicsFilesInterval);
                            } catch (InterruptedException ignored) {
                            }
                        }
                    }
                }

                ReputMessageStore.this.indexService.deleteExpiredFile(minOffset);
            }
        }

        public String getServiceName() {
            return CleanConsumeQueueService.class.getSimpleName();
        }
    }
}
