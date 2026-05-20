# Call Center

生产级通话完成后处理系统工程骨架，包含：

- `call-common`：共享模型、分片路由、MyBatis-Plus、ES 初始化能力
- `call-ingestion`：RocketMQ 消费、MySQL 持久化、ES 写入、重试与 DLQ
- `call-search`：查询接口预留与基础设施
- `call-ops`：运维入口

## 快速开始

1. 构建服务镜像

```bash
docker compose build
```

2. 启动基础设施与服务

```bash
docker compose up -d
```

3. 查看服务

- `call-ingestion`: `http://localhost:8081/actuator/health`
- `call-search`: `http://localhost:8082/actuator/health`
- Grafana: `http://localhost:3000`
- Kibana: `http://localhost:5601`

## 模块结构

```text
call/
├── call-common
├── call-ingestion
├── call-search
├── call-ops
├── docker
├── docs
└── k8s
```

## 说明

- 该工程优先提供生产级骨架、关键配置与核心扩展点。
- 当前消息体采用 JSON DTO，后续可无缝替换为 Protobuf 反序列化器。
- `call-ingestion` 当前通过 RocketMQ nameserver 地址 `ROCKETMQ_NAME_SERVER` 接入消息队列。
- ES 索引模板、ILM 与别名由应用启动时自动初始化。
