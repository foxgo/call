# Call Task Scheduler Gap Closure Design

**日期：** 2026-05-28  
**状态：** 已确认，已按本文方案完成实现

## 背景

当前 `call-task` 的号码调度主链路已经具备以下骨架能力：

- 基于 `TaskPartitionManager` 的 Redis 分区租约
- 基于 `ActiveTaskQueue` 的活跃任务入口
- 基于 `RedisDialUnitQueue` 的 `ready / processing / retry` 热窗口状态机
- 基于 `DialResultWritebackService`、`RetryQueueScheduler`、`ProcessingTimeoutRecoveryJob` 的回写闭环

在立项时，当前实现与目标设计之间存在 3 个核心缺口：

1. 公平调度没有真正落地
2. 主派发链路没有真正占用并发额度
3. 运行中任务导入新号码后不会自动重新激活

此外，当时的 `ActiveTaskQueue` 更像“任务索引”，还不是完整调度队列，导致阻塞和再入队语义并未真正闭环。

> 实施结果：
> 本文方案已经完成落地，相关代码已接入 `ActiveTaskQueue`、`PartitionSchedulerWorker`、`DispatchConcurrencyLimiter`、`CallTaskImportService`、`ProcessingTimeoutRecoveryJob` 以及对应测试。

## 目标

本次设计目标是以最小架构扰动补齐以下能力：

- 让 `ActiveTaskQueue` 成为真正的“弹出、阻塞、再激活、重新排序”调度队列
- 让 `PartitionSchedulerWorker` 在每轮派发后做明确的 `reactivate / block / inactive` 决策
- 让 `DispatchConcurrencyLimiter` 在主派发链路中真正执行并发占用与回滚
- 让运行中任务在导入完成后立即重新进入调度

## 非目标

- 不重写 Redis key 模型
- 不引入新的调度中间件
- 不重做 `RedisDialUnitQueue` 三段队列结构
- 不实现任务自动 `FINISHED`
- 不引入线路级限流或 predictive dialing

## 方案对比

### 方案 A：在现有架构上补齐闭环

做法：

- 扩展 `ActiveTaskQueue` 的 poll/block/reactivate 语义
- 扩展 `DispatchConcurrencyLimiter` 为批量授权与回滚
- 调整 `PartitionSchedulerWorker` 的一轮调度决策
- 在 `CallTaskImportService` 中补导入后激活

优点：

- 兼容当前架构
- 改动范围集中
- 测试面清晰
- 风险最低

缺点：

- 需要谨慎处理状态迁移和并发计数回滚

### 方案 B：把公平调度和并发控制下沉到 Lua

做法：

- 通过 Lua 一次完成 task 选择、额度占用、号码 claim、索引更新

优点：

- Redis 原子性最强

缺点：

- Lua 逻辑复杂
- Java 单测和维护成本显著上升
- 与当前仓库代码风格不匹配

### 方案 C：重做独立调度协调器

做法：

- 将活跃任务队列、并发控制、再入队全部抽成新协调器

优点：

- 模型最整洁

缺点：

- 接近重构
- 回归范围太大
- 交付速度慢

## 推荐方案

推荐采用 `方案 A`。

原因：

- 当前系统的正确架构骨架已经存在
- 问题主要集中在调度闭环的关键控制点未收口
- 没必要用更重的方案替换现有分区、热窗口、回写闭环模型

## 设计细节

### 1. ActiveTaskQueue 改造

当前问题：

- `pollNextTask()` 只读不删
- `TaskSchedulingState` 和 `TaskBlockReason` 没有真正参与控制
- `fairScore` 没有保存在任务元数据中

改造目标：

- 让 `ActiveTaskQueue` 成为真正的调度队列

建议新增/调整能力：

- `pollNextTask(partition)`
  - 返回当前最小 `fairScore` 的 task
  - 并从 `call:scheduler:partition:{p}:active` 中移除
- `upsertMeta(...)`
  - 补写 `fairScore`
