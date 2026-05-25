# Postprocess DLQ Consumers Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add RocketMQ auto-DLQ consumers for the index, AI, and third-party postprocess groups and persist their dead-letter messages as compensation tasks.

**Architecture:** Reuse the existing `%DLQ%<consumerGroup>` listeners and shared `DeadLetterTaskService`, extending dead-letter classification to include postprocess message types and using `DomainEventMessage.eventId` as the idempotency key for postprocess events.

**Tech Stack:** Java 21, Spring Boot 3.2, RocketMQ Spring Boot starter, MyBatis-Plus, JUnit 5, Mockito

---

### Task 1: Extend tests for postprocess DLQ support

**Files:**
- Modify: `call-ingestion/src/test/java/com/callcenter/ingestion/consumer/RocketMqDeadLetterConsumerTest.java`
- Modify: `call-ingestion/src/test/java/com/callcenter/ingestion/service/DeadLetterTaskServiceTest.java`
- Modify: `call-ingestion/src/test/java/com/callcenter/ingestion/config/RocketMqPropertiesTest.java`
- Modify: `call-ingestion/src/test/java/com/callcenter/ingestion/config/RocketMqListenerContainerCustomizerTest.java`

**Step 1: Write the failing tests**

- Add assertions that `index` / `ai` / `third-party` DLQ consumers delegate to `DeadLetterTaskService`.
- Add assertions that postprocess DLQ tasks persist `message_type` and `idempotency_key=eventId`.
- Add property binding assertions for `index-dlq` / `ai-dlq` / `third-party-dlq`.
- Add listener customizer assertions for at least the new DLQ consumer groups.

**Step 2: Run tests to verify they fail**

Run: `mvn -pl call-ingestion -Dtest=RocketMqDeadLetterConsumerTest,DeadLetterTaskServiceTest,RocketMqPropertiesTest,RocketMqListenerContainerCustomizerTest test`

Expected: FAIL because postprocess DLQ consumers and message type handling do not exist yet.

### Task 2: Implement postprocess DLQ consumers and type handling

**Files:**
- Create: `call-ingestion/src/main/java/com/callcenter/ingestion/consumer/RocketMqIndexDeadLetterConsumer.java`
- Create: `call-ingestion/src/main/java/com/callcenter/ingestion/consumer/RocketMqAiDeadLetterConsumer.java`
- Create: `call-ingestion/src/main/java/com/callcenter/ingestion/consumer/RocketMqThirdPartyDeadLetterConsumer.java`
- Modify: `call-ingestion/src/main/java/com/callcenter/ingestion/model/MessageType.java`
- Modify: `call-ingestion/src/main/java/com/callcenter/ingestion/service/DeadLetterTaskService.java`
- Modify: `call-ingestion/src/main/java/com/callcenter/ingestion/processor/MessageKeys.java`
- Modify: `call-ingestion/src/main/java/com/callcenter/ingestion/config/RocketMqProperties.java`
- Modify: `call-ingestion/src/main/java/com/callcenter/ingestion/config/RocketMqListenerContainerCustomizer.java`
- Modify: `call-ingestion/src/main/resources/application.yml`

**Step 1: Write minimal implementation**

- Add new postprocess `MessageType` values.
- Add `MessageKeys.domainEventIdempotencyKey`.
- Teach `DeadLetterTaskService` to use `eventId` for postprocess dead-letter idempotency keys.
- Add new DLQ consumer classes bound to `%DLQ%${call.rocketmq.consumers.<source>.group}`.
- Add properties and listener customizer coverage for the new DLQ groups.

**Step 2: Run tests to verify they pass**

Run: `mvn -pl call-ingestion -Dtest=RocketMqDeadLetterConsumerTest,DeadLetterTaskServiceTest,RocketMqPropertiesTest,RocketMqListenerContainerCustomizerTest test`

Expected: PASS.
