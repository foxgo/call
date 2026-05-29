# Call Task Redis Queue Simplification Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Simplify `call-task` scheduling by shrinking Redis queue responsibilities to a ready-window only model and moving retry and timeout recovery back to MySQL state transitions.

**Architecture:** Introduce a `READY` dial-unit state in MySQL so the database can distinguish "eligible but not preloaded" from "already preloaded into Redis". Keep Redis only as the hot `ready` queue used for batch preload and fast claim, and remove Redis-managed `processing`, `retry`, and timeout-index flows. Replace Redis retry and recovery schedulers with database-driven jobs that reactivate tasks after state transitions.

**Tech Stack:** Java 21, Spring Boot, Spring Scheduling, Spring Data Redis, MyBatis-Plus, JUnit 5, Mockito.

---

### Task 1: Add `READY` status and update the state model

**Files:**
- Modify: `call-common/src/main/java/com/callcenter/common/enums/CallDialUnitStatus.java`
- Modify: `call-task/src/main/java/com/callcenter/task/repository/CallDialUnitRepository.java`
- Test: `call-task/src/test/java/com/callcenter/task/service/DialResultWritebackServiceTest.java`
- Test: `call-task/src/test/java/com/callcenter/task/dispatch/PartitionSchedulerWorkerTest.java`

**Step 1: Write the failing tests**

Add assertions that retry and preload flows now move units through `READY` and `PENDING` instead of `QUEUED`.

```java
assertThat(CallDialUnitStatus.valueOf("READY")).isEqualTo(CallDialUnitStatus.READY);
verify(repository).markDialingFromReady(...);
verify(repository).markFailedForRetry(...); // writes DIALING -> PENDING
```

**Step 2: Run tests to verify they fail**

Run: `mvn -pl call-task -Dtest=DialResultWritebackServiceTest,PartitionSchedulerWorkerTest test`
Expected: FAIL because `READY` does not exist and repository method names and expectations still use `QUEUED`.

**Step 3: Write minimal implementation**

Update the enum and adjust repository method names and comments so the new state model is explicit.

```java
public enum CallDialUnitStatus {
    PENDING,
    READY,
    DIALING,
    SUCCESS,
    FAILED
}
```

**Step 4: Run tests to verify they pass**

Run: `mvn -pl call-task -Dtest=DialResultWritebackServiceTest,PartitionSchedulerWorkerTest test`
Expected: PASS after downstream repository and service references compile against `READY`.

**Step 5: Commit**

```bash
git add call-common/src/main/java/com/callcenter/common/enums/CallDialUnitStatus.java \
  call-task/src/main/java/com/callcenter/task/repository/CallDialUnitRepository.java \
  call-task/src/test/java/com/callcenter/task/service/DialResultWritebackServiceTest.java \
  call-task/src/test/java/com/callcenter/task/dispatch/PartitionSchedulerWorkerTest.java
git commit -m "task: add ready dial unit state"
```

### Task 2: Move preload and dispatch repository transitions to `PENDING -> READY -> DIALING`

**Files:**
- Modify: `call-task/src/main/java/com/callcenter/task/repository/CallDialUnitRepository.java`
- Test: `call-task/src/test/java/com/callcenter/task/dispatch/DialUnitPreloadServiceTest.java`
- Test: `call-task/src/test/java/com/callcenter/task/dispatch/PartitionSchedulerWorkerTest.java`

**Step 1: Write the failing tests**

Cover both repository-facing behaviors:
- preload claims only `PENDING` rows with `next_call_time <= now` and marks them `READY`
- dispatch marks only `READY` rows as `DIALING`

```java
verify(repository).claimPendingToReady(shardKey, 1001L, 50, now);
verify(repository).markDialingFromReady(shardKey, 1001L, List.of(11L, 12L), token, callTime, expireAt);
```

**Step 2: Run tests to verify they fail**

Run: `mvn -pl call-task -Dtest=DialUnitPreloadServiceTest,PartitionSchedulerWorkerTest test`
Expected: FAIL because the repository still exposes `claimPendingForQueue` and `markDialing` with `QUEUED` semantics.

**Step 3: Write minimal implementation**

Refactor repository methods:
- `claimPendingForQueue(...)` -> `claimPendingToReady(...)`
- `markDialing(...)` -> `markDialingFromReady(...)`
- update SQL predicates from `QUEUED` to `READY`
- require `next_call_time <= now` for preload claims

