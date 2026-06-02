# CallTaskDispatcher 调度与下发逻辑分析

## 1. 分析范围

本文基于当前代码实现，聚焦以下类的真实行为：

- `call-task/src/main/java/com/callcenter/task/dispatch/CallTaskDispatcher.java`
- `call-task/src/main/java/com/callcenter/task/dispatch/PartitionSchedulerWorker.java`
- `call-task/src/main/java/com/callcenter/task/dispatch/TaskPartitionManager.java`
- `call-task/src/main/java/com/callcenter/task/dispatch/ActiveTaskQueue.java`
- `call-task/src/main/java/com/callcenter/task/dispatch/DispatchConcurrencyLimiter.java`
- `call-task/src/main/java/com/callcenter/task/dispatch/RedisDialUnitQueue.java`
- `call-task/src/main/java/com/callcenter/task/dispatch/AsyncDialDispatchService.java`
- `call-task/src/main/java/com/callcenter/task/dispatch/DialDispatchCompensationService.java`
- `call-task/src/main/java/com/callcenter/task/service/DialResultWritebackService.java`

## 2. 一句话结论

`CallTaskDispatcher` 只负责把“当前实例持有的 partition”投递到线程池里执行，并保证同一个 partition 同时最多只有一个 `drainPartition` 在跑；真正的号码领取、并发占位、选号、状态迁移、异步下发，以及失败补偿和回写释放并发，都发生在 `PartitionSchedulerWorker` 及其后续链路中。

## 3. 外层调度逻辑

`CallTaskDispatcher` 的职责很窄，但它决定了整个调度面的并发模型：

1. `@Scheduled` 定时执行 `dispatchOwnedPartitions()`。
2. `TaskPartitionManager.ownedPartitions()` 返回当前实例持有租约的 partition 列表。
3. 对每个 partition 调用 `submitPartition(partition)`。
4. `runningPartitions` 用 `AtomicBoolean` 做单 partition 并发闸门。
5. 如果某个 partition 已经有任务在执行，本轮直接跳过，不重复提交。
6. 如果成功占位，则把 `drainPartition(partition)` 丢给 `callTaskDispatchExecutor` 异步执行。
7. `drainPartition` 内部最多执行 `maxTasksPerPartitionTick` 次 `partitionSchedulerWorker.runPartition(partition)`。
8. 只要 `runPartition` 返回 `false`，说明该 partition 当前没有活可做，本轮立即停止；返回 `true` 则继续下一次。

这意味着：

- 调度粒度不是“任务”，而是“partition”。
- 外层定时器不直接做重活，只负责投递。
- 同一个 partition 不会被本实例并发重复 drain。
- 单次 tick 对单个 partition 的工作量受 `maxTasksPerPartitionTick` 限制。

## 4. 内层下发逻辑

`PartitionSchedulerWorker.runPartition(partition)` 才是主派发链路，执行顺序如下：

1. 从 `ActiveTaskQueue.popMin` 取出当前 partition 下 fair score 最小的活跃任务。
2. 如果没取到任务，直接返回 `false`，让 `CallTaskDispatcher` 停止该 partition 本轮 drain。
3. 根据任务元数据加载真实任务实体。
4. 如果任务状态不是 `RUNNING`，把任务标记为 `BLOCKED(PAUSED)`，返回 `true`。
5. 调用 `DialUnitPreloadService.preloadRunningTask(task)`，必要时把 MySQL 中的 `PENDING` 号码搬进 Redis `ready` 热窗口。
6. 计算本轮请求并发 `requested = min(dispatchBatchSize, task.maxConcurrency)`。
7. 调用 `DispatchConcurrencyLimiter.tryAcquireBatch(...)` 申请并发额度。
8. 如果一个额度都拿不到，把任务标记为 `BLOCKED(CONCURRENCY_FULL)`，返回 `true`。
9. 根据分片路由调用 `RedisDialUnitQueue.claimReady(...)` 领取 ready 号码。
10. 如果一个号码都没领到，释放刚申请到的并发额度，标记 `BLOCKED(EMPTY)`，返回 `true`。
11. 如果实际领取数量少于 `granted`，先把多占的并发额度释放掉。
12. 批量加载领取到的号码实体，并加载任务级 Caller ID 策略、候选号和分层统计。
13. 对每个号码执行 Caller ID 选择：
14. 选不中的号码回退到 ready，并释放对应并发额度。
15. 选中的号码写入 `dispatchToken`、所选主叫号码、评分和 attempt stage。
16. 通过 `CallDialUnitRepository.markDialingSelectionsFromReady(...)` 用条件更新把号码从 `READY` 推进到 `DIALING`。
17. 状态迁移失败或缺失的号码重新放回 ready；对应的并发额度按差额回滚。
18. 对真正进入 `DIALING` 的号码逐条调用 `AsyncDialDispatchService.submit(...)`。
19. 如果最终成功进入 `DIALING` 的号码数为 0，标记 `BLOCKED(EMPTY)`，返回 `true`。
20. 根据本轮实际派发量推进 `fairScore`。
21. 如果 ready 号码不足，标记 `BLOCKED(EMPTY)`。
22. 如果并发额度没拿满，标记 `BLOCKED(CONCURRENCY_FULL)`。
23. 否则把任务以新的 `fairScore` 重新激活，等待下一轮继续调度。

