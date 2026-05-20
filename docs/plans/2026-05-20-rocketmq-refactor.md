# RocketMQ 重构实施计划

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 用 RocketMQ 替换 Kafka，保证同一个 `callId` 有序，并把后处理简化为由 `record-persisted` 直接触发 LLM 和第三方消费组，在消费时基于 `call_round` 持久化数量决定立即处理、继续重试，或在超过阈值后按不完整数据继续执行。

**Architecture:** 主写链路统一收敛到一个按 `callId` 分片的 RocketMQ 顺序 topic，继续以 MySQL 加 outbox 作为可靠性边界；旧的双事件 orchestrator 删除，`MessageBatchProcessor` 也一起删除。各消费组直接通过 RocketMQ listener 消费，并分别使用 `consumeThreadMax` 控制并发；当 round 数量未齐时，依赖 RocketMQ 重新投递等待补齐；超过业务重试阈值后，即使数据仍不完整也继续执行下游逻辑。

**Tech Stack:** Java 21, Spring Boot 3.2, RocketMQ Spring Boot starter, MyBatis-Plus, MySQL 8, Micrometer, JUnit 5, Mockito, Testcontainers

---

### Task 1: 固化新的 record 模型和 readiness 规则

**Files:**
- Modify: `call-common/src/main/java/com/callcenter/common/dto/CallRecordMessage.java`
- Modify: `call-common/src/main/java/com/callcenter/common/entity/CallRecordEntity.java`
- Modify: `call-common/src/test/java/com/callcenter/common/...` 按需要补充
- Create: `call-ingestion/src/test/java/com/callcenter/ingestion/postprocess/PostprocessReadinessRuleTest.java`

**Step 1: 先写失败测试**

覆盖点：
- `CallRecordMessage` 新增 `roundTotal`
- readiness 只有在 `expectedRoundCount == receivedRoundCount` 时才成立
- round 数量不足时状态为 pending，而不是 failure

**Step 2: 运行测试确认先失败**

Run: `mvn -pl call-ingestion -Dtest=PostprocessReadinessRuleTest test`
Expected: FAIL，因为新字段和新规则还不存在

**Step 3: 写最小实现**

给共享的 record DTO 和 entity 增加 `roundTotal`，同时实现一个最小的 readiness 判断器，只在数量完全一致时返回 ready。

**Step 4: 再跑测试确认通过**

Run: `mvn -pl call-ingestion -Dtest=PostprocessReadinessRuleTest test`
Expected: PASS

### Task 2: 增加 `round_total` 字段并删除 postprocess state 方案

**Files:**
- Modify: `call-ingestion/src/main/resources/db/migration/V1__create_call_event_outbox.sql` 按需要调整
- Create: `call-ingestion/src/main/resources/db/migration/V3__add_call_record_round_total.sql`
- Create or Modify: `call-ingestion/src/test/java/com/callcenter/ingestion/service/SchemaSmokeTest.java`

**Step 1: 先写失败测试**

覆盖点：
- `call_record` 表新增 `round_total`
- 不再要求存在 `call_postprocess_state`
- 旧的 orchestrator 相关表结构不再是新后处理流程的必要条件

**Step 2: 运行测试确认先失败**

Run: `mvn -pl call-ingestion -Dtest=SchemaSmokeTest test`
Expected: FAIL，因为当前 schema 还不符合新设计

**Step 3: 写最小实现**

新增 `call_record.round_total` 的 migration，并移除对 `call_postprocess_state` 的依赖假设。

**Step 4: 再跑测试确认通过**

Run: `mvn -pl call-ingestion -Dtest=SchemaSmokeTest test`
Expected: PASS

### Task 3: 在主 MySQL 写链路中打通 `roundTotal`

**Files:**
- Modify: `call-ingestion/src/main/java/com/callcenter/ingestion/service/CallRecordMysqlService.java`
- Modify: `call-common/src/main/java/com/callcenter/common/mapper/CallRecordMapper.java`
- Modify: `call-ingestion/src/test/java/com/callcenter/ingestion/service/...` 按需要补充

**Step 1: 先写失败测试**

覆盖点：
- record 落库时会写入 `round_total`
- `CALL_RECORD_PERSISTED` 的事件 payload 中包含 `roundTotal`
- 现有幂等行为保持不变

**Step 2: 运行测试确认先失败**

Run: `mvn -pl call-ingestion -Dtest=OutboxEventFactoryTest,CallIngestionIntegrationTest test`
Expected: FAIL，因为 `roundTotal` 还没有被落库和发布

**Step 3: 写最小实现**