Prefer eliminating the current `select + per-row update` pattern while here if the surrounding mapper code supports it.

```java
update.eq("task_id", taskId)
    .eq("id", unitId)
    .eq("status", CallDialUnitStatus.READY.name())
    .set("status", CallDialUnitStatus.DIALING.name());
```

**Step 4: Run tests to verify they pass**

Run: `mvn -pl call-task -Dtest=DialUnitPreloadServiceTest,PartitionSchedulerWorkerTest test`
Expected: PASS with preload and dispatch both centered on `READY`.

**Step 5: Commit**

```bash
git add call-task/src/main/java/com/callcenter/task/repository/CallDialUnitRepository.java \
  call-task/src/test/java/com/callcenter/task/dispatch/DialUnitPreloadServiceTest.java \
  call-task/src/test/java/com/callcenter/task/dispatch/PartitionSchedulerWorkerTest.java
git commit -m "task: move preload and dispatch through ready state"
```

### Task 3: Shrink `RedisDialUnitQueue` to ready-window operations only

**Files:**
- Modify: `call-task/src/main/java/com/callcenter/task/dispatch/RedisDialUnitQueue.java`
- Modify: `call-task/src/main/java/com/callcenter/task/dispatch/RedisQueueKeys.java`
- Modify: `call-task/src/main/java/com/callcenter/task/dispatch/RedisQueueScriptRepository.java`
- Test: `call-task/src/test/java/com/callcenter/task/dispatch/RedisDialUnitQueueTest.java`

**Step 1: Write the failing tests**

Replace queue tests that assert retry or processing behavior with tests that only verify:
- `offerReady` stores units ordered by score
- `claimReady` pops a batch atomically from `ready`
- `windowSize` reflects the size of the ready window

```java
assertThat(queue.claimReady(9L, 1001L, 1, 2, expireAt)).containsExactly(11L, 12L);
assertThat(queue.windowSize(1001L, 1)).isEqualTo(3L);
```

**Step 2: Run tests to verify they fail**

Run: `mvn -pl call-task -Dtest=RedisDialUnitQueueTest test`
Expected: FAIL because existing tests and production code still expect `processing`, `retry`, and timeout keys.

**Step 3: Write minimal implementation**

Delete queue APIs and scripts that are no longer part of the simplified model:
- remove `ackProcessing`
- remove retry and timeout methods
- collapse key definitions to `ready`
- keep one Lua script for atomic ready batch claim

```java
public List<Long> claimReady(Long tenantId, Long taskId, int shard, int batchSize, Instant ignoredExpireAt) {
    // Pop from ready only. ExpireAt can remain in the signature temporarily if that reduces churn.
}
```

**Step 4: Run tests to verify they pass**

Run: `mvn -pl call-task -Dtest=RedisDialUnitQueueTest test`
Expected: PASS with the queue reduced to ready-window responsibilities.

**Step 5: Commit**

```bash
git add call-task/src/main/java/com/callcenter/task/dispatch/RedisDialUnitQueue.java \
  call-task/src/main/java/com/callcenter/task/dispatch/RedisQueueKeys.java \
  call-task/src/main/java/com/callcenter/task/dispatch/RedisQueueScriptRepository.java \
  call-task/src/test/java/com/callcenter/task/dispatch/RedisDialUnitQueueTest.java
git commit -m "task: simplify redis dial unit queue"
```

### Task 4: Rework preload and dispatch services around the simplified queue

**Files:**
- Modify: `call-task/src/main/java/com/callcenter/task/dispatch/DialUnitPreloadService.java`
- Modify: `call-task/src/main/java/com/callcenter/task/dispatch/PartitionSchedulerWorker.java`
- Test: `call-task/src/test/java/com/callcenter/task/dispatch/DialUnitPreloadServiceTest.java`
- Test: `call-task/src/test/java/com/callcenter/task/dispatch/PartitionSchedulerWorkerTest.java`

**Step 1: Write the failing tests**

Add coverage for:
- preload calling `claimPendingToReady(...)`
- `runPartition(...)` re-offering IDs to Redis if DB fails to move some of them from `READY` to `DIALING`

