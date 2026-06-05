# Call Ingestion Slice Architecture Design

**日期：** 2026-06-05

**状态：** 已确认

## 目标

重构 `call-ingestion` 的内部结构，采用更务实的 `Vertical Slice + DDD Lite + Hexagonal` 组织方式：

- 顶层统一改为 `inbound / application / domain / infrastructure / support`
- 在四层内部按 `record / round / analysis / postprocess / outbox / deadletter` 分业务切片
- 把只被 `call-ingestion` 使用的 `call-common` 类型迁回本模块
- 在不改变业务语义的前提下，收紧职责边界和依赖方向

## 当前问题

当前 `call-ingestion` 主要按 `consumer / service / mq / outbox / model / config` 横向分层：

- `service` 同时承载用例编排、领域规则、MyBatis 落库和第三方调用
- `consumer` 直接依赖编排和基础设施细节，入口层过重
- `outbox` 既包含发布用例，也包含存储细节，边界不清
- 一批只被 `call-ingestion` 使用的 `dto / entity / mapper / enum` 仍然放在 `call-common`

这让代码结构更像“技术分包”，而不是“围绕用例组织”。随着 `record / round / analysis / postprocess / deadletter` 链路继续增长，横向 `service` 包会越来越难维护。

## 设计结论

采用“顶层四层 + 层内切片”的结构，而不是“每个切片再套一层完整工程模板”。

目标目录：

```text
com.callcenter.ingestion
├── inbound
├── application
├── domain
├── infrastructure
└── support
```

层内再按业务切片分目录：

- `record`
- `round`
- `analysis`
- `postprocess`
- `outbox`
- `deadletter`

示例：

- `inbound.record.RocketMqCallRecordConsumer`
- `application.record.RecordIngestionService`
- `domain.record.CallRecordMessage`
- `infrastructure.record.persistence.MybatisCallRecordRepository`

## 层语义

### inbound

只放入站适配器：

- RocketMQ consumer
- DLQ consumer
- 定时触发入口

它们只负责：

- 接收输入
- 反序列化
- 调用 application 用例
- 根据结果决定 ack、retry 或抛错

它们不负责：

- 分片路由规则
- 业务校验
- 持久化细节
- outbox 组装
- 第三方调用

### application

放用例编排和事务边界：

- 主记录写入流程
- 回合写入流程
- 分析编排流程
- 索引和第三方推送流程
- outbox 发布流程
- dead-letter 入库流程

这里负责调用顺序、事务边界、错误传播和降级策略，但不直接持有技术细节实现。

### domain

放 ingestion 自己的领域模型和规则：

- ingest message
- postprocess request model
- 状态枚举
- 一致性校验规则
- 分析降级规则
- outbox 状态和重试规则

`domain` 不放：

- MyBatis entity
- MyBatis mapper
- Elasticsearch client
- RocketMQ publisher
- Spring configuration

这次重构是 `DDD Lite`，不会强行构建复杂聚合，也不会为了形式把所有 POJO 都塞进 domain。

### infrastructure

放所有出站适配器和技术实现：

- MyBatis mapper 和持久化对象
- 仓储适配器
- RocketMQ publisher
- Elasticsearch 写入
- 第三方 HTTP client
- Spring 配置

凡是直接依赖外部技术框架的实现，都应留在这里。

### support

只保留少量横切技术辅助：

- `routing`
- `metrics`
- `json`

`support` 不能演变成新的“杂项 service 包”。如果某段代码明显归属于某个业务切片，应优先放回对应切片。

## 业务切片边界

### record

负责主记录 ingest：

- 消费 `record-ingest`
- 路由记录分片
- 写入 `call_record`
- 校验回合数一致性
- 生成“record persisted” outbox 事件

### round

负责回合 ingest：

- 消费 `round-ingest`
- 路由回合分片
- 写入 `call_round`

### analysis

负责 persisted record 的分析编排：

- 读取 `call_record_persisted`
- 幂等检查
- 加载回合
- 调用分析客户端
- 写入分析结果
- 生成“analysis completed” outbox 事件

### postprocess

负责分析完成后的下游处理：

