# Production Logging Design

**日期：** 2026-06-09

**状态：** 已确认

## 目标

为 `call-iam`、`call-search`、`call-task`、`call-ingestion` 建立一套生产可用的统一日志体系，满足以下要求：

- 生产环境默认输出结构化 JSON 日志
- 所有服务字段命名统一，便于 ELK/Loki/Datadog 等平台采集
- 强制敏感信息脱敏，业务代码不能绕过
- HTTP、RocketMQ、线程池异步链路能够传递 `traceId` / `requestId`
- 关键异常、重试、死信、调度拒绝等高价值事件有稳定日志

## 当前问题

### 配置不统一

当前只有 `call-ingestion` 和 `call-task` 在 `application.yml` 中定义了简单的 `logging.pattern.console`，而且只覆盖控制台格式：

- `call-ingestion/src/main/resources/application.yml`
- `call-task/src/main/resources/application.yml`

`call-search` 和 `call-iam` 没有统一的日志格式配置，因此同一套系统的不同服务无法稳定对齐字段和输出形态。

### 上下文关联能力不足

现有日志 pattern 里虽然预留了 `%X{traceId}` 和 `%X{spanId}`，但仓库里没有完整的统一 MDC 建立和透传机制：

- HTTP 请求入口没有统一生成或透传 `requestId`
- 线程池执行器没有统一透传 MDC
- RocketMQ 消费日志没有统一把消息上下文打入 MDC
- `call-iam` 的鉴权过滤器没有把 `tenantId` / `userId` 注入日志上下文

### 敏感信息治理缺失

当前没有统一的脱敏组件。只要开发者直接打印：

- `Authorization`
- `password`
- `token`
- 手机号
- 身份证号
- Cookie

就会明文出现在生产日志中。

### 业务日志不成体系

关键链路里有零散 `warn/info` 日志，但缺少统一事件命名、关键字段规范和异常模板。例如：

- `call-task` 异步调度日志有 `taskId/dialUnitId/token`，但没有统一 `event`
- `call-ingestion` 的 `CallRecordIngestionService` 出错时直接吞异常返回 `false`，缺少失败原因和业务主键日志
- `call-iam` 的 `GlobalExceptionHandler` 只返回响应，不记录结构化异常日志

## 设计结论

### 模块边界

新增独立共享模块 `call-observability`，不要复用 `call-persistence`。

原因：

- 日志、追踪、脱敏不属于持久化语义
- `call-search` 不依赖 `call-persistence`，但必须复用日志基建
- 后续 metrics / tracing / HTTP client 观测能力也应继续归入该模块

根 `pom.xml` 增加模块：

- `call-observability`

四个后端服务全部依赖：

- `call-observability`

### 总体方案

采用“两层脱敏 + 一层上下文统一 + 一套结构化输出”的方案。

#### 第一层：应用层强制脱敏

所有业务字段、请求头、请求体摘要、消息体摘要，在进入结构化日志前先经过 `LogSanitizer`。

该层负责：

- 按字段名脱敏
- 按值模式脱敏
- 递归处理 `Map` / `Collection` / JSON 树

这是主防线。

#### 第二层：日志输出层兜底脱敏

在 Logback JSON encoder 层增加兜底脱敏规则，防止有人直接写：

- `log.info("token={}", token)`
- `log.info("headers={}", headers)`
- `log.error("payload={}", rawBody)`

该层不替代应用层，只负责最后一道防线。

#### 第三层：统一上下文

通过共享 filter / decorator / MQ support，把这些字段统一写入 MDC：

- `traceId`
- `spanId`
- `requestId`
- `tenantId`
- `userId`
- `clientIp`
- `httpMethod`
- `httpPath`
- `topic`
- `messageId`
- `messageKeys`
- `reconsumeTimes`

### 共享模块内容

`call-observability` 提供以下能力：

#### 1. `LoggingAutoConfiguration`

自动装配：

- HTTP 请求日志 filter
- MDC task decorator
- 日志配置属性
- 脱敏器
- JSON 序列化辅助组件

#### 2. `LoggingProperties`

统一配置项，例如：

- `call.logging.json-enabled`
- `call.logging.env`
- `call.logging.request.include-headers`
- `call.logging.request.header-whitelist`
- `call.logging.request.max-body-length`
- `call.logging.masking.enabled`
- `call.logging.masking.field-names`

#### 3. `RequestLoggingFilter`

职责：

- 接收上游 `X-Request-Id`，没有则生成
- 为每个请求写入 MDC
- 在请求完成后输出一条结构化访问日志
- 默认不记录完整 body，只记录安全摘要

#### 4. `MdcTaskDecorator`

用于 `ThreadPoolTaskExecutor` 的 MDC 透传，保证：

- `call-task` 调度线程
- `call-task` 发送线程
- 后续新增线程池

都能延续同一请求或消息的上下文。

#### 5. `MqLoggingContext`

