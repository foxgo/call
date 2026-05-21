# Outbox 短生命周期发送队列 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 把 `call_event_outbox` 从长期留存的状态表改成短生命周期发送队列，成功发布后立即删除，并通过 `PROCESSING` claim 机制支持多实例安全并发。

**Architecture:** 业务事务仍然只负责插入 `NEW` 状态 outbox 记录；publisher 改成先 claim 再发布，claim 成功后将记录置为 `PROCESSING`。单条消息发布成功后直接删除，发布失败则回写 `FAILED` 并设置退避时间；对卡住过久的 `PROCESSING` 记录增加回收逻辑，保证能够重新重试。

**Tech Stack:** Java 21, Spring Boot 3.2, MyBatis-Plus, MySQL 8, JUnit 5, Mockito

---

### Task 1: 扩展 outbox 状态模型

**Files:**
- Modify: `call-ingestion/src/main/java/com/callcenter/ingestion/outbox/OutboxStatus.java`
- Modify: `call-ingestion/src/test/java/com/callcenter/ingestion/outbox/OutboxPublisherTest.java`

**Step 1: Write the failing test**

补一个最小测试，明确 publisher 逻辑不再依赖 `PUBLISHED`，而是使用 `PROCESSING` 作为中间态。

**Step 2: Run test to verify it fails**

Run: `mvn -pl call-ingestion -Dtest=OutboxPublisherTest test`
Expected: FAIL，因为当前状态模型没有 `PROCESSING`

**Step 3: Write minimal implementation**

给 `OutboxStatus` 增加 `PROCESSING`，并移除后续实现对 `PUBLISHED` 的依赖前提。

**Step 4: Run test to verify it passes**

Run: `mvn -pl call-ingestion -Dtest=OutboxPublisherTest test`
Expected: PASS

**Step 5: Commit**

```bash
git add call-ingestion/src/main/java/com/callcenter/ingestion/outbox/OutboxStatus.java \
  call-ingestion/src/test/java/com/callcenter/ingestion/outbox/OutboxPublisherTest.java
git commit -m "refactor: add processing outbox status"
```

### Task 2: 调整 outbox schema 和索引

**Files:**
- Modify: `call-ingestion/src/main/resources/db/migration/V1__create_call_event_outbox.sql`
- Modify: `call-ingestion/src/test/java/com/callcenter/ingestion/service/SchemaSmokeTest.java`

**Step 1: Write the failing test**

补 schema 断言，要求存在新的 publishable 复合索引；如果决定新增处理中断恢复索引，也在这里一起断言。

**Step 2: Run test to verify it fails**

Run: `mvn -pl call-ingestion -Dtest=SchemaSmokeTest test`
Expected: FAIL，因为当前索引仍是旧的 `(status, next_attempt_at)`

**Step 3: Write minimal implementation**

把 outbox 索引调整为：

```sql
KEY idx_call_event_outbox_publishable (status, next_attempt_at, created_at, id)
```

如实现需要，再增加：

```sql
KEY idx_call_event_outbox_processing (status, updated_at, id)
```

**Step 4: Run test to verify it passes**

Run: `mvn -pl call-ingestion -Dtest=SchemaSmokeTest test`
Expected: PASS

**Step 5: Commit**

```bash
git add call-ingestion/src/main/resources/db/migration/V1__create_call_event_outbox.sql \
  call-ingestion/src/test/java/com/callcenter/ingestion/service/SchemaSmokeTest.java
git commit -m "refactor: optimize outbox indexes for claim flow"
```

### Task 3: 用 claim 机制替换直接扫表

**Files:**
- Modify: `call-ingestion/src/main/java/com/callcenter/ingestion/outbox/OutboxRepository.java`
- Modify: `call-ingestion/src/test/java/com/callcenter/ingestion/outbox/OutboxPublisherTest.java`

**Step 1: Write the failing test**

补 repository / publisher 级别测试，覆盖：

