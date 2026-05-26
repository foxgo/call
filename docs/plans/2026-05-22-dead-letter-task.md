# Dead Letter Task Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add generic RocketMQ DLQ consumers that persist replay-ready compensation tasks into MySQL instead of replaying immediately.

**Architecture:** Use RocketMQ broker auto-DLQ topics (`%DLQ%<source-consumer-group>`) as the only dead-letter ingress, add a shared dead-letter task entity/mapper in `call-common`, and let `call-ingestion` own the migration, listener wiring, and persistence service. Both auto-DLQ listeners share one persistence service and one task table with an idempotent key.

**Tech Stack:** Java 21, Spring Boot 3.2, RocketMQ Spring Boot starter, MyBatis-Plus, Flyway, JUnit 5, Mockito

---

### Task 1: Add failing property-binding test for DLQ consumers

**Files:**
- Modify: `call-ingestion/src/test/java/com/callcenter/ingestion/config/RocketMqPropertiesTest.java`

**Step 1: Write the failing test**

Add assertions for `record-dlq-consumer` and `round-dlq-consumer` group/thread/reconsume settings.

**Step 2: Run test to verify it fails**

Run: `mvn -pl call-ingestion -Dtest=RocketMqPropertiesTest test`

Expected: FAIL because `RocketMqProperties` does not expose the new consumers.

**Step 3: Write minimal implementation**

Extend `RocketMqProperties.Consumers` and `application.yml` with DLQ consumer settings.

**Step 4: Run test to verify it passes**

Run: `mvn -pl call-ingestion -Dtest=RocketMqPropertiesTest test`

Expected: PASS

### Task 2: Add failing service tests for dead-letter task persistence

**Files:**
- Create: `call-ingestion/src/test/java/com/callcenter/ingestion/service/DeadLetterTaskServiceTest.java`

**Step 1: Write the failing test**

Cover:

- persisting a record DLQ ingest payload as a `NEW` task
- persisting a round DLQ ingest payload as a `NEW` task
- treating duplicate insert as success

**Step 2: Run test to verify it fails**

Run: `mvn -pl call-ingestion -Dtest=DeadLetterTaskServiceTest test`

Expected: FAIL because the service and mapper/entity types do not exist.

**Step 3: Write minimal implementation**

Add the new entity/mapper plus a service that maps `MessageExt + original raw payload` to the task row and performs idempotent insert. Main-write DLQ rows should preserve raw `CallRecordMessage` / `CallRoundMessage` JSON; domain-event DLQ rows can keep `DomainEventMessage`.

**Step 4: Run test to verify it passes**

Run: `mvn -pl call-ingestion -Dtest=DeadLetterTaskServiceTest test`

Expected: PASS

### Task 3: Add failing consumer tests for generic DLQ listeners

**Files:**
- Create: `call-ingestion/src/test/java/com/callcenter/ingestion/consumer/RocketMqDeadLetterConsumerTest.java`

**Step 1: Write the failing test**

Cover:

- record DLQ listener delegates to the service with `MessageType.RECORD`
- round DLQ listener delegates to the service with `MessageType.ROUND`
- malformed JSON throws failure for RocketMQ reconsume

**Step 2: Run test to verify it fails**

Run: `mvn -pl call-ingestion -Dtest=RocketMqDeadLetterConsumerTest test`

Expected: FAIL because the listeners do not exist.

**Step 3: Write minimal implementation**

Add a shared abstract/base parsing flow or a small shared helper and two concrete listeners.

**Step 4: Run test to verify it passes**

Run: `mvn -pl call-ingestion -Dtest=RocketMqDeadLetterConsumerTest test`

Expected: PASS

### Task 4: Add schema migration for dead-letter tasks

**Files:**
- Create: `call-ingestion/src/main/resources/db/migration/V3__create_call_dead_letter_task.sql`

**Step 1: Write the migration**

Create the non-sharded table and unique/index keys needed for idempotent insert and later replay scans.

**Step 2: Verify migration shape**

Run: `sed -n '1,220p' call-ingestion/src/main/resources/db/migration/V3__create_call_dead_letter_task.sql`

Expected: table contains task key uniqueness and replay-relevant columns.

### Task 5: Run focused verification

**Files:**
- Verify all files from Tasks 1-4

**Step 1: Run focused tests**

Run: `mvn -pl call-ingestion -Dtest=RocketMqPropertiesTest,DeadLetterTaskServiceTest,RocketMqDeadLetterConsumerTest test`

Expected: PASS

**Step 2: Run wider module verification if needed**

Run: `mvn -pl call-ingestion test`

Expected: PASS or existing unrelated failures only.
