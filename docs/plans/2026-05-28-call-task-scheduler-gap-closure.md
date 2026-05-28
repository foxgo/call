# Call Task Scheduler Gap Closure Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Close the remaining scheduler control gaps in `call-task` by implementing real active-queue consumption, fair-score requeueing, symmetric concurrency acquire/release, and import-triggered reactivation.

**Architecture:** Keep the existing `MySQL + Redis + RocketMQ` scheduler architecture and make targeted changes in the scheduling control path. Extend task scheduling metadata and queue semantics first, then add batch concurrency acquire/rollback, then close the worker loop so each dispatch round ends in `reactivate / block / deactivate` based on real results.

**Tech Stack:** Java 21, Spring Boot, Spring Data Redis, MyBatis-Plus, RocketMQ, JUnit 5, Mockito

---

### Task 1: Extend Task Scheduling Metadata And Active Queue Semantics

**Files:**
- Modify: `call-task/src/main/java/com/callcenter/task/dispatch/TaskSchedulingMeta.java`
- Modify: `call-task/src/main/java/com/callcenter/task/dispatch/ActiveTaskQueue.java`
- Test: `call-task/src/test/java/com/callcenter/task/dispatch/ActiveTaskQueueTest.java`

**Step 1: Write the failing tests**

Add tests that prove:

- `pollNextTask()` removes the lowest-score task from the active ZSET
- `reactivate()` re-inserts the task with a new fair score
- `block()` updates `state` and `blockedReason`
- `loadMeta()` round-trips `fairScore`

Example assertions:

```java
assertEquals(Optional.of(1001L), queue.pollNextTask(3));
assertEquals(Optional.of(1002L), queue.pollNextTask(3));

queue.upsertMeta(1001L, 9L, 1, 16, 7, 0L);
queue.block(1001L, TaskBlockReason.CONCURRENCY_FULL);
assertEquals(TaskSchedulingState.BLOCKED, queue.loadMeta(1001L).orElseThrow().state());
```

**Step 2: Run tests to verify they fail**

Run:

```bash
mvn -pl call-task -Dtest=ActiveTaskQueueTest test
```

Expected:

- FAIL because `TaskSchedulingMeta` has no `fairScore`
- FAIL because `ActiveTaskQueue` has no `block` / `reactivate` behavior
- FAIL because `pollNextTask()` does not remove the task

**Step 3: Write minimal implementation**

Update `TaskSchedulingMeta` to include:

```java
long fairScore
```

Update `ActiveTaskQueue` to:

- persist `fairScore` in meta hash
- remove the chosen task from the active ZSET during `pollNextTask`
- add `block(Long taskId, TaskBlockReason reason)`
- add `reactivate(Long taskId, long fairScore)`
- optionally add `deactivate(Long taskId)` if needed by worker flow

**Step 4: Run tests to verify they pass**

Run:

```bash
mvn -pl call-task -Dtest=ActiveTaskQueueTest test
```

Expected:

- PASS

**Step 5: Commit**

```bash
git add \
  call-task/src/main/java/com/callcenter/task/dispatch/TaskSchedulingMeta.java \
  call-task/src/main/java/com/callcenter/task/dispatch/ActiveTaskQueue.java \
  call-task/src/test/java/com/callcenter/task/dispatch/ActiveTaskQueueTest.java
git commit -m "task: extend active task queue semantics"
```

### Task 2: Add Batch Concurrency Acquire And Rollback Semantics

**Files:**
- Modify: `call-task/src/main/java/com/callcenter/task/dispatch/DispatchConcurrencyLimiter.java`
- Test: `call-task/src/test/java/com/callcenter/task/dispatch/DispatchConcurrencyLimiterTest.java`

**Step 1: Write the failing tests**

Add tests that prove:

- `tryAcquireBatch(...)` returns full grant when all quotas allow it
- `tryAcquireBatch(...)` returns partial grant when one quota is tight
- `releaseBatch(...)` correctly rolls back multiple slots

Example assertions:

```java
assertEquals(3, limiter.tryAcquireBatch(9L, 1001L, 10, 3));
assertEquals(1, limiter.tryAcquireBatch(9L, 1001L, 5, 3));
```

**Step 2: Run tests to verify they fail**

Run:

```bash
mvn -pl call-task -Dtest=DispatchConcurrencyLimiterTest test
```

Expected:

- FAIL because batch acquire/release APIs do not exist

**Step 3: Write minimal implementation**

Add methods with semantics like:

```java
public int tryAcquireBatch(Long tenantId, Long taskId, int taskMaxConcurrency, int requested)
public void releaseBatch(Long tenantId, Long taskId, int count)
```