- `block(taskId, reason)`
  - 将 meta 中 `state` 改为 `BLOCKED`
  - 写入 `blockedReason`
- `reactivate(taskId, fairScore)`
  - 将 meta 中 `state` 改回 `ACTIVE`
  - 更新 `fairScore`
  - 重新加入 active ZSET
- `deactivate(taskId)`
  - 将 meta 中 `state` 改为 `INACTIVE`

状态语义：

- `ACTIVE`
  - 当前应继续参与调度
- `BLOCKED`
  - 当前不应继续调度，等待外部事件重新激活
- `INACTIVE`
  - 任务暂停、完成或不应再被异步事件唤醒

阻塞原因：

- `CONCURRENCY_FULL`
- `EMPTY`
- `PAUSED`

### 2. PartitionSchedulerWorker 改造

当前问题：

- worker 只读任务，不真正消费 active queue
- 没有一轮派发后的显式 requeue/block 决策
- `fairScore` 没有增长
- 并发额度只读取可用值，不真正占用

改造目标：

- 让 worker 成为完整的一轮调度执行器

新的调度循环：

1. `poll` 出 partition 当前队头 task
2. 读取 meta 和任务实体
3. 如果任务不是 `RUNNING`，标记 `PAUSED/INACTIVE`
4. 预加载 Redis 热窗口
5. 计算理论预算 `requested`
6. 向 limiter 申请实际预算 `granted`
7. 用 `granted` 去 claim `ready`
8. 用 claim 结果推进 DB `QUEUED -> DIALING`
9. 回滚多占用的并发额度
10. 发布 MQ
11. 根据本轮结果决定 `reactivate / block / deactivate`

一轮结束后的决策规则：

- `dispatched > 0` 且任务仍可能继续调度
  - `reactivate(taskId, nextFairScore)`
- `dispatched > 0` 且并发已满
  - `block(CONCURRENCY_FULL)`
- `dispatched == 0` 且 ready 为空
  - `block(EMPTY)`
- 任务暂停或不再 `RUNNING`
  - `block(PAUSED)` 或 `deactivate`

### 3. 公平调度算法

当前 `TaskPriorityWeight` 已经定义：

- `P1 -> 16`
- `P2 -> 8`
- `P3 -> 4`
- `P4/default -> 2`

本次不改变映射，只补齐 fair score 演进。

建议：

- `TaskSchedulingMeta` 新增 `fairScore`
- 初始激活使用 `fairScore = 0`
- 每次成功派发后更新：

`nextFairScore = currentFairScore + dispatched * SCALE / weight`

其中：

- `dispatched` = 实际成功进入 `DIALING` 的数量
- `SCALE = 1000`

设计意图：

- 高优任务分值增长更慢，会更频繁被选中
- 低优任务分值增长更快，但不会永久饥饿
- 调度公平基于“实际成功服务量”，而不是 claim 数或理论预算

### 4. 并发控制改造

当前问题：

- `DispatchConcurrencyLimiter` 定义了 `tryAcquire()`，但主派发链路没有调用
- 回写与恢复路径会 `release()`，容易导致计数漂移

改造目标：

- 派发前真实占用，并在部分失败时回滚

建议能力：

- `tryAcquireBatch(tenantId, taskId, taskMaxConcurrency, requested)`
  - 返回 `granted`
- `releaseBatch(tenantId, taskId, count)`
  - 批量释放额度

授权逻辑：

- 全局上限
- 租户上限
- 任务上限

取三者共同约束下的可授权值。

回滚规则：

1. `granted > claimed`
  - 回滚 `granted - claimed`
2. `claimed > markedDialing`
  - 回滚 `claimed - markedDialing`

最终只有 `markedDialing` 条并发额度留存，等待：

- `DialResultWritebackService.release`
- `ProcessingTimeoutRecoveryJob.release`

### 5. 导入后激活

当前问题：

- `CallTaskImportService.importDialUnits()` 导入完成后不会重新激活运行中任务

改造方案：

- 导入完成后加载 task
- 如果 `task.status == RUNNING`
  - 调用 `TaskActivationService.activate(tenantId, taskId)`

