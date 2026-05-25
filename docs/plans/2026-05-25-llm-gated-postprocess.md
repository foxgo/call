# LLM Gate Postprocess Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add an LLM-gated postprocess pipeline so Elasticsearch sync and third-party push wait for analysis completion when the global LLM switch is enabled, while degraded flow still proceeds after retry exhaustion.

**Architecture:** Keep `call_record_persisted` as the raw persistence event, add a non-sharded `call_analysis_result` table for the current effective analysis result, and publish a new `call_record_analysis_completed` event from the analysis stage through the existing outbox. Move ES sync and third-party push to consume only the new completion event so gating logic is centralized in one place.

**Tech Stack:** Java 21, Spring Boot 3.2, RocketMQ Spring Boot starter, MyBatis-Plus, Flyway, JUnit 5, Mockito

---

### Task 1: Add the analysis-result persistence model

**Files:**
- Create: `call-common/src/main/java/com/callcenter/common/entity/CallAnalysisResultEntity.java`
- Create: `call-common/src/main/java/com/callcenter/common/mapper/CallAnalysisResultMapper.java`
- Create: `call-common/src/main/java/com/callcenter/common/model/AnalysisResultStatus.java`
- Create: `call-ingestion/src/main/resources/db/migration/V4__create_call_analysis_result.sql`
- Test: `call-ingestion/src/test/java/com/callcenter/ingestion/service/CallAnalysisResultServiceTest.java`

**Step 1: Write the failing test**

```java
@Test
void shouldUpsertSucceededResultByTenantIdAndCallId() {
    CallAnalysisResultService service = new CallAnalysisResultService(mapper);

    service.saveSucceeded(9L, 1001L, List.of("RISK"), true, 0.92f, "v1");

    verify(mapper).upsert(any(CallAnalysisResultEntity.class));
}
```

**Step 2: Run test to verify it fails**

Run: `mvn -pl call-ingestion -Dtest=CallAnalysisResultServiceTest test`

Expected: FAIL because `CallAnalysisResultService` or analysis-result model does not exist yet.

**Step 3: Write minimal implementation**

```java
public enum AnalysisResultStatus {
    SUCCEEDED,
    DEGRADED
}
```

```java
public void saveSucceeded(...) {
    mapper.upsert(entity);
}
```

**Step 4: Run test to verify it passes**

Run: `mvn -pl call-ingestion -Dtest=CallAnalysisResultServiceTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add call-common/src/main/java/com/callcenter/common/entity/CallAnalysisResultEntity.java \
  call-common/src/main/java/com/callcenter/common/mapper/CallAnalysisResultMapper.java \
  call-common/src/main/java/com/callcenter/common/model/AnalysisResultStatus.java \
  call-ingestion/src/main/resources/db/migration/V4__create_call_analysis_result.sql \
  call-ingestion/src/test/java/com/callcenter/ingestion/service/CallAnalysisResultServiceTest.java
git commit -m "ingestion: add analysis result persistence"
```

### Task 2: Extend postprocess configuration and event routing

**Files:**
- Modify: `call-ingestion/src/main/java/com/callcenter/ingestion/config/PostprocessProperties.java`
- Modify: `call-ingestion/src/main/resources/application.yml`
- Modify: `call-ingestion/src/main/java/com/callcenter/ingestion/service/OutboxEventFactory.java`
- Modify: `call-ingestion/src/main/java/com/callcenter/ingestion/outbox/OutboxPublisher.java`
- Test: `call-ingestion/src/test/java/com/callcenter/ingestion/config/PostprocessPropertiesTest.java`
- Test: `call-ingestion/src/test/java/com/callcenter/ingestion/outbox/OutboxPublisherTest.java`

**Step 1: Write the failing tests**

```java
@Test
void shouldBindAnalysisCompletedTopicAndLlmSwitch() {
    assertThat(properties.getTopics().getAnalysisCompleted()).isEqualTo("call_record_analysis_completed");
    assertThat(properties.isLlmEnabled()).isTrue();
}
```

```java
@Test
void shouldPublishAnalysisCompletedToConfiguredTopic() {
    CallEventOutboxEntity event = claimedEvent(2L, "call_record_analysis_completed", "1001");

    publisher.publishPendingBatch();

    verify(messagePublisher).publish("call_record_analysis_completed", "1001", event.getPayload());
}
```

**Step 2: Run tests to verify they fail**

