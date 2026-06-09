# Call Common Removal And Ingestion Simplification Design

**日期：** 2026-06-08

**状态：** 已确认

## 目标

本次设计同时解决两个问题：

- 删除职责过杂的 `call-common`
- 简化 `call-ingestion` 过度细分的内部包结构

重构后的目标结构是：

- 共享层只保留两个明确模块：`call-kernel`、`call-persistence`
- `call-ingestion` 回到“call 数据写入与写后处理模块”的定位
- `call-task`、`call-search`、`call-iam` 只依赖自己真正需要的共享能力

## 当前问题

### `call-common` 的问题

当前 `call-common` 同时承载：

- 基础工具和配置
- 分片路由上下文
- MySQL/MyBatis 配置
- Elasticsearch 配置
- `call-task` 相关实体、枚举、Mapper
- `call-search` 与 `call-ingestion` 使用的 ES 文档与初始化逻辑

这导致两个直接问题：

- 依赖 `call-common` 的模块会被动带入 MySQL、ES 等无关依赖
- 业务私有模型放在公共模块，模块归属混乱

### `call-ingestion` 的问题

`call-ingestion` 当前把一个写入模块拆成了很多小切片：

- `application/analysis`
- `application/record`
- `application/round`
- `application/postprocess`
- `application/deadletter`
- `domain/*`
- `inbound/consumer/*`
- `infrastructure/*/*`

虽然形式上分层完整，但对当前模块并不务实。`call-ingestion` 的真实职责很直接：

- 消费消息
- 写入 call 相关数据
- 处理写后索引、第三方推送、分析结果和死信补偿

当前目录结构把一个单一模块拆得像多个微服务，增加了阅读和迁移成本。

## 设计结论

### 共享模块

删除 `call-common`，改为两个更小的共享模块：

#### `call-kernel`

只放纯基础能力，不携带存储或搜索技术栈依赖：

- `HashUtil`
- `CallIdProperties`

#### `call-persistence`

统一承载分片路由与 MySQL/MyBatis 持久化支撑：

- `ShardProperties`
- `ShardKey`
- `ShardContext`
- `DbRouteContextHolder`
- `ShardContextHolder`
- `ShardingRouter`
- `ShardedSnowflakeIdGenerator`
- `CallDatasourceProperties`
- `RoutingDataSourceConfig`
- `MybatisPlusConfig`

这个模块允许依赖 JDBC、MyBatis、MySQL 驱动，不再把这些依赖传播到不需要的模块。

### 业务模块归属

#### `call-ingestion`

保留职责：

- record ingest
- round ingest
- analysis result processing
- Elasticsearch indexing
- third-party push
- outbox publish
- dead-letter persistence and compensation

迁入内容：

- ingestion 私有消息模型
- ingestion 私有持久化实体和 Mapper
- ingestion 私有 ES 写入模型和初始化逻辑

#### `call-task`

迁入内容：

- `CallTaskEntity`
- `CallTaskImportBatchEntity`
- `CallDialUnitEntity`
- `CallCallerIdEntity`
- `CallCallerIdStatsEntity`
- `CallTaskCallerIdBindingEntity`
- 对应 Mapper
- 对应枚举

#### `call-search`

迁入内容：

- 模块本地 Elasticsearch 配置

#### `call-iam`

不再通过公共模块被动获取 ES 相关能力，只按需依赖：

- `call-kernel`
- `call-persistence`

## `call-ingestion` 新结构

`call-ingestion` 不再保留 `application / domain / infrastructure` 这类过细的分层切片，而改为围绕模块真实工作流组织：

```text
com.callcenter.ingestion
├── CallIngestionApplication
├── config
├── consumer
├── service
├── repository
├── postprocess
├── model
├── mq
└── support
```

各目录职责如下：

- `config`
  - Spring 配置
  - properties
  - listener/customizer
- `consumer`
  - RocketMQ consumer
  - dead-letter consumer
- `service`
  - 写入流程编排
  - 分析结果处理
  - 死信补偿编排
- `repository`
  - MyBatis repository、entity、mapper
- `postprocess`
  - Elasticsearch 写入
  - third-party push
  - outbox 发布
- `model`
  - 模块内部消息体、请求响应体、事件体、状态枚举
- `mq`
  - MQ 公共适配器和 publisher
- `support`
  - 少量辅助代码，例如 metrics

## 迁移原则

### 共享代码进入新公共模块的原则

只有满足下面条件之一，才进入共享模块：

- 至少两个业务模块稳定复用
- 语义上属于基础设施或基础能力，而不是某个业务模块私有模型

### 业务代码迁回模块的原则

只要某类代码满足下面任一条件，就不应继续留在共享模块：

- 只被一个业务模块使用
- 虽然可复用，但语义明确从属于一个业务模块
- 与具体存储或处理流程强绑定

## 非目标

- 本次不改变现有业务语义
- 本次不重写 ingest、outbox、死信或索引链路
- 本次不引入新的架构风格或额外中间层
- 本次不追求“严格 DDD”或“严格六边形”形式完整性

## 风险点

- `call-task` 对 `call-common` 中实体与 Mapper 的依赖面较大，迁移时包引用改动会比较广
- `call-ingestion` 测试目录当前也较散，主代码压平后测试目录需要同步整理
- `call-search` 若继续依赖共享 ES 文档模型，会重新形成新的隐式公共模块，需要一并迁回

## 验证标准

重构完成后应满足：

- 仓库中不再存在 `call-common` 模块
- 不需要 MySQL/ES 的模块不会再因为公共依赖被动带入对应技术栈
- `call-ingestion` 顶层目录明显收敛，能够顺着“消费 -> 写入 -> 写后处理 -> 补偿”理解代码
- 现有核心测试和编译验证通过
