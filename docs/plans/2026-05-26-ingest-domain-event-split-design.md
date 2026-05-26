# Ingest / Domain Event Split Design

**日期：** 2026-05-26

**状态：** 已确认

## 目标

把入口写库消息和下游领域事件彻底拆开：

- `record-ingest` / `round-ingest` 只消费写库所需的强类型 DTO
- outbox 和后处理链路继续使用 `DomainEventMessage`
- `DomainEventMessage` 不再同时承载 ingest command 和 persisted event 两种语义

## 当前问题

当前实现让 `RocketMqCallRecordConsumer` 和 `RocketMqCallRoundConsumer` 都先把消息体反序列化成 `DomainEventMessage`，再从 `payload` 里恢复 `CallRecordMessage` / `CallRoundMessage`。

这带来两个问题：

- `DomainEventMessage` 这个名字和实际用法不一致。入口消息本质上是“写入命令”，不是“领域事件”。
- 死信任务服务默认把主写链路死信都当成 `DomainEventMessage` 解析，导致入口消息和下游事件在补偿语义上也混在一起。

## 设计结论

采用硬拆分方案：

- 入口 topic 直接承载 ingest DTO
- outbox 发布后的下游 topic 继续承载 `DomainEventMessage`

不保留兼容层，不做双读。由于允许直接切换 RocketMQ 协议，这次改造以“语义完全收口”为目标。

## 消息边界

### Ingest Message

入口消息只用于主写链路：

- `record-ingest` -> `CallRecordMessage`
- `round-ingest` -> `CallRoundMessage`

这些消息只包含写库需要的业务字段，不再包含以下 domain event 元数据：

- `eventId`
- `eventType`
- `aggregateType`
- `aggregateId`
- `occurredAt`
- `schemaVersion`

`RocketMqCallRecordConsumer` 和 `RocketMqCallRoundConsumer` 直接把消息体反序列化成对应 DTO，然后构造 `InboundMessage<T>`。

### Domain Event

领域事件只从 outbox 开始进入系统：

- `OutboxEventFactory` 继续构造 `DomainEventMessage`
- `RocketMqPersistedEventConsumer`、索引链路、后续 AI / third-party 后处理继续消费 `DomainEventMessage`

`DomainEventMessage` 的职责收窄为：

- 表达某个业务事实已经发生
- 提供事件标识、聚合标识、版本和发生时间
- 承载领域事件 payload

## 数据流

改造后的主链路：

`CallRecordMessage / CallRoundMessage -> ingestion consumer -> MySQL + outbox -> ack`

改造后的下游链路：

`outbox row -> DomainEventMessage -> RocketMQ persisted-event topic -> index / AI / third-party consumers`

这样以后入口协议和领域事件协议可以独立演进。

## 主写链路改动

`RocketMqCallRecordConsumer`：

- 删除对 `DomainEventMessage` 的依赖
- 直接反序列化 `CallRecordMessage`
- 幂等键继续通过 `MessageKeys.recordIdempotencyKey(message)` 生成
- `tenantId` 直接从 `CallRecordMessage` 读取
- 删除 `"CALL_RECORD"` 的 envelope 事件类型校验

`RocketMqCallRoundConsumer` 同理：

- 直接反序列化 `CallRoundMessage`
- 删除 `"CALL_ROUND"` 校验

## Outbox 和后处理改动

outbox 维持现状：

- `OutboxEventFactory` 继续创建 `DomainEventMessage`
- `CallEventOutboxEntity.payload` 继续保存序列化后的领域事件 envelope
- `RocketMqPersistedEventConsumer` 保持按 `DomainEventMessage` 解析

这样能把“主写链路输入协议”和“内部领域事件协议”清晰分层，同时不影响后处理消费者。

## 死信与补偿语义

主写链路 DLQ 不再把原始消息当成 `DomainEventMessage`。

`DeadLetterTaskService` 改成按 `MessageType` 解析 payload：

- `RECORD` -> `CallRecordMessage`
- `ROUND` -> `CallRoundMessage`
- 后处理类消息类型继续按 `DomainEventMessage` 解析

对应语义调整：

- `payload` 保存原始入口消息 JSON 或领域事件 JSON
- `payload_type` 对主写链路改为 `RECORD_INGEST` / `ROUND_INGEST`
- `idempotency_key` 对主写链路仍由业务 DTO 恢复
- `message_key` 对主写链路不再依赖 `eventId`，改为业务幂等键；后处理领域事件仍可使用 `eventId`

这样死信表里保存的内容和原始消息类型一致，后续 replay 也可以按真实协议重放。

## 测试策略

至少覆盖以下回归点：

- `RocketMqCallRecordConsumerTest` 和 `RocketMqCallRoundConsumerTest` 直接消费裸 ingest DTO JSON
- 主写链路 consumer 不再依赖 envelope 中的 `tenantId`
- `DeadLetterTaskServiceTest` 验证 `RECORD` / `ROUND` 死信改按 ingest DTO 解析
- `payload_type`、`message_key`、`idempotency_key` 的新语义被固定下来
- `OutboxEventFactoryTest` 和 persisted-event 相关测试继续验证 `DomainEventMessage`

## 文档收口

文档中所有“ingest topic 消费 `DomainEventMessage`”的表述都需要更新为：

- ingress contract: `CallRecordMessage` / `CallRoundMessage`
- internal domain event contract: `DomainEventMessage`

这样代码命名、测试命名和设计文档会回到同一套语义。

## 取舍

这次改造的收益是语义清晰和边界稳定：

- 入口消息不再伪装成 domain event
- `DomainEventMessage` 终于只表示 domain event
- 未来扩展入口协议时不会误伤下游事件协议

代价是：

- ingest 和 domain event 不再共用一套 JSON 外形
- 主写链路 DLQ 需要按消息类别分别解析

在允许直接切协议的前提下，这个代价是值得的。
