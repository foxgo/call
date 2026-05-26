# Call Task Design

**日期：** 2026-05-26

**状态：** 已确认

## 问题

当前仓库已有：

- `call-common`：公共配置、分库分表路由、MyBatis 实体与 Mapper
- `call-ingestion`：写入链路、RocketMQ、定时任务
- `call-search`：查询接口
- `call-ops`：运维入口

系统缺少一个面向外呼任务中心的独立服务，用于承接：

- 任务创建、启动、暂停、恢复、查询
- 名单导入与导入批次跟踪
- 号码池调度、并发控制、失败重试、超时回收
- 外呼投递和结果状态回写

新的服务需要满足以下约束：

- MySQL 是最终真相，Redis 只保存调度状态
- 不丢号，允许至少一次投递
- 不重复拨打，除非进入重试语义
- 支持多实例调度，实例宕机后可恢复
- Redis 不能保存全量号码池，未启动任务不应占用 Redis

## 目标

- 新增独立模块 `call-task`
- 将任务管理、名单导入、调度编排、回写闭环集中到 `call-task`
- 采用 `MySQL + Redis + RocketMQ` 构建可恢复的调度状态机
- 复用现有 `call-common` 分库分表和 MyBatis 体系
- 首版提供可运行的任务 API、调度器、回写接口和基础监控

## 非目标

- 本次不实现线路级 QPS 控制
- 本次不实现 Predictive Dialer 动态放大
- 本次不实现文件上传解析，名单导入先支持 JSON 批量入参
- 本次不实现独立批次审计表，波次先按系统默认策略运行
- 本次不实现跨机房多活

## 推荐架构

推荐新增独立 Spring Boot 模块 `call-task`，负责完整的任务中心职责，但在模块内部按边界拆层：

- `controller`
  - 任务管理 API
  - 名单导入 API
  - 外呼结果回写 API
  - 任务与导入批次查询 API
- `service`
  - 任务生命周期管理
  - 导入批次编排
  - 回写状态机更新
- `dispatch`
  - 主调度循环
  - 预热装载器
  - Redis 三段队列协调器
  - 并发额度控制器
  - 超时回收与失败重试
- `mq`
  - RocketMQ 拨号消息发布
  - 回写适配层

`call-common` 继续作为公共基础模块，新增任务域实体、Mapper、枚举和必要的分片辅助能力。

## 模块边界

### `call-common`

新增：

- `CallTaskEntity`
- `CallTaskImportBatchEntity`
- `CallDialUnitEntity`
- 任务相关状态枚举
- `CallTaskMapper`
- `CallTaskImportBatchMapper`
- `CallDialUnitMapper`

保留职责：

- 分库分表路由
- 数据源装配
- 统一 MyBatis 配置
- 公共 ID 与分片配置

### `call-task`

新增独立应用入口：

- `CallTaskApplication`

该服务扫描 `com.callcenter` 并导入 `CallCommonAutoConfiguration`，风格与现有模块保持一致。

## 数据模型

### `call_task`

用于保存任务主数据和生命周期。

建议字段：

- `id`
- `tenant_id`
- `name`
- `status`
- `total_count`
- `queued_count`
- `dialing_count`
- `success_count`
- `failed_count`
- `max_concurrency`
- `start_time`
- `end_time`
- `next_dispatch_time`
- `version`
- `created_at`
- `updated_at`

状态：

- `INIT`
- `RUNNING`
- `PAUSED`
- `FINISHED`

说明：

- `next_dispatch_time` 由系统默认批次策略推进
- `version` 用于乐观锁更新，避免并发下统计覆盖

### `call_task_import_batch`

用于记录一次名单导入行为及统计结果。

建议字段：

- `id`
- `tenant_id`
- `task_id`
- `source_type`
- `status`
- `total_count`
- `success_count`
- `skipped_count`
- `failed_count`
- `error_message`
- `created_at`
- `updated_at`

状态：

- `PENDING`
- `PROCESSING`
- `COMPLETED`
- `FAILED`

这张表只承担导入审计与追踪，不承载调度态。

### `call_dial_unit`

用于保存号码池和拨号状态，是调度真相表。

建议字段：

- `id`
- `tenant_id`
- `task_id`
- `import_batch_id`
- `phone`
- `status`
- `retry_count`
- `max_retry_count`
- `score`
- `last_call_time`
- `next_call_time`
- `dispatch_token`
- `inflight_expire_at`
- `biz_idempotency_key`
- `failure_code`
- `failure_reason`
- `created_at`
- `updated_at`

