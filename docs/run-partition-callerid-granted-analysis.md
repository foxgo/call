# runPartition 中 CallerId 选择与 granted 计算分析

## 1. 范围

本说明只聚焦 `runPartition` 里两段最关键的判断链：

- 智能选择 `CallerId`
- 计算并获取 `granted`

对应代码入口：

- `call-task/src/main/java/com/callcenter/task/dispatch/PartitionSchedulerWorker.java`
- `call-task/src/main/java/com/callcenter/task/caller/CallerIdCandidateService.java`
- `call-task/src/main/java/com/callcenter/task/caller/CallerIdSelector.java`
- `call-task/src/main/java/com/callcenter/task/dispatch/DispatchConcurrencyLimiter.java`

## 2. 智能选择 CallerId 的真实逻辑

这段逻辑不是简单“从池子里挑一个号码”，而是三段式：

1. 先把任务配置转成 `TaskCallerIdPolicy`
2. 再按策略组装候选号池
3. 最后按号码当前拨打阶段和历史统计打分，取最高分

### 2.1 候选号池怎么来

`CallerIdCandidateService.listCandidates(...)` 的行为：

- 先读取任务级 CallerId 绑定关系
- 从绑定里拆出：
  - `DENY` 黑名单
  - `ALLOW` 白名单和它的 `priorityBoost`
- 再根据 `callerIdMode` 决定是否纳入：
  - 共享池 `SHARED`
  - 任务白名单池
- 对每个候选 CallerId 继续过滤：
  - `caller == null` 或 `id == null`
  - 状态不是 `ACTIVE`
  - 在 `DENY` 里
  - `cooldownUntil > now`
- 通过过滤后，才会形成 `CallerIdCandidate`

因此，图里要把“模式选择”和“过滤规则”明确拆开，否则会误以为所有共享池号码都能参与竞争。

### 2.2 为什么要按尝试阶段预加载统计

`runPartition` 不会对每个号码都单独查一遍主叫统计，而是先：

- 从已领取号码里提取 `retryCount`
- 映射为 `AttemptStage`
- 对每个阶段批量加载 `callerId -> stats`

当前阶段只有两类：

- `FIRST_ATTEMPT`
- `RETRY_ATTEMPT`

这样每个号码在选择 CallerId 时，使用的是“当前拨打阶段”对应的统计，而不是混用一套总统计。

### 2.3 打分公式怎么工作

`CallerIdSelector.selectWithStats(...)` 的核心是：

- 对每个候选 CallerId 计算一个 `score`
- 最后取 `score` 最大的那个

参与打分的主要因子：

- `answerRate = answers / attempts`，无历史时默认 `0.5`
- `successRate = successes / attempts`
- `avgTalkSeconds = totalTalkSeconds / answers`
- `conversionProxy = min(avgTalkSeconds / 60, 1)`
- `trustScore`
- `costScore`
- `priorityBoost`
- `exposurePenalty`
- `failurePenalty`

公式可以概括为：

- 正向项：接通率、转化代理值、可信度、成功率、优先级加成
- 负向项：成本、曝光惩罚、失败码惩罚

最终 `CallerIdSelection` 会回填：

- `callerIdId`
- `callerId`
- `attemptStage`
- `score`
- `reason`

### 2.4 在 runPartition 里怎么落地

对每个 `claimedUnit`：

- 如果 `selectWithStats(...)` 返回空：
  - 该号码进入 `rejectedUnits`
  - 之后回退到 Redis ready
  - 并释放对应并发
- 如果选中：
  - 生成 `dispatchToken`
  - 写入 `selectedCallerId`
  - 写入 `selectedCallerNumber`
  - 写入 `callerIdSelectionScore`
  - 写入 `callerIdSelectionReason`
  - 写入 `attemptStage`
  - 放入 `selectedUnits`

## 3. CapacityControlJob 为什么会影响 granted

`DispatchConcurrencyLimiter.tryAcquireBatch(...)` 不是凭空算出 `poolTarget` 和 `taskTarget` 的，它依赖 `CapacityControlJob` 周期性把容量目标写回 Redis。

### 3.1 CapacityControlJob 的职责

`CapacityControlJob.recalculateTargets()` 每轮会做这些事：

