# Ingest / Domain Event Split Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Split RocketMQ ingest payloads from internal domain events so the main write path consumes strong typed ingest DTOs while outbox and postprocess consumers continue to use `DomainEventMessage`.

**Architecture:** `record-ingest` and `round-ingest` consumers will deserialize `CallRecordMessage` and `CallRoundMessage` directly instead of unwrapping `DomainEventMessage`. The outbox boundary remains the point where `DomainEventMessage` is created, and dead-letter persistence will branch by `MessageType` so ingest DLQ payloads keep their real protocol while postprocess DLQ payloads keep domain-event semantics.

**Tech Stack:** Java 21, Spring Boot 3.2, RocketMQ Spring Boot starter, Jackson, MyBatis-Plus, JUnit 5, Mockito

---

### Task 1: Lock in ingest consumer behavior with failing tests

**Files:**
- Modify: `call-ingestion/src/test/java/com/callcenter/ingestion/consumer/RocketMqCallRecordConsumerTest.java`
- Modify: `call-ingestion/src/test/java/com/callcenter/ingestion/consumer/RocketMqCallRoundConsumerTest.java`

**Step 1: Write the failing tests**

- Replace `DomainEventMessage` fixtures with raw `CallRecordMessage` JSON in `RocketMqCallRecordConsumerTest`.
- Replace `DomainEventMessage` fixtures with raw `CallRoundMessage` JSON in `RocketMqCallRoundConsumerTest`.
- Add assertions that the delegated `InboundMessage` still carries `MessageType.RECORD` / `MessageType.ROUND`.
- Add a regression assertion that the consumer can read `tenantId` from the business DTO itself instead of an envelope.

**Step 2: Run tests to verify they fail**

Run: `mvn -pl call-ingestion -Dtest=RocketMqCallRecordConsumerTest,RocketMqCallRoundConsumerTest test`

Expected: FAIL because the production consumers still deserialize `DomainEventMessage`.

### Task 2: Update ingest consumers to use raw DTO payloads

**Files:**
- Modify: `call-ingestion/src/main/java/com/callcenter/ingestion/consumer/RocketMqCallRecordConsumer.java`
- Modify: `call-ingestion/src/main/java/com/callcenter/ingestion/consumer/RocketMqCallRoundConsumer.java`

**Step 1: Write minimal implementation**

- In `RocketMqCallRecordConsumer`, deserialize the RocketMQ body directly to `CallRecordMessage`.
- Build `InboundMessage<CallRecordMessage>` using `message.tenantId()` for the tenant field.
- Remove the `DomainEventMessage` dependency and the `"CALL_RECORD"` envelope check.
- Apply the symmetrical change in `RocketMqCallRoundConsumer`, including removal of the `"CALL_ROUND"` check.

**Step 2: Run tests to verify they pass**

Run: `mvn -pl call-ingestion -Dtest=RocketMqCallRecordConsumerTest,RocketMqCallRoundConsumerTest test`

Expected: PASS.

**Step 3: Commit**

```bash
git add call-ingestion/src/main/java/com/callcenter/ingestion/consumer/RocketMqCallRecordConsumer.java \
  call-ingestion/src/main/java/com/callcenter/ingestion/consumer/RocketMqCallRoundConsumer.java \
  call-ingestion/src/test/java/com/callcenter/ingestion/consumer/RocketMqCallRecordConsumerTest.java \
  call-ingestion/src/test/java/com/callcenter/ingestion/consumer/RocketMqCallRoundConsumerTest.java
git commit -m "refactor: consume raw ingest payloads"
```

### Task 3: Lock in DLQ parsing semantics with failing tests

**Files:**
- Modify: `call-ingestion/src/test/java/com/callcenter/ingestion/service/DeadLetterTaskServiceTest.java`
- Modify: `call-ingestion/src/test/java/com/callcenter/ingestion/consumer/RocketMqDeadLetterConsumerTest.java`

**Step 1: Write the failing tests**

- Change `RECORD` dead-letter fixtures to raw `CallRecordMessage` JSON.
- Change `ROUND` dead-letter fixtures to raw `CallRoundMessage` JSON.
- Assert `payload_type` becomes `RECORD_INGEST` / `ROUND_INGEST`.
- Assert `idempotency_key` is still derived from `MessageKeys.recordIdempotencyKey(...)` / `MessageKeys.roundIdempotencyKey(...)`.
- Assert `message_key` for ingest DLQ rows uses the recovered business idempotency key instead of `DomainEventMessage.eventId`.
- Keep or add a postprocess/domain-event case proving domain-event DLQ behavior is unchanged.

**Step 2: Run tests to verify they fail**

Run: `mvn -pl call-ingestion -Dtest=DeadLetterTaskServiceTest,RocketMqDeadLetterConsumerTest test`

Expected: FAIL because `DeadLetterTaskService` still assumes ingest DLQ payloads are `DomainEventMessage`.

### Task 4: Split DLQ parsing between ingest messages and domain events

**Files:**
- Modify: `call-ingestion/src/main/java/com/callcenter/ingestion/service/DeadLetterTaskService.java`
- Modify: `call-ingestion/src/main/java/com/callcenter/ingestion/model/MessageType.java`
- Modify: `call-ingestion/src/main/java/com/callcenter/ingestion/processor/MessageKeys.java`

**Step 1: Write minimal implementation**

