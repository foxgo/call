# Call Center

生产级通话完成后处理系统工程骨架，包含：

- `call-common`：共享模型、分片路由、MyBatis-Plus、ES 初始化能力
- `call-iam`：统一身份中心，提供认证、租户、用户、角色、部门与审计接口
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
- `call-iam`: `http://localhost:8085/actuator/health`
- Grafana: `http://localhost:3000`
- Kibana: `http://localhost:5601`

## 模块结构

```text
call/
├── call-common
├── call-iam
├── call-ingestion
├── call-search
├── call-ops
├── deploy
└── docs
```

## 说明

- 该工程优先提供生产级骨架、关键配置与核心扩展点。
- 当前消息体采用 JSON DTO，后续可无缝替换为 Protobuf 反序列化器。
- `call-ingestion` 当前通过 RocketMQ nameserver 地址 `ROCKETMQ_NAME_SERVER` 接入消息队列。
- ES 索引模板、ILM 与别名由应用启动时自动初始化。
- `call-iam` 默认端口为 `8085`，依赖 `MYSQL_HOST`、`MYSQL_PORT`、`MYSQL_USER`、`MYSQL_PASSWORD`、`REDIS_HOST`、`ROCKETMQ_NAME_SERVER`、`ES_URI`、`ES_USERNAME`、`ES_PASSWORD`。
- 当 `CALL_LLM_ENABLED=true` 时，`call-ingestion` 会把后处理链路切成
  `call_record_persisted -> LLM 分析落库 -> call_record_analysis_completed -> ES / 第三方推送`。
- LLM 结果保存在 `call_analysis_result`，当分析失败达到 RocketMQ 最大重试次数后，会按 `DEGRADED` 结果继续放行下游。
- 新增环境变量 `CALL_RECORD_ANALYSIS_COMPLETED_TOPIC`，默认 topic 为 `call_record_analysis_completed`。

## 部署清单

- Docker Compose: [deploy/docker-compose.yml](/Users/johnny/github/call/deploy/docker-compose.yml)
- Kubernetes:
  [deploy/k8s/call-iam-deployment.yaml](/Users/johnny/github/call/deploy/k8s/call-iam-deployment.yaml),
  [deploy/k8s/services.yaml](/Users/johnny/github/call/deploy/k8s/services.yaml),
  [deploy/k8s/configmap.yaml](/Users/johnny/github/call/deploy/k8s/configmap.yaml)
- 模块说明: [call-iam/README.md](/Users/johnny/github/call/call-iam/README.md)