- Elasticsearch 索引
- 第三方推送

`postprocess` 先保留为一个业务切片，内部再按 `index` 和 `push` 区分实现，不单独拉高一层目录复杂度。

### outbox

负责 outbox 发布用例：

- 认领待发布批次
- 恢复卡住的 `PROCESSING`
- 发布 MQ 消息
- 标记失败与退避重试

它是独立用例，不挂在 `record` 或 `analysis` 下面。

### deadletter

负责 DLQ 入库和后续补偿边界：

- 解析死信原始消息
- 提取幂等键和来源元数据
- 落表保存人工/自动补偿所需上下文

## call-common 收口原则

迁回 `call-ingestion` 的类型必须满足两个条件之一：

- 只被 `call-ingestion` 使用
- 虽然理论上可复用，但语义本质上属于 ingestion 写侧模型

这次建议迁回的类型：

- `CallRecordMessage`
- `CallRoundMessage`
- `DomainEventMessage`
- `CallRecordEntity`
- `CallRoundEntity`
- `CallAnalysisResultEntity`
- `CallEventOutboxEntity`
- `CallDeadLetterTaskEntity`
- `CallRecordMapper`
- `CallRoundMapper`
- `CallAnalysisResultMapper`
- `CallEventOutboxMapper`
- `CallDeadLetterTaskMapper`
- `AnalysisResultStatus`

继续保留在 `call-common` 的类型：

- `CallCommonAutoConfiguration`
- `ShardKey`
- `ShardContext`
- `ShardingRouter`
- `ShardContextHolder`
- 数据源和分片基础设施配置

判断原则很简单：

- 跨模块基础设施留在 `call-common`
- ingestion 私有写模型迁回 `call-ingestion`

## 命名与职责收敛

现有 `*MysqlService` 这类类名职责不清。重构后需要向“仓储适配器”语义收敛：

- `CallRecordMysqlService` -> `MybatisCallRecordRepository`
- `CallRoundMysqlService` -> `MybatisCallRoundRepository`

类似地：

- `consumer` 改收口为 `inbound`
- 纯编排类保留在 `application`
- 技术调用类保留在 `infrastructure`

目标不是机械重命名，而是让类名和职责一致。

## 依赖规则

依赖方向固定为：

`inbound -> application -> domain`

`application -> infrastructure` 只通过注入使用实现，不把技术细节反向带回 domain。

`domain` 不能依赖：

- `inbound`
- `infrastructure`
- Spring 框架注解以外的技术实现细节

`support` 不得依赖具体业务切片，避免反向耦合。

## 迁移策略

采用渐进迁移，不做一次性大挪包。

第一阶段：

- 迁回 `call-common` 中 ingestion 私有类型
- 让 `call-ingestion` 先摆脱对这些共享类的依赖

第二阶段：

- 新建 `inbound / application / domain / infrastructure / support`
- 按切片迁移现有类
- 修正 Spring 扫描和测试导入

第三阶段：

- 把 `*MysqlService`、`service` 大包中的技术实现拆到 `infrastructure`
- 把剩余编排逻辑压回 `application`
- 用更清晰的命名替换临时过渡类

第四阶段：

- 删除旧的 `consumer / service / mq / outbox / model / processor` 旧包
- 清理过渡导入和重复类型

## 测试策略

重构以“行为不变”为前提，测试要跟着切片迁移：

- consumer 相关测试迁到 `inbound.*`
- 用例编排测试迁到 `application.*`
- outbox 测试迁到 `application.outbox` 或 `infrastructure.outbox`
- repository adapter 测试迁到 `infrastructure.*`

至少需要跑：

- `mvn -pl call-ingestion test`

在中间阶段可跑切片级测试，降低迁移回归面。

## 取舍

这次方案刻意避免两种极端：

- 不保留现有的横向 `service` 杂糅结构
- 也不把模块拆成过度形式化的“每个切片一套完整小工程”

最终目标是：

- 目录更稳定
- 用例边界更清晰
- `call-common` 只保留真正共享的东西
- 后续新增 ingest / postprocess 能按切片扩展，而不是继续把代码堆进同一个 `service` 包
