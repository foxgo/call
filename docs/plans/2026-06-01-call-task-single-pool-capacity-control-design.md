# Call Task Single Pool Capacity Control Design

**日期：** 2026-06-01  
**状态：** 已确认，待实现

## 背景

当前 `call-task` 已经具备稳定的任务调度执行面：

- `CallTaskDispatcher` 定时驱动 owned partitions
- `PartitionSchedulerWorker` 执行单分区派发闭环
- `DispatchConcurrencyLimiter` 维护全局、租户、任务三级静态并发额度
- `DialResultWritebackService` 和 `DialingRecoveryJob` 负责释放并发并重新激活任务
- `TaskActivationService` 和 `ActiveTaskQueue` 负责任务再入队和公平调度

这套架构已经解决了“怎么派发”的问题，但还没有解决“系统此刻允许派发多少”的问题。当前并发控制仍是静态上限模型，无法根据共享 AI 能力池负载动态收缩或放大，也无法在多任务竞争同一 provider 资源时做统一控制。

用户已经确认本阶段目标为：

- P0 先做“单 AI 能力池”建模
- 在单池之上叠加任务级动态并发
- 架构上预留未来升级为“线路 + LLM + ASR/TTS 多池联合控制”的扩展位

## 目标

- 在不重写现有分区调度器的前提下，为 `call-task` 增加生产可用的动态容量控制面
- 将共享 provider 容量池收敛为唯一全局硬约束，避免多任务同时放量打穿 AI 能力池
- 为每个任务计算动态 `targetConcurrency`，替代纯静态 `maxConcurrency`
- 保留现有 `MySQL + Redis + RocketMQ` 执行链路和事件驱动再激活模型
- 为后续扩展多资源池建模预留清晰接口

## 非目标

- P0 不实现多资源联合控制
- P0 不实现按任务画像估算 LLM/ASR/TTS 单呼叫资源成本
- P0 不引入新的调度中间件、工作流引擎或独立 scheduler 服务
- P0 不把调度入口改为额外的 `dispatch.command` MQ 命令总线
- P0 不替换现有 `ActiveTaskQueue`、分区 lease 或 `PartitionSchedulerWorker`

## 方案对比

### 方案 A：只做任务级动态并发

做法：

- 每个任务独立根据接通率、占用率等指标调整自己的 `targetConcurrency`
- 不引入共享 provider 容量池

优点：

- 改动最小
- 指标面更简单

缺点：

- 无法控制跨任务资源争抢
- 容易出现多个任务同时上调导致 provider 过载
- 与共享 AI/线路资源的真实瓶颈不匹配

### 方案 B：单 AI 能力池 + 任务动态并发

做法：

- 先建立单一共享 provider 容量池
- 由控制面计算池级目标并发，再按任务分摊为 `taskTargetConcurrency`
- 主派发链路继续通过当前 worker 执行

优点：

- 能控制全局共享资源
- 改造范围仍然集中
- 与现有 `DispatchConcurrencyLimiter` 演进路径最匹配
- 后续可以平滑升级到多池

缺点：

- 比纯任务级控制多一层分配逻辑
- 需要补齐池级指标和控制态存储

### 方案 C：直接做多资源池联合控制

做法：

- 同时维护线路、LLM、ASR/TTS 等多个资源池
- 调度时按最小剩余量决定系统允许并发

优点：

- 模型长期最正确
- 资源利用率潜力最高

缺点：

- P0 指标、归一化、控制稳定性风险明显更高
- 实现复杂度和排障成本都过早放大

## 推荐方案

推荐采用 `方案 B`。

原因：

- 它比“只做任务级控制”更符合共享 provider 资源的真实约束
- 它比“直接做多池控制”更适合作为 P0 落地
- 它不要求重写现有调度执行面，只需要把静态配额闸门升级为动态目标配额闸门

## 总体架构

P0 采用“执行面保持不变，新增控制面”的结构。

```text
+-------------------------------+
| CallTask Execution Plane      |
|                               |
|  CallTaskDispatcher           |
|  PartitionSchedulerWorker     |
|  ActiveTaskQueue              |
|  RedisDialUnitQueue           |
|  DialResultWritebackService   |
+---------------+---------------+
                |
                v
+-------------------------------+
| Capacity Control Plane        |
|                               |
|  SinglePoolCapacityProvider   |
|  DispatchMetricsCollector     |
|  CapacityControlEngine        |
|  TaskTargetAllocator          |
|  TaskTargetConcurrencyRegistry|
+---------------+---------------+
                |
                v
+-------------------------------+
| DispatchConcurrencyLimiter    |
| global/provider/task limits   |
+-------------------------------+
```

设计原则：

- `CallTaskDispatcher` 和 `PartitionSchedulerWorker` 仍然负责“何时从 active queue 取任务并尝试派发”
- 控制面只负责“当前允许派发多少”
- `DispatchConcurrencyLimiter` 成为执行面和控制面的交汇点

