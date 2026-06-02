# Caller ID Selection Design

**日期：** 2026-06-02  
**状态：** 已确认，待实现

## 背景

当前仓库中的智能外呼调度已经在 `call-task` 模块完成主执行闭环：

- `CallTaskService` 负责任务创建、启动、暂停、恢复
- `TaskActivationService` 负责把任务激活进分区活跃队列
- `PartitionSchedulerWorker` 负责从 ready 队列 claim 客户号码、落库为 `DIALING`、投递 MQ
- `DialDispatchPublisher` 负责把派发消息送入拨号链路
- `DialResultWritebackService` 负责处理拨号回调、释放并发、安排重试、再激活任务

这套架构已经解决了“客户号码如何被稳定调度出去”的问题，但还没有解决“这次外呼应该用哪个主叫号码”的问题。当前派发消息只带：

- `dialUnitId`
- `tenantId`
- `taskId`
- `phone`
- `dispatchToken`

没有主叫号码选择、号码画像、号码健康度、任务级 Caller ID 策略、以及回调结果对主叫号码的反馈归因。

用户已确认第一版约束如下：

- 按任务设置优化优先级
- 主叫号码池采用混合模式：租户共享池 + 任务白名单/黑名单
- 回调实时可拿到：振铃、接通、时长、失败码
- 首拨和重拨必须分开建模
- 第一版重拨定义为 `retryCount > 0`

## 目标

- 在现有 `call-task` 调度架构中增加生产可用的 Caller ID 选择能力
- 使任务可以按接通率、转化率、成本、风险四类目标设置优先级
- 为每次外呼选择合适的主叫号码，并把选择结果持久化
- 根据拨号回调实时更新主叫号码健康分，形成可持续学习的反馈闭环
- 在第一版中把主叫号码评分拆分为首拨与重拨两套视图，避免样本混算

## 非目标

- 第一版不引入 Contextual Bandit 或强化学习
- 第一版不单独拆出 Caller ID Selection 微服务
- 第一版不依赖 Flink、Kafka Feature Store、实时画像平台
- 第一版不做复杂线路实时质量联合建模
- 第一版不处理投诉、标记、黑名单命中的在线反馈，这些后续可通过离线回灌补齐
- 第一版不强依赖被叫用户地域与运营商画像，如果未来可稳定获得再叠加

## 方案对比

### 方案 A：导入时预绑定主叫号码

做法：

- 名单导入时就为每个 `dialUnit` 提前写入 `selectedCallerId`
- 调度阶段不再实时选择

优点：

- 改造最小
- 派发路径简单

缺点：

- 无法利用派发时刻的实时号码健康度与冷却状态
- 首拨与重拨共用同一绑定结果，无法体现分层评分
- 号码在 ready 队列等待期间状态可能已经变化

### 方案 B：在 `PartitionSchedulerWorker` 内嵌实时选择

做法：

- `claimReady` 后加载 `dialUnit`
- 结合任务策略、拨次上下文、号码池和号码健康分实时选择主叫号码
- 回写阶段更新号码健康分

优点：

- 最贴合现有调度架构
- 闭环完整，数据一致性最好
- 便于以后平滑升级为更复杂模型

缺点：

- 派发链路要增加候选号过滤与评分步骤
- 需要补齐任务策略、号码资产、号码统计三类数据模型

### 方案 C：独立 Caller ID Selection Service

做法：

- `call-task` 派发前请求独立选号服务
- 独立服务维护号码资产、特征和评分逻辑

优点：

- 领域边界清晰
- 长期更利于演进

缺点：

- 当前仓库尚无配套特征存储和缓存体系
- 第一版会引入额外调用链与一致性风险
- 与现有 `call-task` 内部状态耦合较深，短期收益低

## 推荐方案

推荐采用 `方案 B`：在现有 `call-task` 调度链路中内嵌实时 Caller ID 选择。

原因：

