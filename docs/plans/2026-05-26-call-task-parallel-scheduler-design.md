# Call Task Parallel Scheduler Design

**日期：** 2026-05-26

**状态：** 已确认

## 问题

当前 `call-task` 调度链路使用三个定时任务：

- `CallTaskDispatcher.dispatchRunningTasks()`
- `RetryQueueScheduler.requeueDueRetries()`
- `ProcessingTimeoutRecoveryJob.recoverExpiredProcessing()`

它们都通过 `loadRunningTasks()` 全量遍历 `RUNNING` 任务，再按任务逐个处理。这个模型存在几个根本问题：

- 多实例下每个实例都会重复扫描全部运行中任务，扩展性差
- 当存在几万个运行中任务时，调度复杂度与任务总数线性相关
- `nextDispatchTime` 将任务调度建模为时间轮询，不符合“只要还有剩余并发就应继续下发”的容量驱动语义
- `recoverExpiredProcessing()` 与回调并发时，当前实现可能重复释放并发额度，导致限流计数失真

系统需要一个可扩展的多实例并行调度器，在只使用 `MySQL + Redis` 的前提下满足以下要求：

- 不依赖任务级 `next_dispatch_time`
- 基于任务 `priority` 做加权公平调度
- 只要任务仍有待拨号数据且并发未满，就在下一轮立即继续下发
- 支持多实例水平扩展，不重复扫描全量运行中任务
- Redis 保存高频调度状态，MySQL 仍是最终真相

## 目标

- 将任务调度从“全量扫描 + 定时轮询”改为“活跃任务驱动 + 分区并行调度”
- 使用 Redis partition lease 实现多实例稳定分工
- 使用活跃任务队列承接加权公平调度，不再依赖任务级 `nextDispatchTime`
- 将 `dispatch / retry / recover / callback` 统一为事件驱动调度闭环
- 修正并发额度释放的正确性问题，使并发计数只跟真实状态迁移绑定

## 非目标

- 本次不引入新的调度中间件或工作流引擎
- 本次不实现跨机房多活调度
- 本次不引入线路级 QPS 管理或 predictive dialing
- 本次不改变 MySQL 作为任务与号码真实状态存储的职责

## 总体方案

推荐方案是 `Redis 分区 + 加权公平轮转 + task lease 事件激活`。

整体思路如下：

1. 将全部可调度任务按固定 partition 数量进行哈希分桶
2. 由每个实例通过 Redis lease 抢占并续租若干 partition，只处理自己持有的 partition
3. 每个 partition 维护自己的活跃任务队列，只保存“当前值得继续调度”的任务
4. worker 从活跃任务队列中按加权公平算法选择 task，给每个 task 一个小的 dispatch budget
5. task 是否继续参与下一轮，不看 `nextDispatchTime`，只看：
   - 是否仍有待拨号数据
   - 是否仍有并发余量
   - 是否被 retry/recover/callback 重新激活

这个方案避免了全量扫描运行中任务，也避免了单全局队列成为热点。

## 架构分层

### 1. `TaskPartitionManager`

负责 partition 的分配、续租和移交。

职责：

- 定义固定数量的 partition，例如 `128` 或 `256`
- 为每个 partition 建立 owner lease
- 实例启动后持续尝试抢占空闲 partition
- 实例存活期间持续续租自己持有的 partition
- lease 过期后允许其他实例接管

设计约束：

- 同一时刻一个 partition 只能有一个 owner
- task 只由其所属 partition 的 owner 实例调度

### 2. `ActiveTaskQueue`

负责维护 partition 内当前可参与调度的 task。

职责：

- 保存 partition 内活跃 task 的有序集合
- 提供“弹出一个最该被服务的 task”能力
- 支持 task 被 dispatch、retry、recover、callback 事件重新激活
- 支持 task 因并发打满或队列空而暂时退出活跃队列

这个队列不保存时间语义，而保存调度公平语义。

### 3. `PartitionSchedulerWorker`

负责真正的拨号任务分发。

职责：

- 从当前实例持有的 partition 中轮询活跃队列
- 按公平分值选 task
- 根据该 task 当前剩余并发和配置上限计算本轮 budget
- 从 task 的 `ready` 队列 claim 号码，推进到 `processing`
- 更新数据库 `QUEUED -> DIALING`
- 发布 MQ
- 根据处理结果决定 task 是否重新回到活跃队列

### 4. `TaskActivationService`

负责将外部事件转换为 task 激活动作。

激活来源：

- 预热后发现任务已有 `ready` 数据
- dispatch 后仍有剩余待拨号数据
- callback 释放了并发
- retry 到期后将号码搬回 `ready`
- processing 超时回收后将号码搬回 `ready`

### 5. `DialUnitStateCoordinator`

负责 Redis 和 MySQL 之间的状态对齐。

