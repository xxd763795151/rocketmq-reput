package com.xuxd.rocketmq.reput.controller;

import com.xuxd.rocketmq.reput.server.QueryMessageService;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.apache.rocketmq.common.message.MessageExt;
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

    private QueryMessageService messageService;

    /**
     * 返回消息列表，不包含消息体
     *
     * @param topic     topic
     * @param startTime 查询开始时间
     * @param endTime   查询结束时间
     * @return 没有消息体的消息列表
     */
    public List<MessageExt> viewMessageList(String topic, long startTime, long endTime) {

        return null;
    }
}
