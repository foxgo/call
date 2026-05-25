# 通用死信补偿任务设计

**日期：** 2026-05-22

**状态：** 已确认

## 目标

- 为 RocketMQ 自动死信 topic 增加通用消费入口。
- DLQ 消费后不直接重放，而是统一落补偿任务表。
- 通用实体和 mapper 放在 `call-common`，便于后续其他模块复用。

## 约束

- 使用 RocketMQ `%DLQ%<原消费组>` 作为唯一死信入口。
- `call-ingestion` 负责 RocketMQ 消费、任务入库和迁移脚本。
- 本次不实现 replay 执行器，只为后续 replay 保留原始消息和 broker 元数据。

## 方案

新增一张非分片表 `call_dead_letter_task`，由 `call-ingestion` 中两个 DLQ listener 统一消费 RocketMQ 自动死信并委托给一个 service 入库。

DLQ 流程：

`主消费失败 -> RocketMQ 重试 -> 超过 maxReconsumeTimes 后进入 %DLQ%<原消费组> -> 通用 DLQ listener 消费 -> DeadLetterTaskService 解析并入库`

## 数据模型

表 `call_dead_letter_task` 保存：

- `task_key`：幂等键，基于 `sourceTopic + sourcePartition + sourceOffset`
- `message_type`：`RECORD` / `ROUND`
- `source_topic` / `source_partition` / `source_offset`
- `dlq_topic` / `dlq_queue_offset`
- `origin_message_id`
- `message_key`
- `idempotency_key`
- `payload_type`
- `payload`
- `status`：初始值 `NEW`
- `dlq_attempt`
- `dlq_max_attempts`
- `first_failure_at`
- `last_failure_at`
- `created_at` / `updated_at`

其中：

- `payload` 保存原始 `DomainEventMessage` 文本，供后续 replay 直接恢复。
- `task_key` 基于 `%DLQ%topic + originMessageId/msgId` 生成，保证同一条 DLQ 消息重复投递时只生成一条任务。
- `first_failure_at`、`error_class`、`error_message` 在 auto-DLQ 模式下无法从 broker 恢复，本次允许为空。

## 组件调整

`call-common`：

- 新增 `CallDeadLetterTaskEntity`
- 新增 `CallDeadLetterTaskMapper`

`call-ingestion`：

- 新增 `DeadLetterTaskService`
- 新增 `RocketMqRecordDeadLetterConsumer`
- 新增 `RocketMqRoundDeadLetterConsumer`
- 在 `RocketMqProperties` 中增加 DLQ consumer 配置
- 新增 Flyway migration 建表

## 错误处理

- listener 或 service 无法解析原始 DLQ 消息时抛出异常，交给 RocketMQ 重试。
- service 入库失败时抛出异常，交给 RocketMQ 重试。
- 任务重复插入不视为失败，按幂等成功处理。

## 测试范围

- `RocketMqPropertiesTest` 覆盖新增 DLQ consumer 配置绑定。
- `DeadLetterTaskServiceTest` 覆盖：
  - record auto-DLQ 正常入库
  - round auto-DLQ 正常入库
  - 非法原始消息拒绝入库
  - 重复消息按幂等成功
- `DeadLetterConsumerTest` 覆盖：
  - listener 将 `MessageExt` 和 `MessageType` 传给 service