## 组件设计

### 1. SinglePoolCapacityProvider

职责：

- 对外提供单 AI 能力池容量快照
- 统一抽象当前 provider 总容量、已用量、健康分和可用性

建议接口：

```java
public interface CapacityProvider {

    CapacitySnapshot snapshot();

    boolean available();

    double healthScore();
}
```

P0 数据结构：

```java
public record CapacitySnapshot(
    String poolKey,
    int total,
    int busy,
    int idle,
    double utilization,
    double healthScore,
    java.time.Instant updatedAt
) {}
```

实现说明：

- `poolKey` 在 P0 固定为单池，例如 `ai-default`
- `total` 可来自静态配置或 provider 上报
- `busy` 可由外部 AI 会话数、已占用 provider 槽位或聚合中的在途数提供
- `utilization = busy / total`
- `healthScore` 可由线路成功率、超时率、错误率聚合得到

### 2. DispatchMetricsCollector

职责：

- 每 5s 采集控制所需指标
- 聚合任务级和池级观测值

建议输出：

```java
public record DispatchMetrics(
    long taskId,
    double connectRate,
    double occupancy,
    double poolUtilization,
    double trunkHealth,
    double llmLoad,
    long activeCalls,
    long completedCalls,
    long remainingCalls,
    java.time.Instant timestamp
) {}
```

P0 取数建议：

- `activeCalls` 先复用 Redis 中任务并发计数
- `completedCalls`、回写成功/失败来自现有回写链路埋点
- `remainingCalls` 来自号码表或 ready/pending 聚合
- `connectRate` 使用 EWMA 平滑
- `occupancy` 可先近似为 `activeCalls / taskTargetConcurrency`
- `poolUtilization` 直接来自 `CapacitySnapshot`
- `llmLoad`、`trunkHealth` 暂时由 provider 适配层暴露

### 3. CapacityControlEngine

职责：

- 每 10s 根据池快照和任务指标计算新的目标并发
- 负责闭环控制，不直接操作号码队列

建议输入：

```java
public record ControlInput(
    DispatchMetrics metrics,
    CapacitySnapshot capacity,
    TaskPolicy policy,
    int currentConcurrency,
    int currentTargetConcurrency
) {}
```

建议输出：

```java
public record ControlDecision(
    int targetConcurrency,
    String reason
) {}
```

P0 控制公式沿用已确认设计：

```text
Ct = C0 x Fc x Fo x Ft x Fl
```

其中：

- `C0` 为任务基准并发，可先取 `min(task.maxConcurrency, taskAllocatedBase)`
- `Fc` 为接通率因子
- `Fo` 为占用率因子
- `Ft` 为线路健康因子
- `Fl` 为 LLM 负载因子

### 4. 防抖控制

P0 必须包含以下控制保护：

- 冷却时间：`30s`
- 最大步长：每次只允许 `+-10%`
- 死区：变化幅度低于 `5%` 不调整
- Clamp：将新目标限制在上次目标的 `0.9x ~ 1.1x`

这部分的优先级高于“尽快拉满利用率”。

### 5. TaskTargetAllocator

职责：

- 在池级容量上限之内，为活跃任务分配任务目标并发

P0 建议策略：

- 先算出 `poolTargetConcurrency`
- 再按任务权重和当前活跃状态分配 `taskTargetConcurrency`
- 单任务上限始终不超过：
  - `task.maxConcurrency`
  - 任务剩余号码支撑的合理并发
  - 任务级硬限制

P0 不追求复杂最优解，采用简单稳定的分配规则即可：

- 为每个活跃任务先分配一个最小基线
- 剩余额度按 `TaskPriorityWeight` 比例分配
- 如果某任务长期 `EMPTY` 或 `PAUSED`，不分配或快速回收额度

### 6. TaskTargetConcurrencyRegistry

职责：

- 保存池级目标和任务级目标
- 为 limiter 和观测层提供只读访问

P0 建议先用 Redis 做热状态：

- `call:capacity:pool:{poolKey}:target`
- `call:capacity:task:{taskId}:target`
- `call:capacity:task:{taskId}:control-meta`

必要字段：

- `targetConcurrency`
- `updatedAt`
- `reason`
- `cooldownUntil`
- `lastDecisionBasis`

如需持久化审计，可在后续增加 MySQL 状态表，但不是 P0 必选项。

## 对现有执行面的改造

### 1. DispatchConcurrencyLimiter

当前 [call-task/src/main/java/com/callcenter/task/dispatch/DispatchConcurrencyLimiter.java](/Users/johnny/github/call/call-task/src/main/java/com/callcenter/task/dispatch/DispatchConcurrencyLimiter.java:74) 仅考虑：

- `globalMax`
- `tenantDefaultMax`
- `taskMaxConcurrency`

P0 改造后需同时考虑：

- 全局硬上限
- 单池 provider 硬上限
- 单池当前目标并发
- 租户上限
- 任务静态上限
- 任务动态目标并发

