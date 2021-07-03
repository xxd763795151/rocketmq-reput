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

    @GetMapping("/total/{topic}/{startTime}/{endTime}")
    public Object getMessageTotalByTime(@PathVariable String topic, @PathVariable long startTime,
        @PathVariable long endTime) {
        return ResponseData.create().success().data(messageService.getMessageTotalByTime(topic, startTime, endTime));
    }

    @GetMapping("/list/{topic}/{startTime}/{endTime}")
    public Object getMessageByTime(@PathVariable String topic, @PathVariable long startTime,
        @PathVariable long endTime) {
        return ResponseData.create().success().data(messageService.getMessageByTime(topic, startTime, endTime));
    }

    @GetMapping("/view/{topic}/{startTime}/{endTime}")
    public Object viewMessageList(@PathVariable String topic, @PathVariable long startTime,
        @PathVariable long endTime) {
        return ResponseData.create().success().data(messageService.viewMessageList(topic, startTime, endTime));
    }

    @GetMapping("/id/{topic}/{msgId}")
    public Object queryMessageByMsgId(@PathVariable final String topic, @PathVariable final String msgId) {

        return ResponseData.create().success().data(messageService.queryMessageByMsgId(topic, msgId));

    }

    @GetMapping("/key/{topic}/{key}")
    public Object queryMessageByKey(@PathVariable final String topic, @PathVariable final String key) {
        return ResponseData.create().success().data(messageService.queryMessageByKey(topic, key));
    }
}