Run: `mvn -pl call-ingestion -Dtest=PostprocessPropertiesTest,OutboxPublisherTest test`

Expected: FAIL because the new property and event type mapping do not exist.

**Step 3: Write minimal implementation**

```java
public static class Topics {
    private String recordPersisted = "call_record_persisted";
    private String analysisCompleted = "call_record_analysis_completed";
}
```

```java
case "call_record_analysis_completed" -> postprocessProperties.getTopics().getAnalysisCompleted();
```

**Step 4: Run tests to verify they pass**

Run: `mvn -pl call-ingestion -Dtest=PostprocessPropertiesTest,OutboxPublisherTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add call-ingestion/src/main/java/com/callcenter/ingestion/config/PostprocessProperties.java \
  call-ingestion/src/main/resources/application.yml \
  call-ingestion/src/main/java/com/callcenter/ingestion/service/OutboxEventFactory.java \
  call-ingestion/src/main/java/com/callcenter/ingestion/outbox/OutboxPublisher.java \
  call-ingestion/src/test/java/com/callcenter/ingestion/config/PostprocessPropertiesTest.java \
  call-ingestion/src/test/java/com/callcenter/ingestion/outbox/OutboxPublisherTest.java
git commit -m "ingestion: route analysis completed events"
```

### Task 3: Add the analysis orchestration service and LLM client abstraction

**Files:**
- Create: `call-ingestion/src/main/java/com/callcenter/ingestion/service/CallAnalysisOrchestratorService.java`
- Create: `call-ingestion/src/main/java/com/callcenter/ingestion/service/CallAnalysisClient.java`
- Create: `call-ingestion/src/main/java/com/callcenter/ingestion/model/CallAnalysisRequest.java`
- Create: `call-ingestion/src/main/java/com/callcenter/ingestion/model/CallAnalysisResponse.java`
- Modify: `call-ingestion/src/main/java/com/callcenter/ingestion/service/OutboxEventFactory.java`
- Test: `call-ingestion/src/test/java/com/callcenter/ingestion/service/CallAnalysisOrchestratorServiceTest.java`

**Step 1: Write the failing tests**

```java
@Test
void shouldSaveSucceededResultAndCreateCompletionOutboxWhenLlmEnabled() {
    service.handlePersistedEvent(event, 0);

    verify(analysisClient).analyze(any(CallAnalysisRequest.class));
    verify(resultService).saveSucceeded(...);
    verify(outboxMapper).insert(any(CallEventOutboxEntity.class));
}
```

```java
@Test
void shouldCreateCompletionOutboxWithoutCallingLlmWhenDisabled() {
    service.handlePersistedEvent(event, 0);

    verifyNoInteractions(analysisClient);
    verify(outboxMapper).insert(any(CallEventOutboxEntity.class));
}
```

**Step 2: Run tests to verify they fail**

Run: `mvn -pl call-ingestion -Dtest=CallAnalysisOrchestratorServiceTest test`

Expected: FAIL because the orchestration service does not exist.

**Step 3: Write minimal implementation**

```java
if (!postprocessProperties.isLlmEnabled()) {
    outboxMapper.insert(outboxEventFactory.analysisCompleted(record, null));
    return;
}
```

```java
CallAnalysisResponse response = analysisClient.analyze(request);
resultService.saveSucceeded(...);
outboxMapper.insert(outboxEventFactory.analysisCompleted(record, AnalysisResultStatus.SUCCEEDED));
```

**Step 4: Run tests to verify they pass**

Run: `mvn -pl call-ingestion -Dtest=CallAnalysisOrchestratorServiceTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add call-ingestion/src/main/java/com/callcenter/ingestion/service/CallAnalysisOrchestratorService.java \
  call-ingestion/src/main/java/com/callcenter/ingestion/service/CallAnalysisClient.java \
  call-ingestion/src/main/java/com/callcenter/ingestion/model/CallAnalysisRequest.java \
  call-ingestion/src/main/java/com/callcenter/ingestion/model/CallAnalysisResponse.java \
  call-ingestion/src/main/java/com/callcenter/ingestion/service/OutboxEventFactory.java \
  call-ingestion/src/test/java/com/callcenter/ingestion/service/CallAnalysisOrchestratorServiceTest.java
git commit -m "ingestion: add analysis orchestration service"
```

### Task 4: Add the RocketMQ analysis consumer with degraded-flow semantics

