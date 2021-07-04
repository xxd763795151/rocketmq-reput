# rocketmq-reput
rocketmq数据无限持久化备份解决方案：过期消息备份、检索、重新消费
# 使用
## 部署
1. 打包： `sh package.sh`  
2. 上传target/rocketmq-reput.tar.gz到服务器并解压
## 目录结构
> --rocketmq-reput/  
> ----config/  
> ----bin/  
> ----lib/  
> ----logs/  
* config: 配置目录  
* bin: 启停脚本  
## 模式
### 客户端
部署在rocketmq的broker节点上，上传备份数据，修改如下几项配置：  
```
# rocketmq reput config
rocketmq:
  reput:
    # 启动模式：三个选项：CLIENT,SERVER,MIXED(CLIENT+SERVER)
    startMode: CLIENT
    # CLIENT 模式的配置 
    client:
      # 连接SERVER模式启动的地址
      serverAddr: 'http://localhost:9966'
      # rocketmq 的commitlog 位置
      commitlog:
        # 配置broker名称及该broker的commit log的存储目录
        broker-a: '/Users/xuxd/DemoCode/java/daily-demo/rocketmq-demo/rmq-home/zip'
      # 扫描间隔，单位分钟
      scanInterval: 1
      # 过滤文件大小，MB
      fileFilterSize: 950
      # 过期时间，单位小时
      expireTime: 48
```
### 服务器
接收备份数据，及备份消息检索
```
# rocketmq reput config
rocketmq:
  reput:
    # 启动模式：三个选项：CLIENT,SERVER,MIXED(CLIENT+SERVER)
    startMode: SERVER
    # SERVER 模式的配置 
    server:
      # 服务端文件保存时间(h)
      fileReservedTime: 48
      # 过期文件删除时间
      deleteWhen: 04
      # 是否启用Dledger模式
      enableDledger: false
      # 文件存储目录
      rootDir: ${user.dir}/rmq_home
      # 保存备份数据的地址
      store:
        # 配置broker名称及该broker的commit log的存储目录
        broker-a: '${rocketmq.reput.server.rootDir}/broker-a'
```
### 混合模式
既作客户端上传备份数据，又作服务器保存备份数据，可以把自己收到的rocketmq的数据，当过期的时候，再传输到另一个rocketmq-reput机器，继续备份，
只要有足够的主机一直备份，即使每台主机只能保存2天，30台就可以保存60天，无限期备份的解决方案