用于 RocketMQ 消费前后建立和清理 MDC。

写入字段：

- `topic`
- `messageId`
- `messageKeys`
- `queueId`
- `queueOffset`
- `reconsumeTimes`
- 可解析时追加 `tenantId`

#### 6. `LogSanitizer`

统一敏感字段脱敏：

- `phone`
- `mobile`
- `idCard`
- `password`
- `pwd`
- `token`
- `accessToken`
- `refreshToken`
- `authorization`
- `cookie`
- `secret`
- `appSecret`

#### 7. `StructuredLogFields`

通过常量或 builder 统一字段名，避免业务代码自己发明 key，例如：

- `event`
- `tenantId`
- `userId`
- `taskId`
- `callId`
- `dialUnitId`
- `costMs`
- `status`
- `errorCode`

### 日志输出格式

生产环境默认输出 JSON，最少包含这些公共字段：

- `timestamp`
- `level`
- `service`
- `env`
- `traceId`
- `spanId`
- `requestId`
- `thread`
- `logger`
- `event`
- `message`
- `exception`

按场景输出的业务字段：

- `tenantId`
- `userId`
- `taskId`
- `callId`
- `dialUnitId`
- `topic`
- `messageId`
- `messageKeys`
- `status`
- `costMs`

### 脱敏规则

#### 强制全掩码

以下字段不保留明文：

- `password`
- `pwd`
- `token`
- `accessToken`
- `refreshToken`
- `authorization`
- `cookie`
- `secret`
- `appSecret`

输出统一占位，例如 `******`。

#### 部分保留

- 手机号：保留前 3 后 4
- 身份证号：保留前 3 后 2
- 姓名类：如后续进入日志，只保留首字符

#### 递归对象脱敏

对 `Map`、`List`、Jackson `JsonNode` 做递归字段名脱敏。

#### 文本兜底脱敏

对原始字符串日志做模式替换，覆盖典型场景：

- `Bearer xxx`
- `password=...`
- `token=...`
- 11 位手机号
- 身份证号模式

### 模块级改造点

#### `call-iam`

需要完成：

- `JwtAuthenticationFilter` 在鉴权成功后把 `tenantId` / `userId` 写入 MDC
- `GlobalExceptionHandler` 先记录结构化异常日志，再返回统一响应
- 登录失败、权限拒绝、租户/用户/角色关键操作补审计型日志

#### `call-search`

需要完成：

- HTTP 请求入口日志
- 查询耗时日志
- Elasticsearch 查询失败结构化日志

#### `call-task`

需要完成：

- `DispatchExecutorConfiguration` 接入 `MdcTaskDecorator`
- `AsyncDialDispatchService` 统一事件名与关键字段
- `CallTaskDispatcher`、补偿、门禁拒绝、线程池拒绝统一结构化日志

#### `call-ingestion`

需要完成：

- 所有 RocketMQ consumer 建立统一 MQ MDC
- `CallRecordIngestionService`、`CallRoundIngestionService` 等失败时记录结构化异常日志
- 死信、第三方推送、索引写入、分析结果回写统一事件命名

## 落地策略

### Phase 1：日志基建

先完成：

- `call-observability`
- 统一 JSON logback
- HTTP 请求日志
- 全局异常日志
- 线程池 MDC 透传
- RocketMQ MDC
- 强制脱敏

完成这一阶段后，系统已经具备生产可采集、可关联、不泄密的基础能力。

### Phase 2：高价值业务事件

只补关键链路：

- `call-iam` 鉴权与关键变更
- `call-task` 调度、拒绝、重试、补偿
- `call-ingestion` 消费、落库、死信、外部依赖失败
- `call-search` 查询入口、耗时、ES 失败

## 测试策略

### 单元测试

- `LogSanitizerTest`
- `MdcTaskDecoratorTest`
- `MqLoggingContextTest`

### 集成测试

- HTTP 请求进入后 `requestId` 存在且写入响应头
- 统一异常日志结构输出
- MQ 消费上下文字段存在

### 回归验证

- 启动四个服务，检查 JSON 字段一致
- 验证日志中不再出现明文 `password/token/Authorization`

## 风险点

- `call-ingestion` 当前只有部分 tracing 依赖，其它模块需要统一补齐依赖边界
- 若开发者继续手写原始对象日志，仍需依赖兜底脱敏防漏
- 对请求体/消息体默认全量日志要严格克制，否则会放大存储成本和泄密风险

## 非目标

- 本次不接入完整 OpenTelemetry Collector 日志链路
- 本次不做所有业务方法的全量埋点
- 本次不引入独立审计日志存储系统

## 验证标准

完成后应满足：

- 四个服务生产环境默认输出结构化 JSON 日志
- 日志字段命名统一
- HTTP、RocketMQ、线程池链路均可关联 `traceId/requestId`
- 明文敏感字段不会进入生产日志
- 关键失败路径具备结构化日志，能支撑生产排障
