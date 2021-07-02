package com.xuxd.rocketmq.reput.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * rocketmq-reput.
 *
 * @author xuxd
 * @date 2021-07-02 14:38:24
 **/
@RestController
@RequestMapping
public class IndexController {

    @GetMapping
    public String index() {
        return "hello, rocketmq-reput.";
    }
}