- 只 claim `NEW` 和到期 `FAILED`
- claim 后记录状态变成 `PROCESSING`
- 同一条记录不会被重复 claim

**Step 2: Run test to verify it fails**

Run: `mvn -pl call-ingestion -Dtest=OutboxPublisherTest test`
Expected: FAIL，因为当前还是 `findPublishableBatch()`

**Step 3: Write minimal implementation**

把 repository 改成以 `claimPublishableBatch()` 为核心：

- 查询候选 `id`
- 原子更新为 `PROCESSING`
- 回表读出已 claim 记录

**Step 4: Run test to verify it passes**

Run: `mvn -pl call-ingestion -Dtest=OutboxPublisherTest test`
Expected: PASS

**Step 5: Commit**

```bash
git add call-ingestion/src/main/java/com/callcenter/ingestion/outbox/OutboxRepository.java \
  call-ingestion/src/test/java/com/callcenter/ingestion/outbox/OutboxPublisherTest.java
git commit -m "refactor: claim outbox rows before publishing"
```

### Task 4: 发布成功后立即删除

**Files:**
- Modify: `call-ingestion/src/main/java/com/callcenter/ingestion/outbox/OutboxPublisher.java`
- Modify: `call-ingestion/src/main/java/com/callcenter/ingestion/outbox/OutboxRepository.java`
- Modify: `call-ingestion/src/test/java/com/callcenter/ingestion/outbox/OutboxPublisherTest.java`

**Step 1: Write the failing test**

增加测试断言：

- MQ 发布成功时调用 `deleteById(id)`
- 不再调用 `markPublished()`

**Step 2: Run test to verify it fails**

Run: `mvn -pl call-ingestion -Dtest=OutboxPublisherTest test`
Expected: FAIL，因为当前成功路径还是写 `PUBLISHED`

**Step 3: Write minimal implementation**

在 publisher 中把成功路径改为直接删除，并删除不再需要的 `markPublished()`。

**Step 4: Run test to verify it passes**

Run: `mvn -pl call-ingestion -Dtest=OutboxPublisherTest test`
Expected: PASS

**Step 5: Commit**

```bash
git add call-ingestion/src/main/java/com/callcenter/ingestion/outbox/OutboxPublisher.java \
  call-ingestion/src/main/java/com/callcenter/ingestion/outbox/OutboxRepository.java \
  call-ingestion/src/test/java/com/callcenter/ingestion/outbox/OutboxPublisherTest.java
git commit -m "refactor: delete outbox rows after successful publish"
```

### Task 5: 保留失败重试语义

**Files:**
- Modify: `call-ingestion/src/main/java/com/callcenter/ingestion/outbox/OutboxPublisher.java`
- Modify: `call-ingestion/src/main/java/com/callcenter/ingestion/outbox/OutboxRepository.java`
- Modify: `call-ingestion/src/test/java/com/callcenter/ingestion/outbox/OutboxPublisherTest.java`

**Step 1: Write the failing test**

覆盖：

- publish 失败后改回 `FAILED`
- `attempt_count` 自增
- `next_attempt_at` 使用配置的 backoff
- `last_error` 保留根因消息

**Step 2: Run test to verify it fails**

Run: `mvn -pl call-ingestion -Dtest=OutboxPublisherTest test`
Expected: FAIL，如果 claim / 删除改造破坏了失败路径

**Step 3: Write minimal implementation**

确保失败路径仍然只回写失败元数据，不会误删记录，也不会残留在 `PROCESSING`。

**Step 4: Run test to verify it passes**

Run: `mvn -pl call-ingestion -Dtest=OutboxPublisherTest test`
Expected: PASS

**Step 5: Commit**

```bash
git add call-ingestion/src/main/java/com/callcenter/ingestion/outbox/OutboxPublisher.java \
  call-ingestion/src/main/java/com/callcenter/ingestion/outbox/OutboxRepository.java \
  call-ingestion/src/test/java/com/callcenter/ingestion/outbox/OutboxPublisherTest.java
git commit -m "test: preserve outbox retry semantics"
```