- 当前 `call-task` 已经掌握任务状态、号码状态、调度窗口和回写事件，天然处于最佳插入点
- 主叫号码是否真正“被用到”，应以 `markDialingFromReady` 成功为准，而不是以“算过一次分”为准
- 第一版的关键是先形成稳定闭环，而不是先追求组件边界完美

## 核心建模

Caller ID 选择问题的第一版抽象为：

```text
CallerId = f(taskPolicy, attemptStage, candidatePool, callerHealth, exposure, cooldown)
```

其中：

- `taskPolicy`：任务级优化目标和权重
- `attemptStage`：首拨或重拨
- `candidatePool`：租户共享池与任务绑定池过滤后的候选号集合
- `callerHealth`：号码在当前拨次阶段的接通、时长、失败码统计结果
- `exposure`：近窗口曝光次数
- `cooldown`：同任务同号码的冷却约束

### 拨次分层

第一版把拨次分为两层：

- `FIRST_ATTEMPT`
  - 条件：`retryCount == 0`
- `RETRY_ATTEMPT`
  - 条件：`retryCount > 0`

所有号码健康分、接通率、平均时长、失败码惩罚都按拨次分层维护，禁止混算。

## 总体架构

```text
+------------------------------------+
| call-task execution path           |
|                                    |
|  PartitionSchedulerWorker          |
|    -> claimReady                   |
|    -> load dial units              |
|    -> select caller id             |
|    -> mark dialing                 |
|    -> publish dispatch message     |
+-----------------+------------------+
                  |
                  v
+------------------------------------+
| Caller ID selection components     |
|                                    |
|  TaskCallerIdPolicyService         |
|  CallerIdCandidateService          |
|  CallerIdSelector                  |
|  CallerIdHealthService             |
+-----------------+------------------+
                  |
                  v
+------------------------------------+
| Persistence                        |
|                                    |
|  call_task                         |
|  call_dial_unit_xx                 |
|  call_caller_id                    |
|  call_task_caller_id_binding       |
|  call_caller_id_stats              |
+------------------------------------+
```

设计原则：

- `DialUnitPreloadService` 继续只负责 `PENDING -> READY`，不做选号
- `PartitionSchedulerWorker` 负责派发时刻实时选号
- `DialResultWritebackService` 负责回调结果归因与号码健康分更新

## 数据模型设计

### 1. 扩展 `call_task`

为任务增加 Caller ID 策略字段：

- `caller_id_mode`
- `optimization_goal`
- `answer_weight`
- `conversion_weight`
- `cost_weight`
- `risk_weight`
- `local_presence_enabled`
- `same_caller_cooldown_seconds`
- `max_caller_exposure_per_hour`

含义：

- `caller_id_mode` 取值建议：`SHARED_ONLY`、`TASK_ONLY`、`HYBRID`
- `optimization_goal` 取值建议：`ANSWER`、`CONVERSION`、`COST`、`RISK`
- 四个权重用于最终评分加权
- `local_presence_enabled` 为后续本地号匹配预留
- 冷却与曝光阈值用于第一版基础风控

### 2. 扩展 `call_dial_unit_xx`

增加派发归因与反馈字段：

- `selected_caller_id`
- `caller_id_selection_score`
- `caller_id_selection_reason`
- `attempt_stage`
- `ring_duration_seconds`
- `talk_duration_seconds`
- `hangup_code`

作用：

- 记录每次外呼到底选了哪个主叫号码
- 保存选择得分与原因，便于排障与回溯
- 保存回调反馈，用于后续号码统计回灌

### 3. 新增 `call_caller_id`

主叫号码资产表字段建议：

- `id`
- `tenant_id`
- `caller_id`
- `pool_type`
- `carrier`
- `province_code`
- `city_code`
- `cost_score`
- `trust_score`
- `status`
- `cooldown_until`
- `last_used_at`
- `created_at`
- `updated_at`

作用：

- 管理租户共享号码与任务专属号码
- 保存静态画像与运营状态

### 4. 新增 `call_task_caller_id_binding`