```java
verify(queue).offerReady(1001L, 1, List.of(unit(12L), unit(13L)));
verify(activeTaskQueue).block(1001L, TaskBlockReason.EMPTY);
```

**Step 2: Run tests to verify they fail**

Run: `mvn -pl call-task -Dtest=DialUnitPreloadServiceTest,PartitionSchedulerWorkerTest test`
Expected: FAIL because worker logic still assumes Redis `processing` and concurrency release semantics tied to `claimReady`/`markDialing`.

**Step 3: Write minimal implementation**

Update services so they treat Redis as a fast ready queue only:
- preload `PENDING -> READY` rows into Redis
- claim IDs from Redis ready
- mark successful IDs `READY -> DIALING`
- re-offer unmatched IDs back into Redis ready
- keep active-task reactivation logic, but remove Redis processing cleanup assumptions

```java
List<CallDialUnitEntity> units = callDialUnitRepository.markDialingFromReady(...);
List<CallDialUnitEntity> missed = diff(ids, units);
if (!missed.isEmpty()) {
    redisDialUnitQueue.offerReady(task.getId(), shardKey.tableIndex(), missed);
}
```

**Step 4: Run tests to verify they pass**

Run: `mvn -pl call-task -Dtest=DialUnitPreloadServiceTest,PartitionSchedulerWorkerTest test`
Expected: PASS with worker behavior aligned to the simplified queue.

**Step 5: Commit**

```bash
git add call-task/src/main/java/com/callcenter/task/dispatch/DialUnitPreloadService.java \
  call-task/src/main/java/com/callcenter/task/dispatch/PartitionSchedulerWorker.java \
  call-task/src/test/java/com/callcenter/task/dispatch/DialUnitPreloadServiceTest.java \
  call-task/src/test/java/com/callcenter/task/dispatch/PartitionSchedulerWorkerTest.java
git commit -m "task: align preload and dispatch with ready queue"
```

### Task 5: Remove Redis-based writeback, retry, and timeout recovery flows

**Files:**
- Modify: `call-task/src/main/java/com/callcenter/task/service/DialResultWritebackService.java`
- Delete: `call-task/src/main/java/com/callcenter/task/dispatch/RetryQueueScheduler.java`
- Delete: `call-task/src/main/java/com/callcenter/task/dispatch/ProcessingTimeoutRecoveryJob.java`
- Test: `call-task/src/test/java/com/callcenter/task/service/DialResultWritebackServiceTest.java`
- Delete or replace: `call-task/src/test/java/com/callcenter/task/dispatch/RetryQueueSchedulerTest.java`
- Delete or replace: `call-task/src/test/java/com/callcenter/task/dispatch/ProcessingTimeoutRecoveryJobTest.java`

**Step 1: Write the failing tests**

Update writeback tests to assert:
- success path only updates DB, releases concurrency, and activates the task
- retry path writes `DIALING -> PENDING` and does not touch Redis retry or processing structures

```java
verify(queue, never()).ackProcessing(any(), any(), anyInt(), anyLong());
verify(queue, never()).scheduleRetry(any(), any(), anyInt(), anyLong(), any());
verify(concurrencyLimiter).release(9L, 1001L);
```

**Step 2: Run tests to verify they fail**

Run: `mvn -pl call-task -Dtest=DialResultWritebackServiceTest,RetryQueueSchedulerTest,ProcessingTimeoutRecoveryJobTest test`
Expected: FAIL because the service and tests still reference Redis processing and retry APIs.

**Step 3: Write minimal implementation**

Strip Redis queue operations out of writeback and remove scheduler classes that only exist to service Redis retry and timeout indexes.

```java
if (updated) {
    concurrencyLimiter.release(tenantId, request.getTaskId());
    taskActivationService.activate(tenantId, request.getTaskId());
}
```

**Step 4: Run tests to verify they pass**

Run: `mvn -pl call-task -Dtest=DialResultWritebackServiceTest test`
Expected: PASS after obsolete retry and timeout tests are removed or replaced by DB recovery job tests.

**Step 5: Commit**