这里有两个关键点：

- `runPartition = true` 不代表本轮一定发出了号码，只表示“dispatcher 可以继续处理这个 partition 的下一次循环”。
- 只有在活跃队列里已经没有任务时，`runPartition` 才返回 `false`，从而中断外层 `drainPartition`。

## 5. 异步下发、补偿与回写闭环

号码进入 `DIALING` 后，并不等于真正下发成功，后面还有两段闭环：

### 5.1 异步下发

`AsyncDialDispatchService.submit(...)` 会把发送动作放到独立线程池执行：

1. 先做 `DispatchUnitValidator.validate(unit)`。
2. 再做 `DispatchGateService.evaluate(...)`。
3. 只有校验和闸门都通过，才调用 `DialDispatchPublisher.publish(unit)` 发给外部拨号链路。

### 5.2 下发失败补偿

如果发送线程池拒绝、校验失败、闸门拒绝或发布异常：

1. `DialDispatchCompensationService.compensateFailedDispatch(...)`
2. 把号码从 `DIALING` 条件回滚到 `READY`
3. 重新放回 Redis ready 队列
4. 释放对应并发额度

这一步确保“号码已标记 DIALING 但实际没发出去”时不会把并发永久占死。

### 5.3 拨号结果回写

外部拨号链路回调后，`DialResultWritebackService.handleCallback(...)`：

1. 用 `dispatchToken` 找到当前仍处于 `DIALING` 的号码。
2. 成功回调则更新为 `SUCCESS`。
3. 失败回调则更新失败信息，并根据策略进入重试决策。
4. 无论成功还是失败，只要回写被接受，都会释放并发额度。
5. 之后重新激活任务，让它有机会继续领取下一批号码。

因此，真正完整的闭环是：

`活跃任务 -> 领取号码 -> DIALING -> 异步下发 -> 成功回写/失败补偿 -> 释放并发 -> 重新激活任务`

## 6. 关键控制点

### 6.1 防重入

- `TaskPartitionManager` 负责跨实例 partition lease。
- `CallTaskDispatcher.runningPartitions` 负责单实例内 partition 级防重入。

两层合起来，避免同一 partition 被重复 drain。

### 6.2 并发一致性

并发额度不是简单内存计数，而是 Redis 里的全局/租户/任务/池级联合约束。

回滚路径至少有四类：

- `claimReady` 为空，释放整批额度
- `claimReady` 数量少于 `granted`，释放差额
- 选号失败或 DB 状态迁移失败，释放对应差额
- 异步下发失败或回写完成，释放最终占用

### 6.3 状态真相

- Redis `ready` 队列只是高频调度面
- MySQL `READY -> DIALING -> SUCCESS/FAILED` 才是真实状态推进
- `dispatchToken` 是异步下发和回写幂等对账的核心字段

## 7. 图示文件

已生成以下图示：

- `docs/diagrams/call-task-dispatcher-analysis-sequence.*`
- `docs/diagrams/call-task-dispatcher-analysis-flow.*`

其中：

- 时序图更适合看“谁在调用谁”
- 流程图更适合看 `runPartition` 的判断分支与回滚点