任务与号码关系表字段建议：

- `id`
- `tenant_id`
- `task_id`
- `caller_id_id`
- `binding_type`
- `priority_boost`
- `created_at`
- `updated_at`

含义：

- `binding_type` 取值建议：`ALLOW`、`DENY`
- `priority_boost` 用于白名单内微调排序

### 5. 新增 `call_caller_id_stats`

号码统计聚合表按 `caller_id + attempt_stage + time_bucket` 维度维护：

- `tenant_id`
- `caller_id_id`
- `attempt_stage`
- `time_bucket`
- `attempt_count`
- `ring_count`
- `answer_count`
- `success_count`
- `total_talk_seconds`
- `failure_code_summary`
- `health_score`
- `updated_at`

说明：

- 第一版按小时桶或天桶均可，优先小时桶
- `failure_code_summary` 可先用 JSON 文本或聚合字符串保存
- `health_score` 为当前窗口平滑结果，便于实时读取

## 调度链路设计

### 当前链路

当前 `PartitionSchedulerWorker` 的主路径是：

1. 从 active queue 取任务
2. 预热 `PENDING -> READY`
3. claim ready 队列
4. `markDialingFromReady`
5. 发送 MQ

### 改造后链路

第一版改造为：

1. 从 active queue 取任务
2. 预热 `PENDING -> READY`
3. `RedisDialUnitQueue.claimReady(...)`
4. `CallDialUnitRepository.loadReadyUnitsForDispatch(...)`
5. `CallerIdCandidateService.listCandidates(...)`
6. `CallerIdSelector.select(...)`
7. `CallDialUnitRepository.markDialingFromReady(...)`
   - 同步写入 `selectedCallerId`
   - 同步写入 `selectionScore`
   - 同步写入 `selectionReason`
   - 同步写入 `attemptStage`
8. `DialDispatchPublisher.publish(...)`
   - 派发消息新增 `callerId`
   - 派发消息新增 `attemptStage`
9. `DialResultWritebackService.handleCallback(...)`
   - 更新 `dialUnit` 回调结果
   - 调用 `CallerIdHealthService.recordFeedback(...)`

### 为什么不在预热阶段选号

不把选号放在 `DialUnitPreloadService` 的原因：

- 预热阶段只知道“哪个客户号码该进 ready 队列”，不知道派发时刻的实时号码状态
- 号码的冷却、曝光、失败码惩罚具有时效性
- 同一个 `dialUnit` 重试时应按 `RETRY_ATTEMPT` 重新选择，而不是沿用首拨结果

因此：

- `预热` 负责把客户号码送入 ready
- `派发` 负责实时选主叫号码
- `回写` 负责更新主叫号码健康分

## 候选池设计

候选号码池按混合模式生成：

1. 先加载租户共享池内 `ACTIVE` 号码
2. 再叠加任务白名单内号码
3. 再排除任务黑名单内号码
4. 再排除冷却中号码
5. 再排除超曝光号码

模式差异：

- `SHARED_ONLY`
  - 只看共享池
- `TASK_ONLY`
  - 只看任务允许号码
- `HYBRID`
  - 共享池 + 任务白名单 - 黑名单

## 评分模型设计

第一版评分坚持“先过滤、后排序”。

### 过滤条件

以下号码直接不进入排序：

- 状态不是 `ACTIVE`
- 处于 `cooldown_until` 内
- 被任务黑名单排除
- 超出任务曝光上限
- 当前任务模式下不在允许池中

### 排序公式

第一版使用确定性加权评分：

```text
finalScore =
    healthScore(attemptStage)
  + matchScore
  + goalWeightedScore
  - exposurePenalty
  - cooldownPenalty
```

说明：

- `healthScore(attemptStage)`：按首拨或重拨读取对应号码评分
- `matchScore`：第一版主要保留任务白名单加分与后续地域匹配扩展位
- `goalWeightedScore`：由任务目标和权重决定
- `exposurePenalty`：近窗口曝光过高降分
- `cooldownPenalty`：接近冷却边界或重复使用时降分