**Files:**
- Create: `call-ingestion/src/main/java/com/callcenter/ingestion/consumer/RocketMqCallAnalysisConsumer.java`
- Modify: `call-ingestion/src/main/resources/application.yml`
- Modify: `call-ingestion/src/main/java/com/callcenter/ingestion/config/RocketMqProperties.java`
- Test: `call-ingestion/src/test/java/com/callcenter/ingestion/consumer/CallAnalysisConsumerTest.java`

**Step 1: Write the failing tests**

```java
@Test
void shouldRetryWhenLlmFailsBeforeMaxReconsumeTimes() {
    message.setReconsumeTimes(1);

    assertThatThrownBy(() -> consumer.onMessage(message))
            .isInstanceOf(IllegalStateException.class);
}
```

```java
@Test
void shouldDegradeAndCompleteWhenLlmFailsAtMaxReconsumeTimes() {
    message.setReconsumeTimes(3);

    consumer.onMessage(message);

    verify(orchestratorService).handlePersistedEvent(any(), eq(3));
}
```

**Step 2: Run tests to verify they fail**

Run: `mvn -pl call-ingestion -Dtest=CallAnalysisConsumerTest test`

Expected: FAIL because the consumer does not exist.

**Step 3: Write minimal implementation**

```java
DomainEventMessage event = objectMapper.readValue(body, DomainEventMessage.class);
orchestratorService.handlePersistedEvent(event, messageExt.getReconsumeTimes());
```

```java
catch (Exception exception) {
    throw new IllegalStateException("处理 RocketMQ 分析事件失败", exception);
}
```

**Step 4: Run tests to verify they pass**

Run: `mvn -pl call-ingestion -Dtest=CallAnalysisConsumerTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add call-ingestion/src/main/java/com/callcenter/ingestion/consumer/RocketMqCallAnalysisConsumer.java \
  call-ingestion/src/main/resources/application.yml \
  call-ingestion/src/main/java/com/callcenter/ingestion/config/RocketMqProperties.java \
  call-ingestion/src/test/java/com/callcenter/ingestion/consumer/CallAnalysisConsumerTest.java
git commit -m "ingestion: add call analysis consumer"
```

### Task 5: Move Elasticsearch sync behind analysis completion

**Files:**
- Modify: `call-ingestion/src/main/java/com/callcenter/ingestion/consumer/RocketMqPersistedEventConsumer.java`
- Modify: `call-ingestion/src/main/java/com/callcenter/ingestion/service/CallRecordIndexService.java`
- Modify: `call-ingestion/src/main/java/com/callcenter/ingestion/service/ElasticsearchBulkService.java`
- Test: `call-ingestion/src/test/java/com/callcenter/ingestion/consumer/PersistedIndexConsumerTest.java`

**Step 1: Write the failing tests**

```java
@Test
void shouldConsumeAnalysisCompletedEventAndIndexRecordWithAnalysisFields() {
    consumer.onMessage(message);

    verify(indexService).indexAnalysisCompletedEvent(any(DomainEventMessage.class));
}
```

```java
@Test
void shouldIndexDegradedEventWithoutTags() {
    indexService.indexAnalysisCompletedEvent(event);

    verify(bulkService).bulkIndexRecords(anyList());
}
```

**Step 2: Run tests to verify they fail**

Run: `mvn -pl call-ingestion -Dtest=PersistedIndexConsumerTest test`

Expected: FAIL because the consumer still only accepts `call_record_persisted`.

**Step 3: Write minimal implementation**

```java
case "call_record_analysis_completed" -> recordIndexService.indexAnalysisCompletedEvent(event);
```

```java
CallAnalysisResultEntity analysis = analysisResultService.findByTenantIdAndCallId(...);
```

**Step 4: Run tests to verify they pass**

Run: `mvn -pl call-ingestion -Dtest=PersistedIndexConsumerTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add call-ingestion/src/main/java/com/callcenter/ingestion/consumer/RocketMqPersistedEventConsumer.java \
  call-ingestion/src/main/java/com/callcenter/ingestion/service/CallRecordIndexService.java \
  call-ingestion/src/main/java/com/callcenter/ingestion/service/ElasticsearchBulkService.java \
  call-ingestion/src/test/java/com/callcenter/ingestion/consumer/PersistedIndexConsumerTest.java
git commit -m "ingestion: gate indexing on analysis completion"
```

### Task 6: Add third-party push on analysis completion

