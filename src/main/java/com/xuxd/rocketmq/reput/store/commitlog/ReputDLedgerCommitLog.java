package com.xuxd.rocketmq.reput.store.commitlog;

import com.xuxd.rocketmq.reput.store.ReputMessageStore;
import io.openmessaging.storage.dledger.entry.DLedgerEntry;
import io.openmessaging.storage.dledger.store.file.MmapFileList;
import java.nio.ByteBuffer;
import org.apache.rocketmq.store.DispatchRequest;

/**
 * daily-demo.
 *
 * @author xuxd
 * @date 2021-06-28 15:07:31
 * @description daily-demo
 **/
public class ReputDLedgerCommitLog extends ReputCommitLog {
    public ReputDLedgerCommitLog(ReputMessageStore messageStore) {
        super(messageStore);
    }

    public DispatchRequest checkMessageAndReturnSize(ByteBuffer byteBuffer, final boolean checkCRC) {
        return this.checkMessageAndReturnSize(byteBuffer, checkCRC, true);
    }

    @Override
    public DispatchRequest checkMessageAndReturnSize(ByteBuffer byteBuffer, final boolean checkCRC,
        final boolean readBody) {
        try {
            int bodyOffset = DLedgerEntry.BODY_OFFSET;
            int pos = byteBuffer.position();
            int magic = byteBuffer.getInt();
            //In dledger, this field is size, it must be gt 0, so it could prevent collision
            int magicOld = byteBuffer.getInt();
            if (magicOld == ReputCommitLog.BLANK_MAGIC_CODE || magicOld == ReputCommitLog.MESSAGE_MAGIC_CODE) {
                byteBuffer.position(pos);
                return super.checkMessageAndReturnSize(byteBuffer, checkCRC, readBody);
            }
            if (magic == MmapFileList.BLANK_MAGIC_CODE) {
                return new DispatchRequest(0, true);
            }
            byteBuffer.position(pos + bodyOffset);//写的时候加了个bodyOffset，到底用来干什么，其它的都没区别
            DispatchRequest dispatchRequest = super.checkMessageAndReturnSize(byteBuffer, checkCRC, readBody);
            if (dispatchRequest.isSuccess()) {
                dispatchRequest.setBufferSize(dispatchRequest.getMsgSize() + bodyOffset);
            } else if (dispatchRequest.getMsgSize() > 0) {
                dispatchRequest.setBufferSize(dispatchRequest.getMsgSize() + bodyOffset);
            }
            return dispatchRequest;
        } catch (Throwable ignored) {
        }

        return new DispatchRequest(-1, false /* success */);
    }
}