把 `roundTotal` 从 DTO 贯通到 record 持久化、mapper SQL 和 outbox event payload。

**Step 4: 再跑测试确认通过**

Run: `mvn -pl call-ingestion -Dtest=OutboxEventFactoryTest,CallIngestionIntegrationTest test`
Expected: PASS

### Task 4: 引入 RocketMQ 消息抽象和基础配置

**Files:**
- Create: `call-ingestion/src/main/java/com/callcenter/ingestion/mq/MessagePublisher.java`
- Create: `call-ingestion/src/main/java/com/callcenter/ingestion/mq/OrderedMessagePublisher.java`
- Create: `call-ingestion/src/main/java/com/callcenter/ingestion/mq/DomainEventPublisher.java`
- Create: `call-ingestion/src/main/java/com/callcenter/ingestion/config/RocketMqProducerConfig.java`
- Create: `call-ingestion/src/main/java/com/callcenter/ingestion/config/RocketMqProperties.java`
- Create: `call-ingestion/src/test/java/com/callcenter/ingestion/config/RocketMqPropertiesTest.java`

**Step 1: 先写失败测试**

覆盖点：
- RocketMQ producer 配置可以正确绑定
- 顺序发布使用 `callId` 作为 sharding key
- 业务代码依赖的是消息抽象，不是 Kafka 类型

**Step 2: 运行测试确认先失败**

Run: `mvn -pl call-ingestion -Dtest=RocketMqPropertiesTest test`
Expected: FAIL，因为 RocketMQ 配置层还不存在

**Step 3: 写最小实现**

增加 RocketMQ typed properties 和 publisher 抽象，把 broker 细节收敛在基础设施层。

**Step 4: 再跑测试确认通过**

Run: `mvn -pl call-ingestion -Dtest=RocketMqPropertiesTest test`
Expected: PASS

### Task 5: 用 RocketMQ 替换 outbox 和失败投递中的 Kafka producer

**Files:**
- Modify: `call-ingestion/src/main/java/com/callcenter/ingestion/outbox/OutboxPublisher.java`
- Modify: `call-ingestion/src/main/java/com/callcenter/ingestion/service/FailurePublisher.java`
- Modify: `call-ingestion/src/test/java/com/callcenter/ingestion/outbox/OutboxPublisherTest.java`
- Modify: `call-ingestion/src/test/java/com/callcenter/ingestion/service/FailurePublisherTest.java`

**Step 1: 先写失败测试**

覆盖点：
- outbox 通过 RocketMQ 抽象发布消息
- 失败消息通过业务 DLQ 发布，不再依赖 Kafka 类
- 发布成功和失败时的状态迁移逻辑不变

**Step 2: 运行测试确认先失败**

Run: `mvn -pl call-ingestion -Dtest=OutboxPublisherTest,FailurePublisherTest test`
Expected: FAIL，因为服务还依赖 `KafkaTemplate`

**Step 3: 写最小实现**

把 `KafkaTemplate` 的调用替换成新的 RocketMQ publisher 接口，同时保持 outbox 和 DLQ 的行为一致。

**Step 4: 再跑测试确认通过**

Run: `mvn -pl call-ingestion -Dtest=OutboxPublisherTest,FailurePublisherTest test`
Expected: PASS

### Task 6: 用 RocketMQ 顺序 consumer 替换 Kafka consumer，并删除 `MessageBatchProcessor`

**Files:**
- Modify: `call-ingestion/src/main/java/com/callcenter/ingestion/consumer/CallRecordConsumer.java`
- Modify: `call-ingestion/src/main/java/com/callcenter/ingestion/consumer/CallRoundConsumer.java`
- Create: `call-ingestion/src/main/java/com/callcenter/ingestion/config/RocketMqConsumerConfig.java`
- Delete or Refactor: `call-ingestion/src/main/java/com/callcenter/ingestion/processor/MessageBatchProcessor.java`
- Delete or Refactor: `call-ingestion/src/test/java/com/callcenter/ingestion/processor/MessageBatchProcessorTest.java`
- Modify: `call-ingestion/src/test/java/com/callcenter/ingestion/CallIngestionIntegrationTest.java`

**Step 1: 先写失败测试**

覆盖点：
- 主 consumer 能正确解析 RocketMQ 消息
- 同一个 `callId` 的消息进入顺序消费路径
- 瞬时失败通过 RocketMQ retry 暴露，而不是 Kafka ack 语义
- 主写消费组通过 `consumeThreadMax` 控制并发
- 不再依赖 `MessageBatchProcessor`

**Step 2: 运行测试确认先失败**