Implementation notes:

- reuse current global / tenant / task counters
- grant up to the smallest remaining quota
- keep `requested <= 0` safe by returning `0`
- implement `releaseBatch` as repeated decrement or a bounded loop

**Step 4: Run tests to verify they pass**

Run:

```bash
mvn -pl call-task -Dtest=DispatchConcurrencyLimiterTest test
```

Expected:

- PASS

**Step 5: Commit**

```bash
git add \
  call-task/src/main/java/com/callcenter/task/dispatch/DispatchConcurrencyLimiter.java \
  call-task/src/test/java/com/callcenter/task/dispatch/DispatchConcurrencyLimiterTest.java
git commit -m "task: add batch concurrency acquire"
```

### Task 3: Close The Worker Dispatch Loop

**Files:**
- Modify: `call-task/src/main/java/com/callcenter/task/dispatch/PartitionSchedulerWorker.java`
- Test: `call-task/src/test/java/com/callcenter/task/dispatch/PartitionSchedulerWorkerTest.java`

**Step 1: Write the failing tests**

Add tests that prove:

- successful dispatch updates `fairScore` and reactivates the task
- zero budget caused by no quota blocks with `CONCURRENCY_FULL`
- zero claim caused by empty ready queue blocks with `EMPTY`
- over-granted but under-claimed slots are rolled back
- claimed but not marked-dialing slots are rolled back

Example assertions:

```java
verify(activeTaskQueue).reactivate(1001L, expectedFairScore);
verify(activeTaskQueue).block(1001L, TaskBlockReason.CONCURRENCY_FULL);
verify(concurrencyLimiter).releaseBatch(9L, 1001L, 2);
```

**Step 2: Run tests to verify they fail**

Run:

```bash
mvn -pl call-task -Dtest=PartitionSchedulerWorkerTest test
```

Expected:

- FAIL because worker does not call batch acquire
- FAIL because worker does not requeue/block using active queue metadata
- FAIL because fair score is not updated

**Step 3: Write minimal implementation**

Update `PartitionSchedulerWorker` to:

- `poll` the task from active queue
- compute `requested`
- call `tryAcquireBatch(...)`
- use granted budget in `claimReady(...)`
- roll back `granted - claimed`
- roll back `claimed - markedDialing`
- compute:

```java
long nextFairScore = meta.fairScore() + (long) dispatched * 1000 / meta.weight();
```

- `reactivate(...)` when dispatch succeeded and more work may exist
- `block(..., CONCURRENCY_FULL)` when quota is exhausted
- `block(..., EMPTY)` when no ready units remain

**Step 4: Run tests to verify they pass**

Run:

```bash
mvn -pl call-task -Dtest=PartitionSchedulerWorkerTest test
```

Expected:

- PASS

**Step 5: Commit**

```bash
git add \
  call-task/src/main/java/com/callcenter/task/dispatch/PartitionSchedulerWorker.java \
  call-task/src/test/java/com/callcenter/task/dispatch/PartitionSchedulerWorkerTest.java
git commit -m "task: close scheduler worker loop"
```

### Task 4: Reactivate Running Tasks After Import

**Files:**
- Modify: `call-task/src/main/java/com/callcenter/task/service/CallTaskImportService.java`
- Test: `call-task/src/test/java/com/callcenter/task/service/CallTaskImportServiceTest.java`

**Step 1: Write the failing tests**

Add tests that prove:

- importing dial units into a `RUNNING` task triggers `taskActivationService.activate(...)`
- importing into a non-running task does not trigger activation

Example assertions:

```java
verify(taskActivationService).activate(9L, 1001L);
verifyNoInteractions(taskActivationService);
```

**Step 2: Run tests to verify they fail**

Run:

```bash
mvn -pl call-task -Dtest=CallTaskImportServiceTest test
```

Expected:

- FAIL because `CallTaskImportService` does not depend on `TaskActivationService`

**Step 3: Write minimal implementation**

Inject `TaskActivationService` into `CallTaskImportService` and add:

```java
if (CallTaskStatus.RUNNING.name().equals(task.getStatus())) {
    taskActivationService.activate(tenantId, taskId);
}
```

after import persistence succeeds.

**Step 4: Run tests to verify they pass**

Run:

```bash
mvn -pl call-task -Dtest=CallTaskImportServiceTest test
```

Expected:

- PASS

**Step 5: Commit**

```bash
git add \
  call-task/src/main/java/com/callcenter/task/service/CallTaskImportService.java \
  call-task/src/test/java/com/callcenter/task/service/CallTaskImportServiceTest.java
git commit -m "task: reactivate running tasks after import"
```

