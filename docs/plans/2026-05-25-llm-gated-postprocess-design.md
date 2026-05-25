# LLM Gate Postprocess Design

**日期：** 2026-05-25

**状态：** 已确认

## 问题

当前 `call_record_persisted` 事件由 `RocketMqPersistedEventConsumer` 直接消费并同步到 Elasticsearch。后续还要新增两个下游：

- 客户推送 consumer
- LLM 分析 consumer

新的业务约束是：

- 存在全局 LLM 开关
- 当 LLM 开启时，Elasticsearch 同步和客户推送都必须等待 LLM 分析完成
- LLM 分析结果要先落 MySQL，再由下游读取
- 如果 LLM 连续失败到 RocketMQ 重试阈值，后续链路仍然要继续，只是按降级结果执行

这意味着 `call_record_persisted` 已经不适合继续作为 ES 和客户推送的直接消费事件；系统需要把“原始数据落库完成”和“分析结果可消费”拆成两个不同语义的事件。

## 目标

- 保持 `call_record_persisted` 作为原始通话数据落库成功事件
- 在 LLM 开启时，由 LLM 消费链路控制 ES 与客户推送的放行时机
- 把 LLM 结果持久化到 MySQL，作为后续查询的事实来源
- 在 LLM 成功或降级完成后，统一发布一个新的下游放行事件
- 保持 `MySQL + outbox` 作为可靠性边界，避免双写裂缝

## 非目标

- 本次不引入 LLM 历史版本表
- 本次不重构 `call-search` 查询接口
- 本次不在主写入事务中直接调用 LLM
- 本次不实现复杂的分析重跑控制台或人工补偿页面

## 推荐架构

推荐把链路拆成两段：

- `call_record_persisted`
  表示通话主数据已经完成入库，可以开始后处理
- `call_record_analysis_completed`
  表示如果启用了 LLM，则结果已经成功落库；如果 LLM 多次失败，则已经完成降级决策，ES 和客户推送都可以继续

消费关系调整为：

- `call_record_persisted` -> `CallAnalysisConsumer`
- `call_record_analysis_completed` -> `ElasticsearchSyncConsumer`
- `call_record_analysis_completed` -> `ThirdPartyPushConsumer`

这样可以让：

- LLM 分析负责产出“可继续处理”的统一时点
- ES 和客户推送只关心一个固定事件，不需要各自判断 LLM 是否完成
- LLM 失败重试不会直接阻塞 ES 或客户推送的内部重试语义

## 数据模型

新增非分片表 `call_analysis_result`，用于保存每通电话当前生效的一份分析结果。

建议字段：

- `id`
- `tenant_id`
- `call_id`
- `status`
- `tags`
- `risk_flag`
- `quality_score`
- `ai_version`
- `error_message`
- `completed_at`
- `created_at`
- `updated_at`

字段语义：

- `tenant_id + call_id` 为业务唯一键，保证一通电话只有一份当前结果
- `status` 取值：
  - `SUCCEEDED`
  - `DEGRADED`
- `tags` 使用 JSON 保存标签列表或结构化标签
- `risk_flag`、`quality_score`、`ai_version` 对齐现有 ES 文档能力
- `error_message` 只在降级时记录最后一次失败原因
- `completed_at` 表示这通电话已经达到“允许继续 ES 和客户推送”的终态

不建议在该表维护 RocketMQ 的重试次数。消费重试次数应继续以 RocketMQ 元数据为准，避免状态漂移。

## 事件模型

### 现有事件

- `call_record_persisted`

该事件保留现状，继续由 `call_record` 落库成功后通过 outbox 发布。

### 新增事件

- `call_record_analysis_completed`

该事件由分析链路发布，语义是：

- LLM 开启且分析成功，结果已入库
- LLM 开启但多次失败，降级结果已入库
- LLM 关闭，通话已被标记为可直接进入下游

建议为该事件新增独立 topic 配置：

- `call.postprocess.topics.analysis-completed`

`eventId` 使用稳定值：

- `call_record_analysis_completed:{tenantId}:{callId}`

事件 payload 建议只包含下游组装所需的最小定位信息，例如：

- `tenantId`
- `callId`
- `status`

下游读取完整数据时统一从 MySQL 查询 `call_record`、`call_round` 和 `call_analysis_result`。

## 事务边界

关键约束是：

`call_analysis_result` 落库和 `call_record_analysis_completed` outbox 写入必须在同一个本地事务里完成。

推荐流程：

1. `CALL_RECORD` 消费成功
2. `call_record` 与 `call_record_persisted` outbox 同事务提交
3. `CallAnalysisConsumer` 消费 `call_record_persisted`
4. 读取 `call_record` 和 `call_round`
5. 如果全局开启 LLM，调用 LLM 获取标签结果
6. 在同一个事务里：
   - upsert `call_analysis_result`
   - 写入 `call_record_analysis_completed` outbox
7. outbox publisher 发布 `call_record_analysis_completed`
8. ES consumer 与客户推送 consumer 读取该事件并继续处理

全局关闭 LLM 时，步骤 5 不调用模型；步骤 6 只写 `call_record_analysis_completed` outbox，不要求插入 `call_analysis_result`。

## 详细流程

### LLM 开启且分析成功

