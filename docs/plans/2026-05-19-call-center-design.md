# Call Center Design

> Approved by implementation request on 2026-05-19.

## Goal

构建一个面向多租户的通话完成后处理平台，拆分为写入服务、查询服务和公共模块，支持 MySQL 分库分表、Kafka 高吞吐消费、ES 检索、重试/DLQ 和基础可观测性。

## Architecture

- `call-ingestion` 负责消费 `call_record_topic` 与 `call_round_topic`，按 `(tenant_id, year_month, table_index)` 聚合后写入 MySQL，并异步批量写入 Elasticsearch。
- `call-search` 预留查询 API，统一从租户维度注入查询隔离条件与 ES routing。
- `call-common` 提供 DTO、实体、路由算法、雪花 ID、MyBatis-Plus 动态表名、ES 初始化器、共享配置。
- `call-ops` 作为运维入口，后续用于迁移、重建索引、补偿等任务。

## Key Decisions

1. 构建工具使用 Maven，多模块结构更适合 Spring Boot 3.2 与共享 BOM 管理。
2. 先实现生产可扩展骨架与关键接口，保留部分外部系统集成扩展点，避免在空仓库里过度一次性实现。
3. Kafka 消息 DTO 暂使用 JSON 承载，接口与处理流程保持与 Protobuf 兼容。
4. 分库由 `tenant_id` 决定，分表后缀由 `phone` 哈希或 `call_id` 内嵌 shard 位决定。
5. ES 索引模板、ILM、别名通过服务启动自动初始化，Compose 部署不依赖额外人工步骤。

## Error Handling

- MySQL 写入使用批量 `INSERT IGNORE` 保证幂等。
- ES 写入失败进入重试执行器；超过次数后写入 Kafka DLQ。
- 消费端使用手动 ack，只有批处理结束后才提交 offset。

## Deployment

- 本地和单机验证使用 `docker-compose.yml`。
- 生产部署预置 `k8s/` YAML 作为容器化模板。
- 基础设施包含 MySQL、Redis、Kafka、Elasticsearch、Kibana、Prometheus、Grafana。
