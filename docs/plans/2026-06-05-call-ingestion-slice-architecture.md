# Call Ingestion Slice Architecture Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Reorganize `call-ingestion` into `inbound / application / domain / infrastructure / support`, migrate ingestion-private types out of `call-common`, and preserve runtime behavior while tightening boundaries around each ingest and postprocess slice.

**Architecture:** The refactor will proceed in phases. First, migrate ingestion-private DTOs, entities, mappers, and enums into `call-ingestion` so package ownership matches runtime ownership. Then move existing consumers, orchestration services, persistence services, outbox code, and tests into slice-oriented packages under the new top-level layers, renaming infrastructure-heavy classes toward repository adapter semantics where needed.

**Tech Stack:** Java 21, Spring Boot 3.2, Maven multi-module build, MyBatis-Plus, RocketMQ Spring Boot starter, Elasticsearch Java client, JUnit 5, Mockito, Testcontainers

---

### Task 1: Inventory moved types with failing compile targets

**Files:**
- Modify: `call-ingestion/src/main/java/com/callcenter/ingestion/**/*.java`
- Modify: `call-ingestion/src/test/java/com/callcenter/ingestion/**/*.java`
- Modify: `call-common/src/main/java/com/callcenter/common/**/*.java`

**Step 1: Write the failing move set**

- Create a checklist of ingestion-private types to migrate:
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
- Identify every `call-ingestion` import that points to these `call-common` types.

**Step 2: Run compile to capture the current baseline**

Run: `mvn -pl call-ingestion -DskipTests compile`

Expected: PASS before moves, establishing a clean baseline.

### Task 2: Migrate ingestion-private DTOs, entities, mappers, and enum into `call-ingestion`

**Files:**
- Create: `call-ingestion/src/main/java/com/callcenter/ingestion/domain/record/CallRecordMessage.java`
- Create: `call-ingestion/src/main/java/com/callcenter/ingestion/domain/round/CallRoundMessage.java`
- Create: `call-ingestion/src/main/java/com/callcenter/ingestion/domain/analysis/DomainEventMessage.java`
- Create: `call-ingestion/src/main/java/com/callcenter/ingestion/domain/analysis/AnalysisResultStatus.java`
- Create: `call-ingestion/src/main/java/com/callcenter/ingestion/infrastructure/record/persistence/CallRecordEntity.java`
- Create: `call-ingestion/src/main/java/com/callcenter/ingestion/infrastructure/round/persistence/CallRoundEntity.java`
- Create: `call-ingestion/src/main/java/com/callcenter/ingestion/infrastructure/analysis/persistence/CallAnalysisResultEntity.java`
- Create: `call-ingestion/src/main/java/com/callcenter/ingestion/infrastructure/outbox/persistence/CallEventOutboxEntity.java`
- Create: `call-ingestion/src/main/java/com/callcenter/ingestion/infrastructure/deadletter/persistence/CallDeadLetterTaskEntity.java`
- Create: `call-ingestion/src/main/java/com/callcenter/ingestion/infrastructure/record/persistence/CallRecordMapper.java`
- Create: `call-ingestion/src/main/java/com/callcenter/ingestion/infrastructure/round/persistence/CallRoundMapper.java`
- Create: `call-ingestion/src/main/java/com/callcenter/ingestion/infrastructure/analysis/persistence/CallAnalysisResultMapper.java`
- Create: `call-ingestion/src/main/java/com/callcenter/ingestion/infrastructure/outbox/persistence/CallEventOutboxMapper.java`
- Create: `call-ingestion/src/main/java/com/callcenter/ingestion/infrastructure/deadletter/persistence/CallDeadLetterTaskMapper.java`

**Step 1: Copy exact implementations with package-only adjustments**

- Move the listed classes and interfaces into `call-ingestion`.
- Keep their contents unchanged except for package/import rewrites needed by the new location.
- Do not delete the `call-common` versions until `call-ingestion` compiles against the new paths.

**Step 2: Rewrite imports in `call-ingestion`**

Run: `rg -n "import com\\.callcenter\\.common\\.(dto|entity|mapper|model)\\." call-ingestion/src/main/java call-ingestion/src/test/java`

Expected: The result set will shrink to zero for the migrated types after import rewrites.

**Step 3: Run compile to verify the migration**

Run: `mvn -pl call-ingestion -DskipTests compile`

Expected: PASS.

**Step 4: Commit**

```bash
git add call-ingestion/src/main/java call-ingestion/src/test/java
git commit -m "ingestion: internalize write-side models"
```

### Task 3: Create the new top-level package structure