**Files:**
- Create: `call-ingestion/src/main/java/com/callcenter/ingestion/consumer/RocketMqThirdPartyPushConsumer.java`
- Create: `call-ingestion/src/main/java/com/callcenter/ingestion/service/ThirdPartyPushService.java`
- Test: `call-ingestion/src/test/java/com/callcenter/ingestion/consumer/ThirdPartyPushConsumerTest.java`
- Test: `call-ingestion/src/test/java/com/callcenter/ingestion/service/ThirdPartyPushServiceTest.java`

**Step 1: Write the failing tests**

```java
@Test
void shouldDispatchAnalysisCompletedEventToThirdPartyPushService() {
    consumer.onMessage(message);

    verify(pushService).pushAnalysisCompletedEvent(any(DomainEventMessage.class));
}
```

```java
@Test
void shouldBuildPushPayloadFromRecordRoundsAndAnalysisResult() {
    service.pushAnalysisCompletedEvent(event);

    verify(client).push(any());
}
```

**Step 2: Run tests to verify they fail**

Run: `mvn -pl call-ingestion -Dtest=ThirdPartyPushConsumerTest,ThirdPartyPushServiceTest test`

Expected: FAIL because the consumer and service do not exist.

**Step 3: Write minimal implementation**

```java
public void onMessage(MessageExt message) {
    DomainEventMessage event = objectMapper.readValue(body, DomainEventMessage.class);
    pushService.pushAnalysisCompletedEvent(event);
}
```

**Step 4: Run tests to verify they pass**

Run: `mvn -pl call-ingestion -Dtest=ThirdPartyPushConsumerTest,ThirdPartyPushServiceTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add call-ingestion/src/main/java/com/callcenter/ingestion/consumer/RocketMqThirdPartyPushConsumer.java \
  call-ingestion/src/main/java/com/callcenter/ingestion/service/ThirdPartyPushService.java \
  call-ingestion/src/test/java/com/callcenter/ingestion/consumer/ThirdPartyPushConsumerTest.java \
  call-ingestion/src/test/java/com/callcenter/ingestion/service/ThirdPartyPushServiceTest.java
git commit -m "ingestion: add third-party push consumer"
```

### Task 7: Finish integration coverage and document operational behavior

**Files:**
- Modify: `call-ingestion/src/test/java/com/callcenter/ingestion/outbox/OutboxPublisherTest.java`
- Modify: `call-ingestion/src/test/java/com/callcenter/ingestion/consumer/PersistedIndexConsumerTest.java`
- Create: `call-ingestion/src/test/java/com/callcenter/ingestion/service/CallAnalysisFlowTest.java`
- Modify: `README.md`
- Modify: `docs/plans/2026-05-25-llm-gated-postprocess-design.md`

**Step 1: Write the failing integration-style tests**

```java
@Test
void shouldPublishAnalysisCompletedAfterDegradedResultAtRetryLimit() {
    message.setReconsumeTimes(3);

    consumer.onMessage(message);

    verify(outboxMapper).insert(argThat(event -> event.getEventType().equals("call_record_analysis_completed")));
}
```

**Step 2: Run tests to verify they fail**

Run: `mvn -pl call-ingestion -Dtest=CallAnalysisFlowTest test`

Expected: FAIL because the full flow is not covered end-to-end yet.

**Step 3: Write minimal implementation and docs updates**

```java
assertThat(savedResult.getStatus()).isEqualTo("DEGRADED");
assertThat(publishedEvent.getEventType()).isEqualTo("call_record_analysis_completed");
```

Document:

- global LLM switch
- new `analysis-completed` topic
- degraded behavior after retry exhaustion

**Step 4: Run focused tests and module test suite**

Run: `mvn -pl call-ingestion -Dtest=CallAnalysisFlowTest,CallAnalysisConsumerTest,PersistedIndexConsumerTest,ThirdPartyPushConsumerTest test`

Expected: PASS

Run: `mvn -pl call-ingestion test`

Expected: PASS

**Step 5: Commit**

```bash
git add call-ingestion/src/test/java/com/callcenter/ingestion/outbox/OutboxPublisherTest.java \
  call-ingestion/src/test/java/com/callcenter/ingestion/consumer/PersistedIndexConsumerTest.java \
  call-ingestion/src/test/java/com/callcenter/ingestion/service/CallAnalysisFlowTest.java \
  README.md \
  docs/plans/2026-05-25-llm-gated-postprocess-design.md
git commit -m "ingestion: finish llm-gated postprocess flow"
```