Run: `mvn -pl call-ingestion -Dtest=CallIngestionIntegrationTest test`
Expected: FAIL，因为 consumer 和测试仍然建立在 Kafka listener / MessageBatchProcessor 上

**Step 3: 写最小实现**

把 Kafka listener 和 Kafka record 替换成 RocketMQ consumer 及内部适配层，consumer 直接调用各自 service，并删除 `MessageBatchProcessor`。

**Step 4: 再跑测试确认通过**

Run: `mvn -pl call-ingestion -Dtest=CallIngestionIntegrationTest test`
Expected: PASS

### Task 7: 保留 persisted-event 索引链路，但移除 orchestrator 和 ready-event 方案

**Files:**
- Modify: `call-ingestion/src/main/java/com/callcenter/ingestion/consumer/CallRecordPersistedConsumer.java`
- Modify: `call-ingestion/src/main/java/com/callcenter/ingestion/consumer/CallRoundPersistedConsumer.java`
- Modify: `call-ingestion/src/test/java/com/callcenter/ingestion/consumer/PersistedIndexConsumerTest.java`
- Delete: `call-ingestion/src/main/java/com/callcenter/ingestion/orchestrator/CallOrchestratorConsumer.java`
- Delete or Refactor: `call-ingestion/src/main/java/com/callcenter/ingestion/orchestrator/ReadyRuleEvaluator.java`
- Modify or Delete: `call-ingestion/src/test/java/com/callcenter/ingestion/orchestrator/CallOrchestratorConsumerTest.java`
- Modify: `call-ingestion/src/main/java/com/callcenter/ingestion/config/PostprocessProperties.java`

**Step 1: 先写失败测试**

覆盖点：
- persisted-event consumer 仍然支持下游索引
- 不再生成 `CALL_READY_FOR_AI` 和 `CALL_READY_FOR_EXTERNAL_SYNC`
- `CALL_RECORD_PERSISTED` 继续作为 LLM 和第三方的直接触发事件

**Step 2: 运行测试确认先失败**

Run: `mvn -pl call-ingestion -Dtest=PersistedIndexConsumerTest test`
Expected: FAIL，因为旧的 orchestrator 语义还存在

**Step 3: 写最小实现**

保留 persisted-event indexing consumer，删除 orchestrator 和 ready-event 逻辑，并把 `CALL_RECORD_PERSISTED` 收敛为 LLM、第三方和索引链路共用的事件。

**Step 4: 再跑测试确认通过**

Run: `mvn -pl call-ingestion -Dtest=PersistedIndexConsumerTest test`
Expected: PASS

### Task 8: 增加基于数量判断的 LLM / 第三方 consumer

**Files:**
- Create: `call-ingestion/src/main/java/com/callcenter/ingestion/postprocess/LlmSummaryConsumer.java`
- Create: `call-ingestion/src/main/java/com/callcenter/ingestion/postprocess/ThirdPartySyncConsumer.java`
- Create: `call-ingestion/src/main/java/com/callcenter/ingestion/postprocess/RecordPersistedGateService.java`
- Create: `call-ingestion/src/main/java/com/callcenter/ingestion/postprocess/IncompleteDataPolicy.java`
- Modify: `call-ingestion/src/main/java/com/callcenter/ingestion/config/RocketMqConsumerConfig.java`
- Modify: `call-common/src/main/java/com/callcenter/common/mapper/CallRoundMapper.java`
- Create: `call-ingestion/src/test/java/com/callcenter/ingestion/postprocess/LlmSummaryConsumerTest.java`
- Create: `call-ingestion/src/test/java/com/callcenter/ingestion/postprocess/ThirdPartySyncConsumerTest.java`
- Create: `call-ingestion/src/test/java/com/callcenter/ingestion/postprocess/RecordPersistedGateServiceTest.java`

**Step 1: 先写失败测试**

覆盖点：
- 消费 `CALL_RECORD_PERSISTED` 时会查询 `roundTotal` 和实际 round 数量
- round 数量完全一致时会正常执行 LLM 和第三方逻辑
- round 数量不足且重试次数未到阈值时，consumer 返回重新投递
- round 数量不足但重试次数达到阈值时，consumer 按不完整数据继续执行
- LLM 和第三方消费组可以分别配置各自的 `consumeThreadMax`
- 非法 `roundTotal` 或错误事件 payload 会进入业务 DLQ

**Step 2: 运行测试确认先失败**

Run: `mvn -pl call-ingestion -Dtest=LlmSummaryConsumerTest,ThirdPartySyncConsumerTest,RecordPersistedGateServiceTest test`
Expected: FAIL，因为新的下游消费和 gate 逻辑还不存在

**Step 3: 写最小实现**