建议授予规则：

```text
granted =
min(
  requested,
  remainingGlobalHardCap,
  remainingPoolTargetCap,
  remainingTenantCap,
  remainingTaskStaticCap,
  remainingTaskTargetCap
)
```

其中 `remainingTaskTargetCap = taskTargetConcurrency - currentTaskInflight`。

### 2. PartitionSchedulerWorker

当前 [call-task/src/main/java/com/callcenter/task/dispatch/PartitionSchedulerWorker.java](/Users/johnny/github/call/call-task/src/main/java/com/callcenter/task/dispatch/PartitionSchedulerWorker.java:75) 的 `requested` 仍可保留为批量派发预算，但 `granted` 需要基于动态目标计算。

调整要求：

- `requested` 仍由 `dispatchBatchSize` 和任务静态并发决定
- `granted` 由 limiter 按池级和任务级动态额度裁剪
- 当池目标收缩导致无法继续申请额度时，任务进入 `CONCURRENCY_FULL`
- 当控制面上调任务目标时，任务应重新进入 active queue

### 3. TaskActivationService

当前 [call-task/src/main/java/com/callcenter/task/dispatch/TaskActivationService.java](/Users/johnny/github/call/call-task/src/main/java/com/callcenter/task/dispatch/TaskActivationService.java:34) 已经是统一再激活入口。

P0 需要新增一类事件源：

- `taskTargetConcurrency` 上调后，若任务此前因 `CONCURRENCY_FULL` 被阻塞，则重新激活

### 4. 回写与恢复链路

以下现有链路保留不变：

- [DialResultWritebackService.java](/Users/johnny/github/call/call-task/src/main/java/com/callcenter/task/service/DialResultWritebackService.java:46)
- [DialingRecoveryJob.java](/Users/johnny/github/call/call-task/src/main/java/com/callcenter/task/dispatch/DialingRecoveryJob.java:47)

它们继续负责：

- 释放实际已结束的在途并发
- 当任务再次具备派发可能时重新激活任务

控制面不直接管理单条号码状态，只管理目标并发。

## 配置设计

建议新增配置：

```yaml
call:
  task:
    capacity:
      control-interval: PT10S
      metrics-interval: PT5S
      cooldown: PT30S
      deadband-ratio: 0.05
      max-adjust-ratio: 0.10
      pool-key: ai-default
      pool-hard-max: 1000
      task-min-target: 1
      task-base-share: 1
      ewma-alpha: 0.25
```

保留现有：

- `call.task.dispatch.*`
- `call.task.concurrency.*`

但要明确 `call.task.concurrency.global-max` 成为更外层的全局硬上限，而 `pool-hard-max` 是单池共享上限。

## 可观测性

P0 至少补齐以下指标：

- `call.task.capacity.pool.target`
- `call.task.capacity.pool.busy`
- `call.task.capacity.pool.utilization`
- `call.task.capacity.task.target`
- `call.task.capacity.control.decision`
- `call.task.capacity.control.cooldown.skipped`
- `call.task.capacity.control.deadband.skipped`
- `call.task.capacity.limit.pool.rejected`
- `call.task.capacity.limit.task.rejected`

同时建议在控制决策日志中记录：

- `taskId`
- `currentTarget`
- `newTarget`
- `poolTarget`
- `reason`
- `connectRate`
- `occupancy`
- `trunkHealth`
- `llmLoad`

## 失败模式与保护

- provider 快照缺失
  - 降级为保守固定池目标，不允许自动放大
- 指标突刺
  - 通过 EWMA、冷却、最大步长和死区削峰
- 控制状态丢失
  - Redis 热状态丢失后可回退到静态上限，不影响主链路可用性
- 任务大面积因容量阻塞
  - 依赖控制面上调和回写事件继续激活，不引入全量扫描

## 向多池模型的扩展位

P0 虽然只实现单池，但接口设计必须允许平滑升级。

要求：

- `CapacityProvider` 不暴露单池专用方法
- `CapacitySnapshot` 可在后续扩为多资源快照集合
- `TaskTargetAllocator` 不把分配逻辑写死在 limiter 中
- 配置层允许未来增加多个 pool 定义

P1 预期演进：

- `SinglePoolCapacityProvider -> CompositeCapacityProvider`
- `CapacitySnapshot -> Map<ResourceType, ResourceSnapshot>`
- `poolTargetConcurrency -> min(resourceTargets...)`

## 实施结论

P0 应采用“单 AI 能力池 + 任务动态并发 + 现有执行面不变”的方案。

落地重点不是重写调度器，而是：

1. 增加池级和任务级控制状态
2. 通过控制引擎周期性计算目标并发
3. 让 `DispatchConcurrencyLimiter` 按动态目标授予额度
4. 通过现有任务激活机制承接容量变化后的继续调度

这条路径风险最低，也最符合当前仓库已经成型的 `call-task` 架构。
