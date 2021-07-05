package com.xuxd.rocketmq.reput.controller;

import com.xuxd.rocketmq.reput.beans.ResponseData;
import com.xuxd.rocketmq.reput.server.QueryMessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * rocketmq-reput.
 *
 * @author xuxd
 * @date 2021-07-03 17:31:20
 **/
@RestController
@RequestMapping("/message")
public class MessageController {

    @Autowired
    private QueryMessageService messageService;

    /**
     * get the total of message between startTime and endTime.
     *
     * @param topic     topic name.
     * @param startTime start time.
     * @param endTime   end time.
     * @return a long value, the total of message between startTime and endTime.
     */
    @GetMapping("/total/{topic}/{startTime}/{endTime}")
    public Object getMessageTotalByTime(@PathVariable String topic, @PathVariable long startTime,
        @PathVariable long endTime) {
        return ResponseData.create().success().data(messageService.getMessageTotalByTime(topic, startTime, endTime));
    }

    /**
     * get the message list between startTime and endTime.
     *
     * @param topic     topic name.
     * @param startTime start time.
     * @param endTime   end time.
     * @return List(MessageExt),  he message list between startTime and endTime.
     */
    @GetMapping("/list/{topic}/{startTime}/{endTime}")
    public Object getMessageByTime(@PathVariable String topic, @PathVariable long startTime,
        @PathVariable long endTime) {
        return ResponseData.create().success().data(messageService.getMessageByTime(topic, startTime, endTime));
    }

    /**
     * get the message list between startTime and endTime. It differs from the above getMessageByTime is that the
     * message body is null , as a result,  the size is smaller when return the same messages.
     *
     * @param topic     topic name.
     * @param startTime start time.
     * @param endTime   end time.
     * @return List(MessageExt),  he message list between startTime and endTime.
     */
    @GetMapping("/view/{topic}/{startTime}/{endTime}")
    public Object viewMessageList(@PathVariable String topic, @PathVariable long startTime,
        @PathVariable long endTime) {
        return ResponseData.create().success().data(messageService.viewMessageList(topic, startTime, endTime));
    }

    /**
     * get message by message id(server id(offset id) or client id(unique key)).
     *
     * @param topic topic name
     * @param msgId msg id: server id/ client id.
     * @return {@link org.apache.rocketmq.common.message.MessageExt}
     */
    @GetMapping("/id/{topic}/{msgId}")
    public Object queryMessageByMsgId(@PathVariable final String topic, @PathVariable final String msgId) {
        return ResponseData.create().success().data(messageService.queryMessageByMsgId(topic, msgId));
    }

    /**
     * get message by message key.
     *
     * @param topic topic name
     * @param key   msg key: custom business key/ client id.
     * @return {@link org.apache.rocketmq.common.message.MessageExt}
     */
    @GetMapping("/key/{topic}/{key}")
    public Object queryMessageByKey(@PathVariable final String topic, @PathVariable final String key) {
        return ResponseData.create().success().data(messageService.queryMessageByKey(topic, key));
    }
}