### Task 6: 回收卡住的 `PROCESSING` 记录

**Files:**
- Modify: `call-ingestion/src/main/java/com/callcenter/ingestion/outbox/OutboxPublisherProperties.java`
- Modify: `call-ingestion/src/main/resources/application.yml`
- Modify: `call-ingestion/src/main/java/com/callcenter/ingestion/outbox/OutboxRepository.java`
- Modify: `call-ingestion/src/main/java/com/callcenter/ingestion/outbox/OutboxPublisher.java`
- Modify: `call-ingestion/src/test/java/com/callcenter/ingestion/config/PostprocessPropertiesTest.java`
- Modify: `call-ingestion/src/test/java/com/callcenter/ingestion/outbox/OutboxPublisherTest.java`

**Step 1: Write the failing test**

覆盖：

- 新增 `processing-timeout`
- 超过超时时间的 `PROCESSING` 记录会被重置为可重试状态
- 未超时的 `PROCESSING` 不会被错误回收

**Step 2: Run test to verify it fails**

Run: `mvn -pl call-ingestion -Dtest=PostprocessPropertiesTest,OutboxPublisherTest test`
Expected: FAIL，因为当前没有处理中断恢复配置和逻辑

**Step 3: Write minimal implementation**

增加 `processing-timeout` 配置，并在每轮 claim 前回收超时的 `PROCESSING` 记录。

**Step 4: Run test to verify it passes**

Run: `mvn -pl call-ingestion -Dtest=PostprocessPropertiesTest,OutboxPublisherTest test`
Expected: PASS

**Step 5: Commit**

```bash
git add call-ingestion/src/main/java/com/callcenter/ingestion/outbox/OutboxPublisherProperties.java \
  call-ingestion/src/main/resources/application.yml \
  call-ingestion/src/main/java/com/callcenter/ingestion/outbox/OutboxRepository.java \
  call-ingestion/src/main/java/com/callcenter/ingestion/outbox/OutboxPublisher.java \
  call-ingestion/src/test/java/com/callcenter/ingestion/config/PostprocessPropertiesTest.java \
  call-ingestion/src/test/java/com/callcenter/ingestion/outbox/OutboxPublisherTest.java
git commit -m "feat: recover stale processing outbox rows"
```

### Task 7: 跑最小验证集并确认没有旧语义残留

**Files:**
- Modify: `call-ingestion/src/test/java/com/callcenter/ingestion/outbox/OutboxPublisherTest.java` 按需要
- Modify: `call-ingestion/src/test/java/com/callcenter/ingestion/service/SchemaSmokeTest.java` 按需要

**Step 1: Run focused tests**

Run: `mvn -pl call-ingestion -Dtest=OutboxPublisherTest,SchemaSmokeTest,PostprocessPropertiesTest test`
Expected: PASS

**Step 2: Search for old semantics**

Run: `rg -n "PUBLISHED|markPublished|findPublishableBatch" /Users/johnny/github/call`
Expected: 不再有旧 outbox 发布成功状态流转的生产代码残留

**Step 3: Fix anything still failing**

只做收尾修正，不扩展范围。

**Step 4: Re-run focused tests**

Run: `mvn -pl call-ingestion -Dtest=OutboxPublisherTest,SchemaSmokeTest,PostprocessPropertiesTest test`
Expected: PASS

**Step 5: Commit**

```bash
git add call-ingestion/src/main/java/com/callcenter/ingestion/outbox \
  call-ingestion/src/main/resources/application.yml \
  call-ingestion/src/test/java/com/callcenter/ingestion/outbox/OutboxPublisherTest.java \
  call-ingestion/src/test/java/com/callcenter/ingestion/service/SchemaSmokeTest.java \
  call-ingestion/src/test/java/com/callcenter/ingestion/config/PostprocessPropertiesTest.java
git commit -m "refactor: convert outbox to short-lived publish queue"
```