**Files:**
- Create: `call-ingestion/src/main/java/com/callcenter/ingestion/inbound/.gitkeep`
- Create: `call-ingestion/src/main/java/com/callcenter/ingestion/application/.gitkeep`
- Create: `call-ingestion/src/main/java/com/callcenter/ingestion/domain/.gitkeep`
- Create: `call-ingestion/src/main/java/com/callcenter/ingestion/infrastructure/.gitkeep`
- Create: `call-ingestion/src/main/java/com/callcenter/ingestion/support/.gitkeep`

**Step 1: Create package directories**

- Add the top-level directories and the initial slice subdirectories:
  - `record`
  - `round`
  - `analysis`
  - `postprocess`
  - `outbox`
  - `deadletter`

**Step 2: Compile sanity check**

Run: `mvn -pl call-ingestion -DskipTests compile`

Expected: PASS, since no behavior changed yet.

### Task 4: Move inbound consumers into slice-oriented `inbound` packages

**Files:**
- Modify: `call-ingestion/src/main/java/com/callcenter/ingestion/consumer/RocketMqCallRecordConsumer.java`
- Modify: `call-ingestion/src/main/java/com/callcenter/ingestion/consumer/RocketMqCallRoundConsumer.java`
- Modify: `call-ingestion/src/main/java/com/callcenter/ingestion/consumer/RocketMqCallAnalysisConsumer.java`
- Modify: `call-ingestion/src/main/java/com/callcenter/ingestion/consumer/RocketMqElasticSearchConsumer.java`
- Modify: `call-ingestion/src/main/java/com/callcenter/ingestion/consumer/RocketMqThirdPartyPushConsumer.java`
- Modify: `call-ingestion/src/main/java/com/callcenter/ingestion/consumer/dlq/*.java`
- Modify: `call-ingestion/src/test/java/com/callcenter/ingestion/consumer/*.java`

**Step 1: Move classes by business slice**

- `RocketMqCallRecordConsumer` -> `inbound.record`
- `RocketMqCallRoundConsumer` -> `inbound.round`
- `RocketMqCallAnalysisConsumer` -> `inbound.analysis`
- `RocketMqElasticSearchConsumer` and `RocketMqThirdPartyPushConsumer` -> `inbound.postprocess`
- DLQ consumers -> `inbound.deadletter`

**Step 2: Keep behavior unchanged**

- Update imports only.
- Leave listener annotations and message handling logic intact at this stage.

**Step 3: Run focused tests**

Run: `mvn -pl call-ingestion -Dtest=RocketMqCallRecordConsumerTest,RocketMqCallRoundConsumerTest,CallAnalysisConsumerTest,PersistedIndexConsumerTest,ThirdPartyPushConsumerTest,RocketMqDeadLetterConsumerTest test`

Expected: PASS.

**Step 4: Commit**

```bash
git add call-ingestion/src/main/java call-ingestion/src/test/java
git commit -m "ingestion: move mq consumers into inbound slices"
```

### Task 5: Move use-case orchestration into `application` packages

**Files:**
- Modify: `call-ingestion/src/main/java/com/callcenter/ingestion/service/CallRecordIngestionService.java`
- Modify: `call-ingestion/src/main/java/com/callcenter/ingestion/service/CallRoundIngestionService.java`
- Modify: `call-ingestion/src/main/java/com/callcenter/ingestion/service/CallAnalysisOrchestratorService.java`
- Modify: `call-ingestion/src/main/java/com/callcenter/ingestion/service/CallAnalysisResultService.java`
- Modify: `call-ingestion/src/main/java/com/callcenter/ingestion/service/CallRecordIndexService.java`
- Modify: `call-ingestion/src/main/java/com/callcenter/ingestion/service/ThirdPartyPushService.java`
- Modify: `call-ingestion/src/main/java/com/callcenter/ingestion/service/DeadLetterTaskService.java`
- Modify: `call-ingestion/src/main/java/com/callcenter/ingestion/outbox/OutboxPublisher.java`
- Modify: `call-ingestion/src/main/java/com/callcenter/ingestion/service/OutboxEventFactory.java`
- Modify: `call-ingestion/src/test/java/com/callcenter/ingestion/service/*.java`
- Modify: `call-ingestion/src/test/java/com/callcenter/ingestion/outbox/*.java`

**Step 1: Move classes by slice**

- `CallRecordIngestionService` -> `application.record`
- `CallRoundIngestionService` -> `application.round`
- `CallAnalysisOrchestratorService` and `CallAnalysisResultService` -> `application.analysis`
- `CallRecordIndexService` and `ThirdPartyPushService` -> `application.postprocess`
- `DeadLetterTaskService` -> `application.deadletter`
- `OutboxPublisher` and `OutboxEventFactory` -> `application.outbox`

