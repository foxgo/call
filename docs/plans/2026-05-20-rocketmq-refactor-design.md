# RocketMQ 重构设计

**日期：** 2026-05-20

**状态：** 已确认

## 问题

当前 ingestion 链路是围绕 Kafka 语义搭建的：

- Spring Kafka listener、手动 ack、按 topic 分层重试都直接写进了应用代码。
- 重试逻辑依赖 Kafka record timestamp、partition pause/resume 和多级 retry topic。
- outbox 发布和 DLQ 投递都依赖 `KafkaTemplate`。
- 部署、本地开发和测试环境也都默认 Kafka 存在。

这会带来两个问题：

- 消息层和 Kafka 绑定过深，后续很难演进。
- 下游处理被 Kafka 运行方式约束，而不是围绕业务规则设计。

后处理流程本身也已经变化。系统不再需要一个同时消费 `record-persisted` 和 `round-persisted` 的 orchestrator 去推断 readiness，新的业务规则是：

- `record-persisted` 是后处理的唯一触发事件。
- `round` 上报可能会慢一点，这种延迟是正常情况。
- LLM 和第三方推送都直接消费 `CALL_RECORD_PERSISTED`。
- 如果 `call_record.round_total` 和数据库里实际 `call_round` 数量暂时不一致，则依赖 RocketMQ 的重新投递稍后重试。
- 如果超过重试次数后数量仍未一致，也允许继续执行 LLM 和第三方推送。

## 目标

- 彻底移除 Kafka，改为 RocketMQ。
- 使用 RocketMQ 原生重试语义，不再照搬 Kafka retry topic 方案。
- 保证同一个 `callId` 的消息有序。
- 保持 MySQL 作为主写链路的成功边界。
- 保留事务型 outbox 作为下游事件分发机制。
- 删除 `CallOrchestratorConsumer`，改成由下游消费组直接消费 `record-persisted`。
- 将 `round` 延迟到达视为正常业务情况，通过 RocketMQ 重新投递等待补齐。
- 当超过最大重试次数后，即使 `round` 仍未全部到齐，也允许继续执行下游处理。

## 非目标

- 这一阶段不重构 query service。
- 不引入 CDC 或 Debezium。
- 不在 ingestion 事务里直接调用 LLM 或第三方系统。
- 不再引入单独的 postprocess 状态表或调度补偿器。

## 推荐架构

整体改成基于 RocketMQ 的事件链路，并继续使用事务型 outbox；后处理 readiness 不再落库建状态，而是由下游 consumer 在消费时自行判断。

主链路：

`RocketMQ ingest topic -> ingestion consumer -> MySQL + outbox（同一本地事务）-> ack`

下游分发链路：

`outbox publisher -> RocketMQ domain event topic`

后处理链路：

`record-persisted event -> llm consumer / third-party consumer -> 查询 expected rounds 和 persisted rounds -> 满足条件立即处理，否则依赖 RocketMQ 重投 -> 超过最大重试后按不完整数据继续处理`

这样分工更清楚：

- MQ 负责真实消费失败时的可靠投递和重试。
- 下游 consumer 自己负责判断“业务数据是否已经到齐”。
- 不再额外维护 `MessageBatchProcessor` 这类应用内并发调度器。

## 为什么要用 RocketMQ 原生语义

这次重构不能简单把 Kafka retry 方案机械迁移过去。

当前 Kafka 方案依赖：

- 多个 retry topic
- record timestamp 判断
- partition pause/resume
- 手动 ack 时机控制

这套设计适合 Kafka，不适合 RocketMQ。

RocketMQ 版本应该改成：

- 用顺序消息保证同一个 `callId` 有序
- 用 RocketMQ consumer retry 处理暂时性消费失败
- 用业务 DLQ 处理脏数据或不可恢复错误
- 用 RocketMQ 重新投递处理 `round` 到达较慢的情况
- 用 `@RocketMQMessageListener` 的 `consumeThreadMax` 控制每个消费组的并发

## 消息模型

### Ingest Topic

主入口使用一个 RocketMQ topic：

- `call_ingest`

Tag：

- `CALL_RECORD`
- `CALL_ROUND`

入口消息协议直接使用写库 DTO：

- `CALL_RECORD` -> `CallRecordMessage`
- `CALL_ROUND` -> `CallRoundMessage`

