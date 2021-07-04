#!/bin/bash
# 不要修改进程标记，作为进程属性关闭使用
PROCESS_FLAG="rocketmq-reput-process-flag"
pkill -f $PROCESS_FLAG
echo 'Stop Rocketmq-reput!'