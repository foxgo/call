# Postprocess DLQ Consumers Design

**日期：** 2026-05-25

**状态：** 已确认

## 目标

为后处理消费组补齐 RocketMQ 自动死信入口：

- `call-index-group`
- `call-ai-group`
- `call-third-party-group`

让这三个组在超过 `maxReconsumeTimes` 后，也能像主写链路的 `record` / `round` 一样，把 `%DLQ%<consumerGroup>` 中的消息转成可追踪、可补偿的死信任务。

## 当前问题

当前仓库只有两个自动死信 consumer：

- `RocketMqRecordDeadLetterConsumer`
- `RocketMqRoundDeadLetterConsumer`

`RocketMqPersistedEventConsumer` 以及后续新增的 `ai` / `third-party` 组虽然同样依赖 RocketMQ 原生重试和原生 DLQ，但项目内没有对应的 DLQ listener。结果是：

- RocketMQ 会把超限消息送入 `%DLQ%<group>`
- 但应用不会继续消费这些 DLQ topic
- 死信消息无法统一落库形成补偿任务

## 方案

新增三个 DLQ consumer：

- `RocketMqIndexDeadLetterConsumer`
- `RocketMqAiDeadLetterConsumer`
- `RocketMqThirdPartyDeadLetterConsumer`

这三个 consumer 继续复用 `AbstractRocketMqDeadLetterConsumer` 和 `DeadLetterTaskService`。

## 数据建模

`call_dead_letter_task.message_type` 目前只承载 `RECORD` / `ROUND`。为支持后处理组，需要扩展到：

- `INDEX`
- `AI`
- `THIRD_PARTY`

这些值只是补偿分类，不改变表结构。

## 幂等键

主写链路死信目前从 payload 里恢复业务幂等键：

- `RECORD` -> `callId`
- `ROUND` -> `callId:roundId`

后处理组消费的消息是领域事件，当前和后续都可能共享同一条 `call_record_persisted` 或 `call_record_analysis_completed` 事件，因此不适合再强行按 `CallRecordMessage` / `CallRoundMessage` 反序列化。

后处理组死信的 `idempotency_key` 改为直接使用 `DomainEventMessage.eventId`。

这样能保证：

- 对同一条后处理领域事件具备稳定标识
- 不依赖特定 payload 类型
- 不阻塞未来新增其它后处理事件类型

## 配置

在 `call.rocketmq.consumers` 下新增：

- `index-dlq`
- `ai-dlq`
- `third-party-dlq`

每组独立配置：

- `group`
- `consume-thread-max`
- `max-reconsume-times`

并让 `RocketMqListenerContainerCustomizer` 把这些配置应用到 RocketMQ 原生 consumer。

## 验证

至少覆盖以下测试：

- 新增三个 DLQ consumer 能把消息委托给 `DeadLetterTaskService`
- `DeadLetterTaskService` 能为 `INDEX` / `AI` / `THIRD_PARTY` 死信生成 `eventId` 幂等键
- 新增的三组配置能完成绑定
- `RocketMqListenerContainerCustomizer` 能对新增三组 DLQ consumer 应用线程数和最大重试次数