实现新的 gate service、LLM consumer、第三方 consumer 和 round 数量查询逻辑。

**Step 4: 再跑测试确认通过**

Run: `mvn -pl call-ingestion -Dtest=LlmSummaryConsumerTest,ThirdPartySyncConsumerTest,RecordPersistedGateServiceTest test`
Expected: PASS

### Task 9: 增加“数据未齐时继续重投，超阈值后继续执行”的配置

**Files:**
- Modify: `call-ingestion/src/main/java/com/callcenter/ingestion/config/PostprocessProperties.java`
- Modify: `call-ingestion/src/test/java/com/callcenter/ingestion/config/PostprocessPropertiesTest.java`
- Create: `call-ingestion/src/test/java/com/callcenter/ingestion/postprocess/IncompleteDataPolicyTest.java`

**Step 1: 先写失败测试**

覆盖点：
- LLM 和第三方各自可以配置“不完整数据最大可重投次数”
- 在阈值以内数量未齐会继续重投
- 超过阈值后即使数量未齐也会继续执行
- 相关日志或指标包含“不完整数据执行”标记

**Step 2: 运行测试确认先失败**

Run: `mvn -pl call-ingestion -Dtest=IncompleteDataPolicyTest,PostprocessPropertiesTest test`
Expected: FAIL，因为新的不完整数据策略和配置还不存在

**Step 3: 写最小实现**

实现不完整数据策略和相关配置，让 consumer 可以根据重试次数决定继续重投还是继续执行。

**Step 4: 再跑测试确认通过**

Run: `mvn -pl call-ingestion -Dtest=IncompleteDataPolicyTest,PostprocessPropertiesTest test`
Expected: PASS

### Task 10: 移除 Kafka 依赖、配置和部署资产

**Files:**
- Modify: `call-common/pom.xml`
- Modify: `call-ingestion/pom.xml`
- Modify: `call-ingestion/src/main/resources/application.yml`
- Modify: `deploy/docker-compose.yml`
- Modify: `deploy/k8s/configmap.yaml`
- Modify: `README.md`
- Delete or Refactor: `call-ingestion/src/main/java/com/callcenter/ingestion/config/KafkaConsumerConfig.java`
- Delete or Refactor: `call-ingestion/src/main/java/com/callcenter/ingestion/config/KafkaProducerConfig.java`

**Step 1: 先写失败测试**

覆盖点：
- 应用上下文不再依赖 Kafka bean
- RocketMQ 配置成为唯一有效的 broker 配置
- 每个消费组拥有独立的并发配置
- 本地部署文档和 compose 不再拉起 Kafka

**Step 2: 运行测试确认先失败**

Run: `mvn -pl call-ingestion -Dtest=PostprocessPropertiesTest,RocketMqPropertiesTest test`
Expected: FAIL，因为 Kafka 配置仍然存在

**Step 3: 写最小实现**

移除 Kafka 依赖和配置，补上 RocketMQ 环境配置，并更新文档和部署清单。

**Step 4: 再跑测试确认通过**

Run: `mvn -pl call-ingestion -Dtest=PostprocessPropertiesTest,RocketMqPropertiesTest test`
Expected: PASS

### Task 11: 做模块级验证并记录剩余风险

**Files:**
- Modify: 验证后需要修正的文件
- Modify: `docs/plans/2026-05-20-rocketmq-refactor.md` 如果验证结果导致执行顺序变化

**Step 1: 运行聚焦测试**

Run: `mvn -pl call-ingestion -Dtest=PostprocessReadinessRuleTest,SchemaSmokeTest,OutboxEventFactoryTest,OutboxPublisherTest,FailurePublisherTest,PersistedIndexConsumerTest,LlmSummaryConsumerTest,ThirdPartySyncConsumerTest,RecordPersistedGateServiceTest,IncompleteDataPolicyTest,PostprocessPropertiesTest,RocketMqPropertiesTest,CallIngestionIntegrationTest test`
Expected: PASS

**Step 2: 运行模块测试集**

Run: `mvn -pl call-ingestion test`
Expected: PASS

**Step 3: 运行模块编译**

Run: `mvn -pl call-ingestion -DskipTests compile`
Expected: PASS

**Step 4: 记录剩余缺口**

需要记录：
- RocketMQ topic 和 consumer group 的初始化流程
- 同一个 `callId` 有序时 queue 数量怎么定
- 各消费组 `consumeThreadMax` 怎么配，以及如何和连接池、下游限流匹配
- DLQ replay 工具怎么做
- LLM 和第三方对“不完整数据继续执行”的可观测性怎么做
- LLM 和第三方 consumer 的幂等要求