1. 从 `CapacityProvider.snapshot()` 读取当前能力池快照
2. 扫描 `ActiveTaskQueue.listKnownMetas()`
3. 只保留任务状态仍是 `RUNNING` 的任务
4. 读取每个任务当前 `TaskTargetState`
5. 采集任务维度调度指标 `DispatchMetricsSnapshot`
6. 构造 `ControlInput`
7. 用 `CapacityControlEngine.decide(...)` 得到该任务的期望目标并发 `desiredTarget`
8. 把所有任务的 `desiredTarget + weight + maxConcurrency` 交给 `TaskTargetAllocator.allocate(...)`
9. 先把能力池总目标写入 `savePoolTarget(poolKey, capacitySnapshot.total())`
10. 再把每个任务的最终分配值写入 `saveTaskTarget(taskId, TaskTargetState(...))`
11. 如果某个任务之前因为 `CONCURRENCY_FULL` 被阻塞，且本轮分配值变大，还会重新 `activate`

### 3.2 这和 granted 的关系

`tryAcquireBatch(...)` 读取：

- `loadPoolTarget(poolKey)`
- `loadTaskTarget(taskId)`

也就是说：

- `poolTarget` 是 `CapacityControlJob` 写入的池级目标
- `taskTarget` 是 `CapacityControlJob` 写入的任务级目标

随后这两个目标会参与 `granted` 的最小值计算。

因此从因果上看：

- `CapacityControlJob` 决定“当前允许的目标上限”
- `DispatchConcurrencyLimiter` 决定“这一瞬间最多还能批出多少”

## 4. granted 是怎么得到的

`runPartition` 里先算：

- `requested = min(dispatchBatchSize, task.maxConcurrency)`

然后把它交给：

- `DispatchConcurrencyLimiter.tryAcquireBatch(...)`

### 4.1 Java 层先补两个动态目标

进入 Lua 之前，会先得到：

- `poolTarget`
  - 优先从 `TaskTargetConcurrencyRegistry.loadPoolTarget(poolKey)` 读取
  - 取不到时退回 `poolHardMax`
- `taskTarget`
  - 优先从 `TaskTargetConcurrencyRegistry.loadTaskTarget(taskId)` 读取
  - 取不到时退回 `taskMaxConcurrency`

因此，`granted` 不是只看静态任务并发，还会受到容量控制平面动态目标的影响。

### 4.2 Lua 里真正的 granted 公式

脚本先读四个当前在途计数：

- `currentGlobal`
- `currentPool`
- `currentTenant`
- `currentTask`

再分别计算剩余额度：

- `availableGlobal = globalMax - currentGlobal`
- `availablePool = poolTarget - currentPool`
- `availableTenant = tenantMax - currentTenant`
- `availableTask = taskMax - currentTask`
- `availableTaskTarget = taskTarget - currentTask`

然后：

- `granted = min(requested, availableGlobal, availablePool, availableTenant, availableTask, availableTaskTarget)`

也就是说，`granted` 本质上是“六个上限里最紧的那个”。

### 4.3 granted 小于等于 0 会怎样

如果 `granted <= 0`：

- Lua 直接返回 `0`
- Java 层调用 `recordRejectReason(...)`
- 按优先顺序打容量拒绝指标：
  - `global`
  - `tenant`
  - `pool`
  - `taskStatic`
  - `taskTarget`

然后 `runPartition` 会把任务标记成：

- `TaskBlockReason.CONCURRENCY_FULL`

### 4.4 granted 大于 0 会怎样

如果 `granted > 0`：

- Lua 对四个 busy key 同步 `INCRBY`
- 同时刷新 TTL
- Java 返回 `grantedValue`

后续在 `runPartition` 里还有两类差额回滚：

- `ids.size() < granted`
  - 说明领到的 ready 号码不足
  - 释放 `granted - ids.size()`
- `units.size() < selectedUnits.size()`
  - 说明 DB 状态迁移少于选中数量
  - 释放差额

## 5. 图示文件

本次输出两张流程图：

- `docs/diagrams/run-partition-callerid-selection-flow.*`
- `docs/diagrams/run-partition-granted-calculation-flow.*`
- `docs/diagrams/capacity-control-job-flow.*`

前者看“智能选号”，后者看“granted 从哪里来、受哪些上限约束”。
第三张图单独解释“这些动态目标是谁算出来并写入 Redis 的”。