状态：

- `PENDING`
- `QUEUED`
- `DIALING`
- `SUCCESS`
- `FAILED`

分片策略：

- 按 `tenant_id` 分库
- 按 `task_id` 分表

### 唯一性与索引

建议保证：

- `call_task_import_batch(id)` 主键
- `call_task(id)` 主键
- `call_dial_unit(id)` 主键
- `task_id + phone + biz_idempotency_key` 业务唯一，防止同批次重复导入
- `task_id + status + next_call_time` 便于按需预热
- `task_id + dispatch_token` 便于回写幂等更新

## Redis 调度状态机

Redis 不是全量数据存储，只保存近期活跃窗口。

三段队列：

- `queue:ready:{taskId}:{shard}`
- `queue:processing:{taskId}:{shard}`
- `queue:retry:{taskId}:{shard}`

结构：

- `ready`：ZSET，score 为 `score + next_call_time`
- `processing`：ZSET，score 为处理超时时间戳
- `retry`：ZSET，score 为下次允许重试时间

### 关键原则

- 名单导入只写 MySQL，不直接灌入 Redis
- 只有 `RUNNING` 任务才允许预热到 Redis
- Redis 保存的是热窗口，不保存未启动任务和全量号码

## 状态机

### 任务状态

流转规则：

- `INIT -> RUNNING`
- `RUNNING -> PAUSED`
- `PAUSED -> RUNNING`
- `RUNNING -> FINISHED`

限制：

- `FINISHED` 不允许回退
- 没有号码池的任务不能启动

### 拨号单元状态

流转规则：

- `PENDING -> QUEUED`
- `QUEUED -> DIALING`
- `DIALING -> SUCCESS`
- `DIALING -> FAILED`
- `FAILED -> PENDING` 仅在允许重试时成立

说明：

- `PENDING`：仅存在于 MySQL，尚未进入调度窗口
- `QUEUED`：已被预热装载进 Redis
- `DIALING`：已成功占有并发额度并完成投递
- `SUCCESS`/`FAILED`：终态，除非失败进入重试回退

## 调度流程

### 1. 名单导入

1. 创建 `call_task_import_batch`
2. 批量写入 `call_dial_unit`
3. 初始状态全部标记为 `PENDING`
4. 更新导入批次统计

导入时不向 Redis 写入任何号码。

### 2. 预热装载

只有 `RUNNING` 任务会执行预热：

1. 读取当前任务在 Redis 中的活跃窗口数量：
   - `ready`
   - `processing`
   - `retry`
2. 如果总量低于 `preloadThreshold`
3. 从 MySQL 条件更新一批 `PENDING -> QUEUED`
4. 将这批号码写入 Redis `ready`

这一步让 Redis 保持小而热的工作集。

### 3. 主调度循环

每秒执行一次：

1. 扫描 `RUNNING` 任务
2. 跳过 `next_dispatch_time` 未到的任务
3. 检查全局、租户、任务三级并发额度
4. 根据系统默认批次大小，计算本轮最多可投递数量
5. 从 `ready` 原子搬运到 `processing`
6. 条件更新 MySQL：`QUEUED -> DIALING`
7. 发送 RocketMQ 拨号消息
8. 更新 `dispatch_token`、`inflight_expire_at`
9. 推进任务 `next_dispatch_time`

### 4. 结果回写

回写入参至少包含：

- `tenantId`
- `taskId`
- `dialUnitId`
- `dispatchToken`
- `resultStatus`
- `failureCode`
- `failureReason`

处理逻辑：

1. 按 `dialUnitId + dispatchToken` 做幂等匹配
2. 条件更新 `DIALING -> SUCCESS/FAILED`
3. 从 `processing` 移除
4. 释放并发额度
5. 若失败且可重试，写入 `retry`
6. 更新任务统计

### 5. 重试回流

后台定时扫描 `retry` 到期项：

1. 从 `retry` 移除
2. 回写到 `ready`
3. MySQL 保持 `QUEUED`

### 6. 超时回收

后台定时扫描 `processing` 超时项：

1. 找出超时未确认的拨号单元
2. 从 `processing` 移除
3. 释放并发额度
4. 若未超过最大重试次数，则回到 `ready`
5. 否则标记 `FAILED`