```bash
git add call-task/src/main/java/com/callcenter/task/service/DialResultWritebackService.java \
  call-task/src/test/java/com/callcenter/task/service/DialResultWritebackServiceTest.java
git rm call-task/src/main/java/com/callcenter/task/dispatch/RetryQueueScheduler.java \
  call-task/src/main/java/com/callcenter/task/dispatch/ProcessingTimeoutRecoveryJob.java \
  call-task/src/test/java/com/callcenter/task/dispatch/RetryQueueSchedulerTest.java \
  call-task/src/test/java/com/callcenter/task/dispatch/ProcessingTimeoutRecoveryJobTest.java
git commit -m "task: remove redis retry and recovery flows"
```

### Task 6: Add database-driven recovery jobs for pending and expired dialing units

**Files:**
- Create: `call-task/src/main/java/com/callcenter/task/dispatch/PendingDialUnitActivationJob.java`
- Create: `call-task/src/main/java/com/callcenter/task/dispatch/DialingRecoveryJob.java`
- Modify: `call-task/src/main/java/com/callcenter/task/repository/CallDialUnitRepository.java`
- Test: `call-task/src/test/java/com/callcenter/task/dispatch/PendingDialUnitActivationJobTest.java`
- Test: `call-task/src/test/java/com/callcenter/task/dispatch/DialingRecoveryJobTest.java`

**Step 1: Write the failing tests**

Cover:
- `PendingDialUnitActivationJob` finds due `PENDING` work and re-activates the owning task
- `DialingRecoveryJob` converts expired `DIALING` records back to `PENDING` or `FAILED`, releases concurrency, and re-activates tasks

```java
verify(taskActivationService).activate(9L, 1001L);
verify(concurrencyLimiter).releaseBatch(9L, 1001L, 2);
```

**Step 2: Run tests to verify they fail**

Run: `mvn -pl call-task -Dtest=PendingDialUnitActivationJobTest,DialingRecoveryJobTest test`
Expected: FAIL because the new jobs and repository query methods do not exist.

**Step 3: Write minimal implementation**

Add repository helpers that support database-driven recovery:
- list or batch claim due `PENDING` task IDs
- recover expired `DIALING` rows by `inflight_expire_at`

Implement jobs with small batch sizes and task reactivation semantics consistent with the existing scheduler design.

```java
@Scheduled(fixedDelayString = "${call.task.dispatch.processing-recovery-interval:PT5S}")
public void recoverExpiredDialing() {
    // query expired DIALING rows, move to PENDING/FAILED, release quota, activate task
}
```

**Step 4: Run tests to verify they pass**

Run: `mvn -pl call-task -Dtest=PendingDialUnitActivationJobTest,DialingRecoveryJobTest test`
Expected: PASS with DB-driven recovery replacing Redis-driven retry and timeout indexes.

**Step 5: Commit**

```bash
git add call-task/src/main/java/com/callcenter/task/dispatch/PendingDialUnitActivationJob.java \
  call-task/src/main/java/com/callcenter/task/dispatch/DialingRecoveryJob.java \
  call-task/src/main/java/com/callcenter/task/repository/CallDialUnitRepository.java \
  call-task/src/test/java/com/callcenter/task/dispatch/PendingDialUnitActivationJobTest.java \
  call-task/src/test/java/com/callcenter/task/dispatch/DialingRecoveryJobTest.java
git commit -m "task: add db-driven dial unit recovery jobs"
```

### Task 7: Run module regression and clean up dead references

**Files:**
- Modify as needed: `call-task/src/main/java/...`
- Modify as needed: `call-task/src/test/java/...`
- Optional doc touch-up: `docs/call-task-number-scheduling-architecture.md`

**Step 1: Write the failing test or check**

Search for stale references to removed Redis queue APIs and `QUEUED` semantics.

```bash
rg -n "ackProcessing|scheduleRetry|retryDue|processingTimeout|QUEUED" call-task/src/main/java call-task/src/test/java
```

**Step 2: Run checks to verify failures or stale references exist**

Run: `mvn -pl call-task test`
Expected: FAIL or warnings until all renamed methods, deleted jobs, and state transitions are updated.

**Step 3: Write minimal implementation**

Remove any remaining dead references, align metrics and comments, and update the architecture doc only if it still documents Redis retry and processing-timeout flows as active behavior.

**Step 4: Run tests to verify they pass**

Run: `mvn -pl call-task test`
Expected: PASS

**Step 5: Commit**

```bash
git add call-task/src/main/java call-task/src/test/java docs/call-task-number-scheduling-architecture.md
git commit -m "task: finish redis queue simplification"
```
