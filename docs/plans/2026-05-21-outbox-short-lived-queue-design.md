# Outbox 短生命周期发送队列设计

**日期：** 2026-05-21

**状态：** 已确认

## 问题

当前 `call_event_outbox` 设计把 outbox 同时当成：

- MySQL 到 MQ 的可靠发送缓冲
- 发布历史留存表

实现上，publisher 会周期性扫描 `NEW` 和到期 `FAILED` 记录，发布成功后仅把状态更新为 `PUBLISHED`，不会删除历史记录。

这会带来两个问题：

- 单库内 `call_event_outbox` 会持续膨胀，长期保留大量 `PUBLISHED` 数据。
- outbox 的真实职责是“可靠发出去”，不是“长期保留历史”；把两者混在一起会把写放大、索引膨胀和查询成本都堆到同一张表上。

## 目标

- `call_event_outbox` 只保存尚未成功发布的事件。
- 事件发布成功后立即删除，不保留 `PUBLISHED` 历史行。
- 多个 publisher 实例并发运行时，同一行只能被一个实例 claim。
- 保持至少一次投递语义，不追求恰好一次。
- publisher 异常退出后，卡在处理中间态的记录可以恢复重试。

## 非目标

- 不引入 CDC、Debezium 或 binlog 方案。
- 不把 outbox 改造成审计日志或历史归档表。
- 不改变下游消费端的幂等责任。

## 推荐架构

把 `call_event_outbox` 改成短生命周期发送队列。

状态只保留：

- `NEW`：新插入，待发送
- `PROCESSING`：已被某个 publisher claim，正在发送
- `FAILED`：发送失败，等待 `next_attempt_at` 到期后重试

成功后不再写 `PUBLISHED`，而是直接删除该行。

整体流程：

`业务事务插入 NEW -> publisher claim 一批记录为 PROCESSING -> 逐条发布 -> 成功即删除 -> 失败改为 FAILED`

## 状态机

### 新写入

- 业务事务内插入 `NEW`
- `attempt_count = 0`
- `next_attempt_at = NULL`

### claim

publisher 周期性执行 claim：

- 可 claim 的记录包括
  - `status = NEW`
  - `status = FAILED and next_attempt_at <= now`
- claim 成功后把状态更新为 `PROCESSING`
- 同时更新 `updated_at = now`

claim 之后，publisher 只处理自己刚 claim 到的记录。

### 发布成功

- MQ publish 成功
- 直接 `DELETE FROM call_event_outbox WHERE id = ?`

### 发布失败

- MQ publish 抛异常
- 把该行改回 `FAILED`
- 更新 `attempt_count`
- 更新 `next_attempt_at`
- 更新 `last_error`
- 更新 `updated_at`

### 处理中断恢复

如果 publisher 在 `PROCESSING` 状态下崩溃，记录不能永久卡住。

增加恢复规则：

- 当 `status = PROCESSING and updated_at <= now - processing_timeout` 时，视为 abandoned
- 下次 claim 前，先把这类记录重置为 `FAILED`
- 重置后走正常的失败重试路径

## Repository 设计

当前 `findPublishableBatch()` 是“先查后发”，不适合多实例竞争。需要改成“先 claim 再发”。

建议 repository 收敛为以下接口：

- `claimPublishableBatch(now, batchSize, processingTimeout)`
- `markFailed(id, attemptCount, lastError, nextAttemptAt, now)`
- `deleteById(id)`
- `requeueExpiredProcessing(now, processingTimeout)`

其中 `claimPublishableBatch()` 推荐按下面的顺序实现：

1. 查询一批候选 `id`
2. 用带状态条件的 `UPDATE` 把这些候选行改成 `PROCESSING`
3. 回表读取真正 claim 成功的记录

这样即使多个实例同时执行，只有成功改成 `PROCESSING` 的实例会拿到记录。

## 索引设计

当前索引：

- `uk_call_event_outbox_event_id (event_id)`
- `idx_call_event_outbox_status_next_attempt (status, next_attempt_at)`

改造后建议索引变为：

- 保留 `uk_call_event_outbox_event_id (event_id)`
- 把 publishable 查询索引调整为 `idx_call_event_outbox_publishable (status, next_attempt_at, created_at, id)`
- 如需专门支持处理中断恢复，可评估补 `idx_call_event_outbox_processing (status, updated_at, id)`

原因：

- claim 查询既要按状态和重试时间过滤，也要按 `created_at` 取最早批次
- `PROCESSING` 超时回收依赖 `updated_at`

## 并发和一致性语义

这个方案的核心收益是：

- 表不会因为 `PUBLISHED` 历史而无限膨胀
- 多实例下可以通过 `PROCESSING` 中间态避免重复 claim

但它仍然不是恰好一次。

仍然存在的经典窗口：

- MQ 已成功接收
- 应用还没来得及删除 outbox 行就崩溃

这种情况下，记录未来可能再次被发送，因此整体语义仍然是至少一次投递。下游消费端必须继续基于 `event_id`、业务主键或幂等键去重。

## 测试策略

至少需要覆盖：

- 只 claim `NEW` 和到期 `FAILED`
- claim 后记录状态改为 `PROCESSING`
- 已 claim 的记录不会被再次 claim
- 发布成功后直接删除，不再出现 `PUBLISHED`
- 发布失败后正确回写 `FAILED`
- 超时 `PROCESSING` 记录会被重新纳入重试

## 迁移说明

这是一次行为变更，不只是索引优化。

迁移后：

- `call_event_outbox` 不再适合作为发布历史排查来源
- 排障需要更多依赖 MQ、应用日志和下游幂等观测
- 如果未来确实需要事件审计，应单独建设历史表或日志链路，而不是让 outbox 承担两类职责