这一步用于兜底：

- 调度实例宕机
- 投递后未收到回调
- 网络异常导致状态悬挂

## Redis 原子操作

以下操作必须 Lua 原子化：

- `ready -> processing`
- `retry -> ready`
- `processing -> ready`

原因：

- 避免中间状态丢号
- 避免多实例并发下重复搬运

## 并发控制

并发额度分三级：

- 全局：`call:concurrency:global`
- 租户：`call:concurrency:tenant:{tenantId}`
- 任务：`call:concurrency:task:{taskId}`

规则：

- 成功占有并准备投递时申请额度
- 回写完成、判定失败或超时回收时释放额度
- Redis 并发键需要设置 TTL，防止实例崩溃后额度泄漏
- 定时恢复任务可按 MySQL `DIALING` 记录做额度校准

## 批次节奏

任务级不配置 `qps_limit`、`batch_size`、`batch_interval_seconds`。

首版统一走系统默认批次策略，配置在 `call-task` 模块：

- `pollInterval`
- `dispatchBatchSize`
- `preloadBatchSize`
- `preloadThreshold`
- `processingTimeout`
- `retryBackoff`

这样可以先稳定实现任务中心，再把复杂节奏控制下沉到未来的线路层。

## 幂等策略

### 导入幂等

- `task_id + phone + biz_idempotency_key` 唯一
- 重复号码计入 `skippedCount`

### 调度幂等

- 只有 `QUEUED` 状态允许进入 `DIALING`
- 条件更新失败说明被其他实例抢占，当前实例直接跳过

### 回写幂等

- 按 `dialUnitId + dispatchToken` 更新
- 旧 token 的回写直接忽略
- 重复回调按成功处理

## API 设计

### 任务管理

- `POST /api/v1/{tenantId}/tasks`
- `POST /api/v1/{tenantId}/tasks/{taskId}/start`
- `POST /api/v1/{tenantId}/tasks/{taskId}/pause`
- `POST /api/v1/{tenantId}/tasks/{taskId}/resume`
- `GET /api/v1/{tenantId}/tasks`
- `GET /api/v1/{tenantId}/tasks/{taskId}`

### 名单导入

- `POST /api/v1/{tenantId}/tasks/{taskId}/imports`
- `GET /api/v1/{tenantId}/tasks/{taskId}/imports/{importBatchId}`

首版仅支持 JSON 批量导入，不做文件上传解析。

### 结果回写

- `POST /api/v1/{tenantId}/tasks/callbacks/dial-result`

回写逻辑落在 `call-task` 内部实现，形成完整闭环。

## 配置设计

建议增加配置前缀 `call.task`：

- `call.task.dispatch.poll-interval`
- `call.task.dispatch.dispatch-batch-size`
- `call.task.dispatch.preload-batch-size`
- `call.task.dispatch.preload-threshold`
- `call.task.dispatch.processing-timeout`
- `call.task.dispatch.retry-backoff`
- `call.task.dispatch.max-retries`
- `call.task.dispatch.shard-count`
- `call.task.concurrency.global-max`
- `call.task.concurrency.tenant-default-max`
- `call.task.rocketmq.topics.dispatch`

## 可观测性

至少暴露以下指标：

- 运行中任务数量
- `ready/processing/retry` 队列长度
- 预热装载次数
- 成功投递数
- 回写成功数
- 回写失败数
- 超时回收次数
- 重试回流次数

告警重点：

- `processing` 长时间堆积
- `retry` 短时间激增
- 回写失败率异常

## 测试策略

单元测试覆盖：

- 任务状态流转
- 拨号单元状态流转
- 导入去重
- 并发额度申请与释放
- 回写幂等
- 重试回流与超时回收

集成测试覆盖：

- 创建任务并导入名单
- 启动任务后从 MySQL 预热到 Redis
- 调度后进入 `DIALING`
- 回写成功后进入 `SUCCESS`
- 回写失败后进入 `retry`

Redis Lua 脚本建议做独立组件测试。

## 实施建议

实施顺序建议为：

1. 先补 `call-common` 任务域模型与 `call-task` 模块骨架
2. 再补任务管理 API 与名单导入
3. 再补预热、调度、并发控制
4. 最后补回写、重试、回收与指标

这样可以在不推翻架构的前提下分层交付 `call-task` 首版。