**Step 2: Keep Spring wiring stable**

- Preserve constructor injection.
- Preserve public method signatures unless a package-private helper is being tightened.

**Step 3: Run focused tests**

Run: `mvn -pl call-ingestion -Dtest=CallRecordIngestionServiceTest,CallRoundIngestionServiceTest,CallAnalysisOrchestratorServiceTest,CallAnalysisResultServiceTest,ThirdPartyPushServiceTest,DeadLetterTaskServiceTest,OutboxEventFactoryTest,OutboxPublisherTest test`

Expected: PASS.

**Step 4: Commit**

```bash
git add call-ingestion/src/main/java call-ingestion/src/test/java
git commit -m "ingestion: move use cases into application slices"
```

### Task 6: Move shared domain models and rules into `domain`

**Files:**
- Modify: `call-ingestion/src/main/java/com/callcenter/ingestion/model/InboundMessage.java`
- Modify: `call-ingestion/src/main/java/com/callcenter/ingestion/model/MessageType.java`
- Modify: `call-ingestion/src/main/java/com/callcenter/ingestion/model/CallAnalysisRequest.java`
- Modify: `call-ingestion/src/main/java/com/callcenter/ingestion/model/CallAnalysisResponse.java`
- Modify: `call-ingestion/src/main/java/com/callcenter/ingestion/model/ThirdPartyPushRequest.java`
- Modify: `call-ingestion/src/main/java/com/callcenter/ingestion/processor/MessageKeys.java`

**Step 1: Move models into the right slice**

- `InboundMessage` and `MessageType` -> `domain.shared`
- `CallAnalysisRequest` and `CallAnalysisResponse` -> `domain.analysis`
- `ThirdPartyPushRequest` -> `domain.postprocess`
- `MessageKeys` -> `domain.shared`

**Step 2: Add lightweight rule placement**

- If `record` round-count validation or `analysis` degrade decision can be isolated without behavior changes, add a small domain helper for that rule.
- Do not introduce speculative abstractions.

**Step 3: Run focused tests**

Run: `mvn -pl call-ingestion -Dtest=CallRecordIngestionServiceTest,CallAnalysisOrchestratorServiceTest,ThirdPartyPushServiceTest,RocketMqDeadLetterConsumerTest test`

Expected: PASS.

### Task 7: Move persistence adapters and technical implementations into `infrastructure`

**Files:**
- Modify: `call-ingestion/src/main/java/com/callcenter/ingestion/service/CallRecordMysqlService.java`
- Modify: `call-ingestion/src/main/java/com/callcenter/ingestion/service/CallRoundMysqlService.java`
- Modify: `call-ingestion/src/main/java/com/callcenter/ingestion/service/ElasticsearchBulkService.java`
- Modify: `call-ingestion/src/main/java/com/callcenter/ingestion/service/CallAnalysisClient.java`
- Modify: `call-ingestion/src/main/java/com/callcenter/ingestion/service/ThirdPartyPushClient.java`
- Modify: `call-ingestion/src/main/java/com/callcenter/ingestion/outbox/OutboxRepository.java`
- Modify: `call-ingestion/src/main/java/com/callcenter/ingestion/outbox/OutboxStatus.java`
- Modify: `call-ingestion/src/main/java/com/callcenter/ingestion/mq/MessagePublisher.java`
- Modify: `call-ingestion/src/main/java/com/callcenter/ingestion/mq/RocketMqMessagePublisher.java`
- Modify: `call-ingestion/src/main/java/com/callcenter/ingestion/consumer/BaseRocketMQListener.java`

**Step 1: Move implementations into infrastructure packages**

- Persistence-heavy classes move under `infrastructure.<slice>.persistence`
- MQ abstractions and publisher move under `infrastructure.mq`
- External clients move under `infrastructure.analysis.client` and `infrastructure.postprocess.client`
- ES writer moves under `infrastructure.postprocess.search`

**Step 2: Rename infrastructure-shaped services**

- `CallRecordMysqlService` -> `MybatisCallRecordRepository`
- `CallRoundMysqlService` -> `MybatisCallRoundRepository`

Do this only after package moves are stable so refactor scope stays readable.

**Step 3: Run focused tests**

Run: `mvn -pl call-ingestion -Dtest=CallRecordIngestionServiceTest,CallRoundIngestionServiceTest,CallAnalysisOrchestratorServiceTest,ThirdPartyPushServiceTest,OutboxRepositoryTest,OutboxPublisherTest,RocketMqMessagePublisherTest test`