`call_record_persisted -> CallAnalysisConsumer -> LLM -> call_analysis_result(status=SUCCEEDED) + analysis_completed outbox -> RocketMQ -> ES/第三方`

### LLM 开启且分析失败但未到重试阈值

`call_record_persisted -> CallAnalysisConsumer -> LLM exception -> throw -> RocketMQ retry`

此阶段：

- 不写 `call_analysis_result`
- 不写 `call_record_analysis_completed`

### LLM 开启且分析失败并达到重试阈值

`call_record_persisted -> CallAnalysisConsumer -> LLM exception -> 根据重试阈值降级 -> call_analysis_result(status=DEGRADED) + analysis_completed outbox -> RocketMQ -> ES/第三方`

降级记录至少包含：

- `status=DEGRADED`
- `tags` 为空
- `error_message` 为最后一次失败原因
- `completed_at` 为当前完成时间

### LLM 关闭

`call_record_persisted -> CallAnalysisConsumer(pass-through) -> analysis_completed outbox -> RocketMQ -> ES/第三方`

下游统一消费 `call_record_analysis_completed`，不再直接订阅 `call_record_persisted`。

## 幂等策略

### 分析结果

- `call_analysis_result` 以 `tenant_id + call_id` 唯一
- `CallAnalysisConsumer` 每次消费时先查现有结果
- 如果已经是 `SUCCEEDED` 或 `DEGRADED`，说明该通话已完成终态处理，直接按幂等成功返回

### 放行事件

- `call_record_analysis_completed` 使用稳定 `eventId`
- outbox 表天然可承接事件唯一性约束
- 即使 RocketMQ 至少一次投递导致重复消费，下游也应按幂等处理

### 下游消费

- ES 同步重复执行时应覆盖同一文档 ID
- 客户推送若外部接口不天然幂等，需要额外引入请求幂等键

## 组件调整

### `call-common`

- 新增 `CallAnalysisResultEntity`
- 新增 `CallAnalysisResultMapper`
- 新增分析结果相关 DTO 或枚举

### `call-ingestion`

- 新增 Flyway migration：创建 `call_analysis_result`
- 新增 `CallAnalysisResultService`
- 新增 LLM 调用抽象，例如 `CallAnalysisClient`
- 新增 `RocketMqCallAnalysisConsumer`
- 新增 `RocketMqThirdPartyPushConsumer`
- 将 ES 消费从 `call_record_persisted` 切换到 `call_record_analysis_completed`
- 在 `OutboxEventFactory` 增加 `analysisCompleted(...)`
- 在 `OutboxPublisher` 增加新事件类型到 topic 的映射
- 在配置中增加：
  - 全局 LLM 开关
  - `analysis-completed` topic

## Elasticsearch 同步

ES consumer 改为消费 `call_record_analysis_completed` 后再查库组装文档：

- `call_record`
- `call_round`
- `call_analysis_result`（若存在）

这允许 ES 文档带上：

- `tags`
- `risk_flag`
- `quality_score`
- `ai_version`

当分析结果不存在且 LLM 关闭时，ES 仍然按无标签文档写入。

当分析结果是 `DEGRADED` 时，ES 写入空标签或缺省 AI 字段，但流程继续。

## 客户推送

客户推送 consumer 也只消费 `call_record_analysis_completed`。

它与 ES consumer 的区别只是目标系统不同，查询事实来源保持一致：

- `call_record`
- `call_round`
- `call_analysis_result`

这样客户收到的消息与 ES 索引看到的标签状态保持一致，不会出现一个已带标签、另一个仍是旧数据的分叉。

## 失败语义

### 分析阶段

- LLM 失败且未到 RocketMQ 重试阈值：抛异常，等待 broker 重投
- LLM 失败且已到阈值：落降级结果并放行下游

### ES 阶段

- ES 写入失败只影响 ES 消费组
- 继续由 RocketMQ 管理该消费组重试

### 客户推送阶段

- 客户推送失败只影响第三方消费组
- 不回滚 LLM 结果，也不影响 ES 消费组

## 测试范围

至少覆盖以下场景：

- LLM 开启并分析成功，写 `SUCCEEDED` 结果并发布 `call_record_analysis_completed`
- LLM 开启并短暂失败，consumer 抛错重试，不落结果、不发放行事件
- LLM 开启并达到最大重试次数，写 `DEGRADED` 结果并发布放行事件
- LLM 关闭时不调用模型，直接发布放行事件
- 重复消费同一 `call_record_persisted` 不会重复生成终态结果
- ES consumer 消费放行事件时能带上分析字段
- 客户推送 consumer 消费放行事件时能识别成功与降级结果

## 风险与权衡

- 新增一张分析结果表和一个新事件，链路比“直接在 record-persisted 上各自判断”多一跳
- 好处是编排逻辑集中，不会在 ES 和客户推送中重复实现状态判断
- 当前只保留一份生效结果，不保留历史版本；如果后续有重跑追踪需求，再扩展历史表

## 结论

推荐采用：

`call_record_persisted -> 分析消费与落库 -> call_record_analysis_completed -> ES/客户推送`

这样可以把“原始入库成功”和“分析已完成可放行”两种语义分离，同时保持 `MySQL + outbox` 的可靠性边界，并满足：

- LLM 开启时，下游必须等待分析完成
- LLM 超过失败阈值时，下游仍可按降级结果继续