职责：

- 原子迁移 `ready / processing / retry`
- 基于 DB compare-and-set 做状态迁移
- 只对真实成功的 DB 状态变更释放并发额度
- 将回调与超时回收的竞态压缩到状态机条件更新中

## 数据模型调整

### `call_task`

任务主表保留任务生命周期和统计信息，但不再以 `next_dispatch_time` 驱动调度。

建议：

- 保留 `priority`
- 保留 `max_concurrency`
- 保留统计字段
- 新增或保留 `status`
- 移除任务调度依赖中的 `next_dispatch_time`

如果出于兼容考虑短期内不能删列，可以先停止在调度链路中使用该字段，再择机清理。

当前落地约束：

- `next_dispatch_time` 列暂时保留，避免直接破坏现有库结构
- 通过后续 migration 将该列标记为 deprecated comment，明确禁止重新接入调度主路径

### `call_dial_unit`

继续作为号码真实状态表，保留以下关键字段：

- `status`
- `retry_count`
- `max_retry_count`
- `dispatch_token`
- `inflight_expire_at`
- `next_call_time`
- `last_call_time`

说明：

- 任务层不再依赖 `next_dispatch_time`
- 号码层的 `next_call_time` 仍然用于 retry backoff
- `dispatch_token + inflight_expire_at` 仍是回调与超时恢复的关键 compare-and-set 条件

## Redis Key 设计

### Partition Lease

- `call:scheduler:partition:{p}:owner`
  - value: `instanceId`
  - ttl: lease 过期时间

语义：

- 用 `SET NX PX` 抢占 lease
- 用 compare-and-renew 续租
- owner 失联后 partition 自动释放

### Active Task Queue

- `call:scheduler:partition:{p}:active`
  - type: `ZSET`
  - member: `taskId`
  - score: `fairScore`

- `call:scheduler:task:{taskId}:meta`
  - type: `HASH`
  - fields:
    - `tenantId`
    - `priority`
    - `weight`
    - `partition`
    - `active`
    - `blockedReason`
    - `version`

### Retry / Recovery Due Index

建议按 partition 建立到期索引，避免全局热 key：

- `call:scheduler:partition:{p}:retry-due`
  - member: `taskId:shard:dialUnitId`
  - score: `retryAt`

- `call:scheduler:partition:{p}:processing-timeout`
  - member: `taskId:shard:dialUnitId:dispatchToken`
  - score: `inflightExpireAt`

### Dial Unit Queue

继续按 `task + shard` 组织 Redis 队列：

- `call:task:{taskId}:shard:{s}:ready`
- `call:task:{taskId}:shard:{s}:processing`
- `call:task:{taskId}:shard:{s}:retry`

所有迁移继续使用 Lua 脚本保证原子性。

## Task 调度状态

任务调度态不再依赖时间，而改成三态：

- `ACTIVE`
  - 当前应该参与调度
- `BLOCKED`
  - 当前暂时不需要参与调度
- `INACTIVE`
  - 已完成、已暂停、已停止或未运行

### `ACTIVE`

任务应进入活跃队列的典型条件：

- 任务处于 `RUNNING`
- 存在 `ready` 号码，或事件表明该任务已经恢复出新的 `ready` 号码
- 当前并发未满，或者释放并发后应该立刻重新尝试派发

### `BLOCKED`

阻塞原因应显式记录：

- `CONCURRENCY_FULL`
- `EMPTY`
- `PAUSED`

说明：

- `CONCURRENCY_FULL`：等待 callback 或 recovery 释放额度
- `EMPTY`：等待 preload、retry、recovery 再次补充 `ready`
- `PAUSED`：任务暂停，不应被任何事件重新激活

## 加权公平调度算法

### 权重映射

`priority` 不直接作为排序值，而映射为 `weight`。例如：

- `P1 -> 16`
- `P2 -> 8`
- `P3 -> 4`
- `P4 -> 2`

高优先级任务拿更多调度份额，但不应独占队列。

### 公平分值

每个 task 在活跃队列里维护 `fairScore`。

worker 每次从 partition 的 `active` ZSET 中选择 `fairScore` 最小的 task。

每轮服务后按实际派发量增加分值：

- `fairScore = fairScore + dispatched * SCALE / weight`

其中 `SCALE` 是整数放大因子，用于避免浮点误差。

效果：

- 高权重 task 分值增长更慢，会更频繁地被调度
- 低权重 task 分值增长更快，但不会永久饥饿

### 小预算原则

单个 task 每轮只拿一个小 budget，避免热点任务长时间占住 worker。

建议：

- `available = taskMaxConcurrency - inflight`
- `budget = min(available, dispatchBatchSize, taskBurstLimit)`

这样高优先级任务可以连续多轮拿更多份额，但不会一次独占 partition。

## 调度链路

### 1. Dispatch