这样可以保证：

- 运行中任务新增号码后立即可调度
- 暂停任务不会被错误唤醒

### 6. 事件边界

允许重新激活 task 的事件：

- `startTask`
- `resumeTask`
- 导入完成且任务为 `RUNNING`
- callback 成功
- callback 失败
- retry 到期
- processing 超时恢复成功

不允许重新激活的事件边界：

- 任务状态不是 `RUNNING`
- task 已被标记为 `PAUSED`
- task 已被标记为 `INACTIVE`

这样可以避免暂停任务被回调或恢复任务误唤醒。

## 影响文件

核心改造文件预计包括：

- `call-task/src/main/java/com/callcenter/task/dispatch/ActiveTaskQueue.java`
- `call-task/src/main/java/com/callcenter/task/dispatch/TaskSchedulingMeta.java`
- `call-task/src/main/java/com/callcenter/task/dispatch/PartitionSchedulerWorker.java`
- `call-task/src/main/java/com/callcenter/task/dispatch/DispatchConcurrencyLimiter.java`
- `call-task/src/main/java/com/callcenter/task/service/CallTaskImportService.java`

相关测试：

- `call-task/src/test/java/com/callcenter/task/dispatch/ActiveTaskQueueTest.java`
- `call-task/src/test/java/com/callcenter/task/dispatch/PartitionSchedulerWorkerTest.java`
- `call-task/src/test/java/com/callcenter/task/dispatch/DispatchConcurrencyLimiterTest.java`
- `call-task/src/test/java/com/callcenter/task/service/CallTaskImportServiceTest.java`
- `call-task/src/test/java/com/callcenter/task/service/DialResultWritebackServiceTest.java`
- `call-task/src/test/java/com/callcenter/task/dispatch/RetryQueueSchedulerTest.java`
- `call-task/src/test/java/com/callcenter/task/dispatch/ProcessingTimeoutRecoveryJobTest.java`

## 测试策略

### ActiveTaskQueue

- `poll` 会移除当前队头 task
- `reactivate` 会按新 `fairScore` 重新排序
- `block` 会更新 `state` 和 `blockedReason`

### PartitionSchedulerWorker

- 派发后会更新 `fairScore`
- 并发满时会 `block(CONCURRENCY_FULL)`
- ready 为空时会 `block(EMPTY)`
- `granted > claimed > marked` 时会正确回滚并发额度

### DispatchConcurrencyLimiter

- 批量占用返回正确 `granted`
- 超过任务/租户/全局上限时只授予部分额度或 0
- 回滚后计数恢复正确

### CallTaskImportService

- `RUNNING` 任务导入后会触发激活
- 非 `RUNNING` 任务导入后不会激活

### 回写与恢复链路

- 只有真实状态推进成功时才 `release + reactivate`

## 风险与控制

### 风险 1：并发计数释放不对称

控制措施：

- 所有释放都改成按实际成功推进状态的条数执行
- worker 内部显式回滚多申请的额度

### 风险 2：任务重复入队

控制措施：

- `poll` 时从 active ZSET 移除
- `reactivate` 统一通过单入口重新入队

### 风险 3：暂停任务被异步重新激活

控制措施：

- 激活入口统一检查任务状态
- `PAUSED` 任务只允许显式 `resumeTask` 重新进入调度

## 实施顺序

1. 先补 `TaskSchedulingMeta` 与 `ActiveTaskQueue`
2. 再补 `DispatchConcurrencyLimiter`
3. 再收口 `PartitionSchedulerWorker`
4. 然后补 `CallTaskImportService` 导入激活
5. 最后统一修测试与文档

## 结论

本次推荐方案是在现有分区调度架构上补齐关键闭环，而不是重写调度器。

最终目标不是让架构“更复杂”，而是让当前设计真正符合其原本意图：

- active queue 真正消费和重排
- fairness 基于真实派发量生效
- concurrency acquire/release 严格对称
- import / callback / retry / recover 都成为可靠的 reactivation 事件源