Expected: PASS.

**Step 4: Commit**

```bash
git add call-ingestion/src/main/java call-ingestion/src/test/java
git commit -m "ingestion: move adapters into infrastructure"
```

### Task 8: Move shared technical helpers into `support`

**Files:**
- Modify: `call-ingestion/src/main/java/com/callcenter/ingestion/config/WriteMetrics.java`
- Modify: `call-ingestion/src/main/java/com/callcenter/ingestion/config/WriteMetricsConfig.java`
- Modify: `call-ingestion/src/main/java/com/callcenter/ingestion/config/*.java`

**Step 1: Split true support code from infrastructure config**

- Keep Spring bootstrapping and binding classes in `infrastructure.config`
- Move only neutral helpers such as metrics wrappers or JSON utility shims into `support`
- Do not migrate `ShardingRouter` or other shared infra out of `call-common`

**Step 2: Run compile**

Run: `mvn -pl call-ingestion -DskipTests compile`

Expected: PASS.

### Task 9: Delete obsolete packages after all imports are clean

**Files:**
- Delete: `call-ingestion/src/main/java/com/callcenter/ingestion/consumer/**`
- Delete: `call-ingestion/src/main/java/com/callcenter/ingestion/service/**`
- Delete: `call-ingestion/src/main/java/com/callcenter/ingestion/model/**`
- Delete: `call-ingestion/src/main/java/com/callcenter/ingestion/mq/**`
- Delete: `call-ingestion/src/main/java/com/callcenter/ingestion/outbox/**`
- Delete: `call-ingestion/src/main/java/com/callcenter/ingestion/processor/**`

**Step 1: Verify old packages are no longer referenced**

Run: `rg -n "com\\.callcenter\\.ingestion\\.(consumer|service|model|mq|outbox|processor)" call-ingestion/src/main/java call-ingestion/src/test/java`

Expected: No references except maybe comments that should also be cleaned up.

**Step 2: Delete empty or obsolete sources**

- Remove old package directories only after code and tests reference the new paths exclusively.

**Step 3: Run full module test suite**

Run: `mvn -pl call-ingestion test`

Expected: PASS.

**Step 4: Commit**

```bash
git add call-ingestion/src/main/java call-ingestion/src/test/java
git commit -m "ingestion: remove legacy package structure"
```

### Task 10: Verify no ingestion-private dependency remains in `call-common`

**Files:**
- Modify: `call-common/src/main/java/com/callcenter/common/**/*.java`
- Modify: `call-ingestion/pom.xml`

**Step 1: Search for now-orphaned shared classes**

Run: `rg -n "class (CallRecordMessage|CallRoundMessage|DomainEventMessage|CallRecordEntity|CallRoundEntity|CallAnalysisResultEntity|CallEventOutboxEntity|CallDeadLetterTaskEntity)|interface (CallRecordMapper|CallRoundMapper|CallAnalysisResultMapper|CallEventOutboxMapper|CallDeadLetterTaskMapper)|enum AnalysisResultStatus" call-common/src/main/java`

Expected: These definitions are either removed from `call-common` or left temporarily only if another module still truly needs them.

**Step 2: Remove the `call-common` dependency if fully unused**

- If `call-ingestion` still needs shared routing/config infrastructure, keep the dependency.
- Otherwise, remove the dependency and recompile.

**Step 3: Run compile and targeted tests**

Run: `mvn -pl call-ingestion -DskipTests compile`

Expected: PASS.

### Task 11: Run final verification before claiming completion

**Files:**
- No file changes

**Step 1: Run focused regression tests**

Run: `mvn -pl call-ingestion -Dtest=RocketMqCallRecordConsumerTest,RocketMqCallRoundConsumerTest,CallAnalysisConsumerTest,PersistedIndexConsumerTest,ThirdPartyPushConsumerTest,RocketMqDeadLetterConsumerTest,CallRecordIngestionServiceTest,CallRoundIngestionServiceTest,CallAnalysisOrchestratorServiceTest,CallAnalysisResultServiceTest,ThirdPartyPushServiceTest,DeadLetterTaskServiceTest,OutboxEventFactoryTest,OutboxPublisherTest,OutboxRepositoryTest,RocketMqMessagePublisherTest test`

Expected: PASS.

**Step 2: Run full module test suite**

Run: `mvn -pl call-ingestion test`

Expected: PASS.

**Step 3: Commit**

```bash
git add call-ingestion/src/main/java call-ingestion/src/test/java call-common/src/main/java call-ingestion/pom.xml
git commit -m "refactor: reorganize call ingestion by slice"
```