### Task 5: Tighten Writeback, Retry, And Recovery Release Semantics

**Files:**
- Modify: `call-task/src/main/java/com/callcenter/task/service/DialResultWritebackService.java`
- Modify: `call-task/src/main/java/com/callcenter/task/dispatch/RetryQueueScheduler.java`
- Modify: `call-task/src/main/java/com/callcenter/task/dispatch/ProcessingTimeoutRecoveryJob.java`
- Test: `call-task/src/test/java/com/callcenter/task/service/DialResultWritebackServiceTest.java`
- Test: `call-task/src/test/java/com/callcenter/task/dispatch/RetryQueueSchedulerTest.java`
- Test: `call-task/src/test/java/com/callcenter/task/dispatch/ProcessingTimeoutRecoveryJobTest.java`

**Step 1: Write the failing tests**

Add tests that prove:

- writeback only releases concurrency when DB status update succeeds
- retry scheduler only reactivates when retry item really moved back to ready
- timeout recovery only releases the number of slots actually recovered

Example assertions:

```java
verify(concurrencyLimiter, never()).release(anyLong(), anyLong());
verify(taskActivationService, never()).activate(anyLong(), anyLong());
```

**Step 2: Run tests to verify they fail**

Run:

```bash
mvn -pl call-task -Dtest=DialResultWritebackServiceTest,RetryQueueSchedulerTest,ProcessingTimeoutRecoveryJobTest test
```

Expected:

- FAIL if any code path releases/reactivates without confirmed state movement

**Step 3: Write minimal implementation**

Audit each code path and keep the rule:

- only release concurrency after confirmed DB transition or confirmed queue move
- only reactivate task after confirmed state progress

Prefer keeping existing behavior where already correct; change only inconsistent paths.

**Step 4: Run tests to verify they pass**

Run:

```bash
mvn -pl call-task -Dtest=DialResultWritebackServiceTest,RetryQueueSchedulerTest,ProcessingTimeoutRecoveryJobTest test
```

Expected:

- PASS

**Step 5: Commit**

```bash
git add \
  call-task/src/main/java/com/callcenter/task/service/DialResultWritebackService.java \
  call-task/src/main/java/com/callcenter/task/dispatch/RetryQueueScheduler.java \
  call-task/src/main/java/com/callcenter/task/dispatch/ProcessingTimeoutRecoveryJob.java \
  call-task/src/test/java/com/callcenter/task/service/DialResultWritebackServiceTest.java \
  call-task/src/test/java/com/callcenter/task/dispatch/RetryQueueSchedulerTest.java \
  call-task/src/test/java/com/callcenter/task/dispatch/ProcessingTimeoutRecoveryJobTest.java
git commit -m "task: tighten scheduler release semantics"
```

### Task 6: Run Focused Regression And Update Documentation

**Files:**
- Modify: `docs/call-task-number-scheduling-architecture.md`
- Modify: `docs/plans/2026-05-28-call-task-scheduler-gap-closure-design.md`

**Step 1: Write the failing check list**

Prepare a regression list covering:

- active queue consumption
- fair-score requeueing
- batch concurrency acquire/release
- import-triggered activation
- callback/retry/recovery release semantics

**Step 2: Run focused tests**

Run:

```bash
mvn -pl call-task -Dtest=ActiveTaskQueueTest,DispatchConcurrencyLimiterTest,PartitionSchedulerWorkerTest,CallTaskImportServiceTest,DialResultWritebackServiceTest,RetryQueueSchedulerTest,ProcessingTimeoutRecoveryJobTest test
```

Expected:

- PASS

**Step 3: Update docs to reflect completed behavior**

Update architecture docs so they no longer describe these items as missing once the code is merged:

- active queue consumption and block/reactivate semantics
- fair-score based requeueing
- batch concurrency acquire/release symmetry
- import-triggered activation

**Step 4: Run final verification**

Run:

```bash
mvn -pl call-task test
```

Expected:

- PASS, or document any environment-limited failures precisely

**Step 5: Commit**

```bash
git add \
  docs/call-task-number-scheduling-architecture.md \
  docs/plans/2026-05-28-call-task-scheduler-gap-closure-design.md
git commit -m "docs: update scheduler gap closure design"
```

Plan complete and saved to `docs/plans/2026-05-28-call-task-scheduler-gap-closure.md`. Two execution options:

**1. Subagent-Driven (this session)** - I dispatch fresh subagent per task, review between tasks, fast iteration

**2. Parallel Session (separate)** - Open new session with executing-plans, batch execution with checkpoints

Which approach?