这里不再复用 `DomainEventMessage`。`DomainEventMessage` 只从 outbox 发布边界开始使用。

原因：

- 如果 `CALL_RECORD` 和 `CALL_ROUND` 继续分成两个主 topic，那么系统没法对同一个 `callId` 建立有意义的跨消息类型顺序保证。
- 合并成一个 topic 后，生产端以 `callId` 作为 sharding key，RocketMQ 才能把相关消息路由到同一个 queue 上顺序处理。

### Domain Event Topic

领域事件使用一个 topic：

- `call_domain_event`

Tag：

- `CALL_RECORD_PERSISTED`
- `CALL_ROUND_PERSISTED`

其中：

- `CALL_RECORD_PERSISTED` 是 LLM 和第三方推送的直接触发事件
- `CALL_ROUND_PERSISTED` 仍然可以给 ES 索引之类的下游使用，但它不再参与 readiness 编排

不再需要：

- `CALL_READY_FOR_AI`
- `CALL_READY_FOR_EXTERNAL_SYNC`

### 顺序键

以下消息统一使用 `callId` 作为顺序键：

- 主入口消息
- persisted domain event
- ready domain event

## 数据模型调整

### Call Record

新增正式字段表示 round 总数：

- `CallRecordMessage.roundTotal`
- `CallRecordEntity.roundTotal`
- `call_record.round_total`

这个字段不能继续藏在 `extJson` 里。它已经是 readiness 判断的核心业务字段，必须具备清晰的 schema、校验和可观测性。

### Post-Process State

本次方案不再引入 `call_postprocess_state`。

原因：

- readiness 判断逻辑已经足够简单，只依赖 `roundTotal` 和 `call_round` 实际数量。
- LLM 和第三方推送各自独立消费 `CALL_RECORD_PERSISTED` 即可。
- 未齐时直接依赖 RocketMQ 重投，不再额外维护数据库状态和调度器。

代价：

- 可观测性会弱一些，数据库中没有单独的“等待中”状态表。
- “重试了多少次”主要依赖 RocketMQ 的消费重试次数和日志观察。

## 主写入流程

对于 `CALL_RECORD` 和 `CALL_ROUND`：

1. 以 RocketMQ 顺序消费方式拉取消息。
2. 直接反序列化为 `CallRecordMessage` / `CallRoundMessage` 并做参数校验。
3. 路由到正确分片。
4. 持久化业务数据到 MySQL。
5. 在同一个本地事务里插入 outbox 记录。
6. 只有事务提交成功后才确认消费成功。

主链路不直接调用 Elasticsearch、LLM 或第三方接口。

## 并发模型

本次方案不再保留 `MessageBatchProcessor`。

原因：

- RocketMQ 已经提供消费端并发控制，不需要再在应用层额外做一层批处理分发。
- 主写链路是否并发，应优先由 RocketMQ consumer 的线程模型决定。
- 每个消费组的线程池可以天然隔离，避免一个下游组的压力影响另一个消费组。

建议做法：

- `call-ingestion-main-group` 使用独立的 `consumeThreadMax`
- `call-index-group` 使用独立的 `consumeThreadMax`
- `call-ai-group` 使用独立的 `consumeThreadMax`
- `call-external-sync-group` 使用独立的 `consumeThreadMax`

实现上：

- 每个 consumer 直接调用各自的 service
- 不再经过统一的 `MessageBatchProcessor`
- 通过 RocketMQ listener 配置控制并发度

要注意：

- `consumeThreadMax` 解决的是消费并发，不等于无限扩容
- 同一个 `callId` 的顺序仍然受 RocketMQ orderly consume 约束
- 数据库连接池、分片热点和下游接口限流需要和各消费组线程数一起评估

## Outbox 发布

继续保留事务型 outbox。

建议行为：

1. 业务事务插入状态为 `NEW` 的 outbox 记录。
2. Outbox publisher 扫描可发布记录。
3. 发布到 RocketMQ 成功后把状态更新为 `PUBLISHED`。
4. 发布失败时更新 `attempt_count`、`last_error` 和 `next_attempt_at`。

它仍然是解决 MySQL 到 MQ 双写问题的关键保护层。

## 后处理流程

删除 `CallOrchestratorConsumer`。

新增两个直接消费 `CALL_RECORD_PERSISTED` 的 consumer：

- `llm-summary-group`
- `third-party-sync-group`

每个 consumer 的处理逻辑一致：