### 任务目标解释

- `ANSWER`
  - 更看重接通率、振铃率、失败码惩罚
- `CONVERSION`
  - 第一版用长通话和成功通话近似转化能力
- `COST`
  - 提高低成本号码权重，但不能绕过健康度下限
- `RISK`
  - 严格执行曝光、冷却与失败码惩罚

### 首拨与重拨评分隔离

- 首拨时只读取 `FIRST_ATTEMPT` 的号码统计
- 重拨时只读取 `RETRY_ATTEMPT` 的号码统计
- 禁止用首拨表现推断重拨表现，反之亦然

## 回调与反馈闭环

第一版回调已确认可获得：

- 振铃
- 接通
- 通话时长
- 失败码

据此回调模型需要扩展：

- `ringDurationSeconds`
- `talkDurationSeconds`
- `hangupCode`

`DialResultWritebackService` 在原有成功/失败迁移之外，增加：

1. 将回调结果写回 `call_dial_unit_xx`
2. 调用 `CallerIdHealthService.recordFeedback(...)`
3. 根据 `selectedCallerId + attemptStage` 更新聚合统计与健康分

第一版健康分建议优先基于：

- 近窗接通率
- 近窗平均通话时长
- 失败码惩罚

投诉、标记、黑名单命中等高价值信号，后续可通过离线作业补齐。

## 幂等与一致性

第一版幂等点放在 `dialUnit + dispatchToken` 上，而不是放在 selector 上。

规则：

- 一个 `dialUnit` 的一次派发只能绑定一个 `selectedCallerId`
- 回写必须使用 `taskId + dialUnitId + dispatchToken` 命中唯一拨次
- 只有 `markDialingFromReady` 成功的记录，才允许计入号码曝光与使用
- 如果 claim 成功但落库缩水，未成功进入 `DIALING` 的号码不计入主叫号码使用

这样可以保证：

- 评分计算和实际派发不会错位
- MQ 发送重试不会污染号码使用统计
- 首拨/重拨样本归因可回溯

## 组件拆分建议

第一版在 `call-task` 内新增以下组件：

- `CallerIdSelector`
  - 负责对候选号码排序并返回选择结果
- `CallerIdCandidateService`
  - 负责根据任务模式和绑定关系产生候选集
- `CallerIdHealthService`
  - 负责维护号码聚合统计和健康分
- `TaskCallerIdPolicyService`
  - 负责把 `call_task` 配置转换为可执行策略对象

新增仓储建议：

- `CallCallerIdRepository`
- `CallTaskCallerIdBindingRepository`
- `CallCallerIdStatsRepository`

## 最小可交付范围

第一版 MVP 仅包含：

1. 任务级 Caller ID 策略配置
2. 租户共享池 + 任务白名单/黑名单
3. 首拨/重拨分层评分
4. 派发前实时选号
5. 回写后实时更新号码健康分
6. 基础冷却、曝光、失败码惩罚

明确不纳入 MVP：

- Bandit / RL
- 独立 Feature Store
- 多线路联合建模
- 投诉/标记实时回灌
- 精细地域/运营商匹配

## 测试策略

第一版测试应覆盖：

- 任务创建时策略字段的绑定与默认值
- 候选号码池在共享池、白名单、黑名单下的过滤行为
- `retryCount == 0` 与 `retryCount > 0` 的评分路径差异
- `PartitionSchedulerWorker` 在成功选号时会把 `callerId` 带入派发消息
- `DialResultWritebackService` 在回调后会更新号码统计
- 迁移脚本创建和修改表结构成功

## 后续演进

后续可在这套设计上继续扩展：

- 引入被叫地域与运营商画像，支持本地号策略
- 接入投诉、标记、黑名单命中的离线回灌
- 将评分器升级为 Contextual Bandit
- 把 Caller ID 选择从 `call-task` 内部组件平滑拆分成独立服务