- Refactor `DeadLetterTaskService` so `persist(...)` branches by `MessageType`.
- For `RECORD`, deserialize the raw payload to `CallRecordMessage`, compute `idempotency_key` and set `payload_type=RECORD_INGEST`.
- For `ROUND`, deserialize the raw payload to `CallRoundMessage`, compute `idempotency_key` and set `payload_type=ROUND_INGEST`.
- Set ingest `message_key` from the recovered business idempotency key.
- Preserve existing `DomainEventMessage` parsing for postprocess/domain-event-oriented message types.
- If needed, add a small helper in `MessageKeys` for any shared key normalization used by ingest DLQ rows.

**Step 2: Run tests to verify they pass**

Run: `mvn -pl call-ingestion -Dtest=DeadLetterTaskServiceTest,RocketMqDeadLetterConsumerTest test`

Expected: PASS.

**Step 3: Commit**

```bash
git add call-ingestion/src/main/java/com/callcenter/ingestion/service/DeadLetterTaskService.java \
  call-ingestion/src/main/java/com/callcenter/ingestion/model/MessageType.java \
  call-ingestion/src/main/java/com/callcenter/ingestion/processor/MessageKeys.java \
  call-ingestion/src/test/java/com/callcenter/ingestion/service/DeadLetterTaskServiceTest.java \
  call-ingestion/src/test/java/com/callcenter/ingestion/consumer/RocketMqDeadLetterConsumerTest.java
git commit -m "refactor: separate ingest and domain-event dlq parsing"
```

### Task 5: Confirm outbox and persisted-event boundaries stay on `DomainEventMessage`

**Files:**
- Modify: `call-ingestion/src/test/java/com/callcenter/ingestion/service/OutboxEventFactoryTest.java`
- Modify: `call-ingestion/src/test/java/com/callcenter/ingestion/consumer/PersistedIndexConsumerTest.java`
- Modify: `call-ingestion/src/test/java/com/callcenter/ingestion/service/DeadLetterTaskServiceTest.java`

**Step 1: Extend tests**

- Add or keep assertions that `OutboxEventFactory` still serializes `DomainEventMessage`.
- Add or keep assertions that `RocketMqPersistedEventConsumer` still routes by `eventType`.
- Add a DLQ regression proving postprocess/domain-event message types still use `DomainEventMessage.eventId` semantics where applicable.

**Step 2: Run tests to verify they pass**

Run: `mvn -pl call-ingestion -Dtest=OutboxEventFactoryTest,PersistedIndexConsumerTest,DeadLetterTaskServiceTest test`

Expected: PASS.

### Task 6: Update design docs and implementation notes

**Files:**
- Modify: `docs/plans/2026-05-20-rocketmq-refactor-design.md`
- Modify: `docs/plans/2026-05-20-rocketmq-refactor.md`
- Modify: `docs/plans/2026-05-22-dead-letter-task-design.md`
- Modify: `docs/plans/2026-05-22-dead-letter-task.md`

**Step 1: Update docs**

- Replace any statement that ingest topics consume `DomainEventMessage` with the new raw DTO contract.
- Clarify that `DomainEventMessage` starts at the outbox boundary.
- Update dead-letter documentation so main-write DLQ rows store ingest payloads and ingest payload types.

**Step 2: Run a quick verification search**

Run: `rg -n "ingest.*DomainEventMessage|CALL_RECORD\\\"\\)|CALL_ROUND\\\"\\)" docs call-ingestion/src/main/java call-ingestion/src/test/java`

Expected: Only outbox or persisted-event usages remain; no main-ingest consumer should deserialize `DomainEventMessage`.

**Step 3: Commit**

```bash
git add docs/plans/2026-05-20-rocketmq-refactor-design.md \
  docs/plans/2026-05-20-rocketmq-refactor.md \
  docs/plans/2026-05-22-dead-letter-task-design.md \
  docs/plans/2026-05-22-dead-letter-task.md \
  call-ingestion/src/test/java/com/callcenter/ingestion/service/OutboxEventFactoryTest.java \
  call-ingestion/src/test/java/com/callcenter/ingestion/consumer/PersistedIndexConsumerTest.java \
  call-ingestion/src/test/java/com/callcenter/ingestion/service/DeadLetterTaskServiceTest.java
git commit -m "docs: clarify ingest and domain event boundaries"
```

### Task 7: Run focused verification before completion

**Files:**
- No file changes

**Step 1: Run the focused module test suite**

Run: `mvn -pl call-ingestion -Dtest=RocketMqCallRecordConsumerTest,RocketMqCallRoundConsumerTest,DeadLetterTaskServiceTest,RocketMqDeadLetterConsumerTest,OutboxEventFactoryTest,PersistedIndexConsumerTest test`

Expected: PASS.

**Step 2: Run a broader safety check**

Run: `mvn -pl call-ingestion test`

Expected: PASS.

**Step 3: Commit**

```bash
git add call-ingestion/src/main/java \
  call-ingestion/src/test/java \
  docs/plans/2026-05-20-rocketmq-refactor-design.md \
  docs/plans/2026-05-20-rocketmq-refactor.md \
  docs/plans/2026-05-22-dead-letter-task-design.md \
  docs/plans/2026-05-22-dead-letter-task.md
git commit -m "refactor: split ingest payloads from domain events"
```