1. 消费 `CALL_RECORD_PERSISTED`。
2. 读取 `callId`、`tenantId` 和 `roundTotal`。
3. 查询该 `callId` 当前已持久化的 `call_round` 数量。
4. 如果 `receivedRoundCount == expectedRoundCount`：
   - 认为数据完整
   - 正常执行对应下游逻辑
5. 如果 `receivedRoundCount < expectedRoundCount` 且当前重试次数尚未达到阈值：
   - 认为 `round` 还没到齐
   - 返回重新消费，让 RocketMQ 稍后重投
6. 如果 `receivedRoundCount < expectedRoundCount` 且当前重试次数已达到阈值：
   - 接受数据不完整
   - 继续执行对应下游逻辑
   - 记录“按不完整数据执行”的日志或指标

LLM 和第三方 consumer 彼此独立重试，互不共享状态。

## 重试和失败语义

### MQ 重试

RocketMQ consumer retry 只用于真实消费失败，例如：

- 数据库连接异常
- 事务超时
- 消费过程中基础设施抖动

### 业务延迟重试

当：

`receivedRoundCount < expectedRoundCount`

这不一定是错误，更常见的情况是 `round` 上报慢了。

它表示：

- `round` 还在上报途中，或
- `round` 已经上报但尚未全部持久化完成

正确处理方式是：

- 在当前 consumer 内返回稍后重试
- 等待 RocketMQ 重新投递
- 重新消费时再次查询数据库数量

不应该：

- 直接送入 DLQ

### 超过重试次数后的处理

如果 `receivedRoundCount < expectedRoundCount`，但 RocketMQ 的重试次数已经到达业务允许阈值：

- 允许继续执行 LLM 总结
- 允许继续执行第三方推送
- 允许带“不完整数据”标识记录日志、监控或审计信息

这表示业务接受“在最终仍未补齐 round 时，先基于当前数据继续处理”。

### 业务 DLQ

业务 DLQ 只处理脏数据或不可恢复错误，例如：

- 缺失 `callId`
- 缺失或非法 `roundTotal`
- 事件 payload 格式错误
- tag 和消息类型不匹配

### 重试策略

建议由 RocketMQ 的消费重试机制承接“等待 round 到齐”的过程，同时在 consumer 内增加业务阈值判断：

- 在业务阈值以内：数量未齐则继续重试
- 超过业务阈值：即使数量仍未齐，也继续执行

具体阈值可以配置成：

- `llmMaxIncompleteRetries`
- `thirdPartyMaxIncompleteRetries`

如果两边容忍度一致，也可以统一成一个配置项。

## Consumer Group

建议 group：

- `call-ingestion-main-group`
- `call-index-group`
- `call-ai-group`
- `call-external-sync-group`

每个 group 只负责一个明确能力。LLM 和第三方各自判断是否需要继续重试，并各自维护独立的消费线程配置。

## 测试影响

这次迁移需要把 Kafka 相关测试整体替换成 RocketMQ 版本，并补上围绕新状态逻辑的单元测试。

关键覆盖点：

- 基于 `callId` 的顺序消费和顺序发布
- `call_record` / `call_round` 和 outbox 的同事务写入
- 不同消费组 `consumeThreadMax` 配置互相隔离
- `record-persisted` 直接触发 LLM 和第三方 consumer
- round 数量不足时 RocketMQ 重新投递
- 达到业务重试阈值后按不完整数据继续执行
- LLM 和第三方 consumer 的幂等处理
- 非法 payload 进入业务 DLQ

## 部署影响

以下内容需要从 Kafka 改成 RocketMQ：

- 本地 `docker-compose`
- Kubernetes manifests
- 应用配置
- 健康检查
- topic provisioning 和启动文档

同时删除：

- Kafka 依赖
- Kafka 专属配置类
- Kafka 测试工具

## 总结

这次重构应该被当成一次真正的消息架构调整，而不是简单换 client。

关键设计点是：

- 一个按 `callId` 顺序处理的主入口 topic
- 继续保留事务型 outbox
- `record-persisted` 作为 LLM 和第三方的直接触发事件
- `roundTotal` 成为 `call_record` 的正式字段
- `round` 延迟到达通过 RocketMQ 重新投递处理
- 超过重试次数后允许按不完整数据继续执行
- 不再保留 `MessageBatchProcessor`，消费并发直接交给 RocketMQ listener 配置