partition owner 从活跃队列中取出一个 task：

1. 读取 task 并发计数和元数据
2. 计算本轮 budget
3. 从 `ready -> processing` claim 号码
4. DB 将号码从 `QUEUED` 标记为 `DIALING`
5. 发布 MQ
6. 根据结果决定 task 后续状态：
   - 仍有容量且可能仍有数据：重新入活跃队列
   - 并发满：`BLOCKED(CONCURRENCY_FULL)`
   - 无数据：`BLOCKED(EMPTY)`

### 2. Retry

不再按 task 扫所有 `retry` 队列。

改为搬运 partition 内已到期的 retry 项：

1. 从 `retry-due` 取出已到期项
2. 对应号码从 `retry -> ready`
3. 如果本次至少恢复了一条 ready 数据，则激活该 task

### 3. Recovery

不再按 task 扫所有 processing。

改为搬运 partition 内已超时的 processing 项：

1. 从 `processing-timeout` 取出已超时项
2. 按 `taskId + dialUnitId + dispatchToken + inflightExpireAt` 条件更新 DB
3. 只有 DB 实际从 `DIALING` 迁移成功的记录，才：
   - 释放并发
   - 重新入 `ready` 或标记 `FAILED`
4. 如果任务仍可继续调度，则重新激活 task

### 4. Callback

回调不只是状态写回，也是重新激活任务的事件源：

1. 更新 DB `DIALING -> SUCCESS/QUEUED/FAILED`
2. `ack processing`
3. 释放并发
4. 如果需要 retry，写入 `retry-due`
5. 如果任务仍有继续下发的可能，重新激活 task

## 多实例正确性

设计依赖以下原则：

1. partition lease 保证一个 partition 同时只有一个调度 owner
2. Redis 队列迁移必须通过 Lua 保证原子性
3. MySQL 状态迁移必须带 compare-and-set 条件：
   - `status`
   - `dispatch_token`
   - `inflight_expire_at`
4. 并发 release 只按 DB 实际更新成功条数执行，不能按尝试处理条数执行

这样可以避免：

- 多实例重复调度同一 task
- retry / recover 重复搬运
- callback 与 recovery 并发时重复释放并发额度
- 大规模运行中任务下的重复全量扫描

## 可扩展性分析

### 时间复杂度

旧模型：

- 每轮复杂度近似 `O(运行中任务总数)`

新模型：

- owner lease 只处理自身 partition
- worker 只消费活跃 task
- retry / recover 只处理到期项

复杂度更接近：

- `O(活跃 task 数 / partition)`
- `O(到期 retry 项)`
- `O(到期 processing timeout 项)`

### 横向扩展

通过增加实例数，系统可自然扩展：

- 更多实例可持有更多 partition
- 每实例可配置多个 worker 并行处理 partition
- 每个 partition 相互独立，减少跨实例共享热点

## 失败恢复

- 实例宕机：
  - partition lease 过期
  - 其他实例接管 partition
  - `processing-timeout` 负责回收悬挂中的拨号单元

- MQ 发布失败：
  - DB compare-and-set 回滚 `DIALING -> QUEUED`
  - dial unit 返回 `ready`
  - task 重新激活

- Redis 与 DB 短暂不一致：
  - 以 DB 为最终真相
  - 通过 recovery 和 compare-and-set 修复临时偏差

## 迁移策略

建议分阶段迁移：

### 阶段 1

- 引入 partition lease
- 保留现有 dispatch 逻辑，但只允许 owner 实例处理自己的任务
- 修复 recovery 的并发 release 正确性

### 阶段 2

- 引入 partition 级 active task queue
- 移除 `loadRunningTasks()` 全量扫描主路径
- dispatch 改为消费 active task

### 阶段 3

- retry / recover 改为消费 partition due index
- callback 负责重新激活 task
- 停止依赖任务级 `next_dispatch_time`

### 阶段 4

- 清理遗留字段和旧调度入口
- 增加指标、告警和压测验证

## 监控建议

建议新增以下指标：

- 每实例持有 partition 数
- 每 partition 活跃 task 数
- task 激活次数、阻塞次数、重新激活延迟
- dispatch claim 成功数、MQ 发布成功数、回滚数
- retry due backlog
- processing timeout backlog
- callback release 次数
- recovery release 次数
- Redis 并发计数与 DB `DIALING` 数偏差

## 推荐结论

推荐采用“Redis 分区 + 加权公平活跃任务队列 + 事件驱动 task 激活”的并行调度器设计。

这是在当前仓库 `MySQL + Redis` 约束下，兼顾多实例扩展性、加权公平、容量驱动调度和状态正确性的最合适方案。它能够替代当前基于 `nextDispatchTime` 和全量任务扫描的调度模式，并为后续大规模运行中任务场景提供稳定的横向扩展基础。
