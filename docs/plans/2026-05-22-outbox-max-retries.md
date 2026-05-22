# Outbox Max Retries Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Stop re-claiming failed outbox rows after they reach the configured maximum retry count while keeping them in `FAILED` status.

**Architecture:** Add a new `call.outbox.max-retries` property with a default of `10`, thread it through the publisher and repository claim flow, and gate retries at claim time so existing failed rows stay visible in storage but are no longer retried after the cap. Keep failure accounting unchanged except for using the new limit when selecting retryable rows.

**Tech Stack:** Java 21, Spring Boot configuration properties, MyBatis-Plus repository wrappers, JUnit 5, Mockito

---

### Task 1: Add failing tests for retry-cap behavior

**Files:**
- Modify: `call-ingestion/src/test/java/com/callcenter/ingestion/outbox/OutboxRepositoryTest.java`
- Modify: `call-ingestion/src/test/java/com/callcenter/ingestion/config/PostprocessPropertiesTest.java`

**Step 1: Write the failing tests**

Add a repository test asserting `claimPublishableBatch(...)` passes the configured max retries into mapper selection and excludes already-exhausted `FAILED` rows from the conditional update. Add a config binding test asserting `call.outbox.max-retries` binds and defaults to `10`.

**Step 2: Run tests to verify they fail**

Run: `mvn -pl call-ingestion -Dtest=OutboxRepositoryTest,PostprocessPropertiesTest test`

Expected: FAIL because `OutboxPublisherProperties` has no `maxRetries` property and repository claim code does not use it.

### Task 2: Implement minimal retry-cap support

**Files:**
- Modify: `call-ingestion/src/main/java/com/callcenter/ingestion/outbox/OutboxPublisherProperties.java`
- Modify: `call-common/src/main/java/com/callcenter/common/mapper/CallEventOutboxMapper.java`
- Modify: `call-ingestion/src/main/java/com/callcenter/ingestion/outbox/OutboxRepository.java`
- Modify: `call-ingestion/src/main/resources/application.yml`

**Step 1: Write minimal implementation**

Add `maxRetries` to `OutboxPublisherProperties` with default `10` and validation. Update the mapper query to only select `FAILED` rows when `next_attempt_at <= now` and `attempt_count < maxRetries`. Thread `maxRetries` through `OutboxRepository.claimPublishableBatch(...)` and preserve existing semantics for `NEW` rows.

**Step 2: Run tests to verify they pass**

Run: `mvn -pl call-ingestion -Dtest=OutboxRepositoryTest,PostprocessPropertiesTest test`

Expected: PASS.

### Task 3: Verify publisher-level retry accounting still works

**Files:**
- Modify: `call-ingestion/src/test/java/com/callcenter/ingestion/outbox/OutboxPublisherTest.java` if needed

**Step 1: Add or confirm a focused test**

Keep the existing publisher failure test proving failed publishes still increment `attemptCount` and set `nextAttemptAt`. Only add a test if the repository signature change requires it.

**Step 2: Run targeted outbox tests**

Run: `mvn -pl call-ingestion -Dtest=OutboxPublisherTest,OutboxRepositoryTest,PostprocessPropertiesTest test`

Expected: PASS.
