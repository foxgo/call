# Call Task Scheme 2 Dispatch Optimization Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Reduce scheduler hot-path overhead without changing the dispatch consistency model by batching quota operations, making active-task pop atomic, and draining more than one task per partition per scheduler tick.

**Architecture:** Keep `PartitionSchedulerWorker` as the synchronous dispatch pipeline, but remove avoidable Redis round-trips and coarse scheduler granularity. Redis scripts handle atomic active-task pop and batch quota adjustments; the dispatcher loops per partition up to a bounded budget each tick.

**Tech Stack:** Java 21, Spring Boot, Spring Data Redis, Redis Lua scripts, JUnit 5, Mockito

---

### Task 1: Add Atomic Active-Task Pop With Meta

**Files:**
- Modify: `call-task/src/main/java/com/callcenter/task/dispatch/ActiveTaskQueue.java`
- Modify: `call-task/src/test/java/com/callcenter/task/dispatch/ActiveTaskQueueTest.java`

**Step 1: Write the failing test**

Add a test that activates a task, stores its meta, and asserts a single queue call returns both the task id and parsed meta while removing the task from the active zset.

**Step 2: Run test to verify it fails**

Run: `mvn -pl call-task -Dtest=ActiveTaskQueueTest test`
Expected: FAIL because `ActiveTaskQueue` does not yet expose an atomic pop-with-meta method.

**Step 3: Write minimal implementation**

Introduce a `pollNextTaskWithMeta(int partition)` method that:
- atomically removes the lowest-score task from the partition active zset
- loads the task meta once
- returns a compact record containing `taskId` and `TaskSchedulingMeta`

Keep `block` and `reactivate` overloads able to reuse known meta to avoid re-reading Redis.

**Step 4: Run test to verify it passes**

Run: `mvn -pl call-task -Dtest=ActiveTaskQueueTest test`
Expected: PASS

**Step 5: Commit**

```bash
git add call-task/src/main/java/com/callcenter/task/dispatch/ActiveTaskQueue.java \
  call-task/src/test/java/com/callcenter/task/dispatch/ActiveTaskQueueTest.java
git commit -m "task: make active task pop atomic"
```

### Task 2: Batch Quota Acquire And Release

**Files:**
- Modify: `call-task/src/main/java/com/callcenter/task/dispatch/DispatchConcurrencyLimiter.java`
- Modify: `call-task/src/test/java/com/callcenter/task/dispatch/DispatchConcurrencyLimiterTest.java`

**Step 1: Write the failing test**

Add tests that assert:
- one batch acquire call goes through Redis `execute(...)` once and returns the granted count
- one batch release call goes through Redis `execute(...)` once for the count released

**Step 2: Run test to verify it fails**

Run: `mvn -pl call-task -Dtest=DispatchConcurrencyLimiterTest test`
Expected: FAIL because acquire and release still loop slot-by-slot.

**Step 3: Write minimal implementation**

Replace looped acquire/release with Redis scripts that:
- compute the maximum grant within global, tenant, and task limits
- increment the three counters by the granted amount and refresh TTL
- decrement the three counters by a release count in one call

Keep `tryAcquire(...)` and `release(...)` delegating to the batch methods for compatibility.

**Step 4: Run test to verify it passes**

Run: `mvn -pl call-task -Dtest=DispatchConcurrencyLimiterTest test`
Expected: PASS

**Step 5: Commit**

```bash
git add call-task/src/main/java/com/callcenter/task/dispatch/DispatchConcurrencyLimiter.java \
  call-task/src/test/java/com/callcenter/task/dispatch/DispatchConcurrencyLimiterTest.java
git commit -m "task: batch dispatch quota operations"
```

### Task 3: Drain More Work Per Partition Tick

**Files:**
- Modify: `call-task/src/main/java/com/callcenter/task/config/CallTaskDispatchProperties.java`
- Modify: `call-task/src/main/resources/application.yml`
- Modify: `call-task/src/main/java/com/callcenter/task/dispatch/PartitionSchedulerWorker.java`
- Modify: `call-task/src/main/java/com/callcenter/task/dispatch/CallTaskDispatcher.java`
- Modify: `call-task/src/test/java/com/callcenter/task/dispatch/PartitionSchedulerWorkerTest.java`
- Modify: `call-task/src/test/java/com/callcenter/task/dispatch/CallTaskDispatcherTest.java`

**Step 1: Write the failing test**

Add tests that assert:
- `runPartition(...)` reports whether it actually processed a task
- the dispatcher keeps invoking the worker on the same partition until either no more work is found or the configured per-tick limit is hit

**Step 2: Run test to verify it fails**

Run: `mvn -pl call-task -Dtest=PartitionSchedulerWorkerTest,CallTaskDispatcherTest test`
Expected: FAIL because `runPartition(...)` is void and the dispatcher only executes one task per partition per tick.

**Step 3: Write minimal implementation**

Change the worker to return `boolean didWork`, using the atomic active-task pop result and reusing meta for block/reactivate paths. Add a bounded per-partition loop in the dispatcher controlled by a new config property such as `maxTasksPerPartitionTick`.

**Step 4: Run test to verify it passes**

Run: `mvn -pl call-task -Dtest=PartitionSchedulerWorkerTest,CallTaskDispatcherTest test`
Expected: PASS

**Step 5: Commit**

```bash
git add call-task/src/main/java/com/callcenter/task/config/CallTaskDispatchProperties.java \
  call-task/src/main/resources/application.yml \
  call-task/src/main/java/com/callcenter/task/dispatch/PartitionSchedulerWorker.java \
  call-task/src/main/java/com/callcenter/task/dispatch/CallTaskDispatcher.java \
  call-task/src/test/java/com/callcenter/task/dispatch/PartitionSchedulerWorkerTest.java \
  call-task/src/test/java/com/callcenter/task/dispatch/CallTaskDispatcherTest.java
git commit -m "task: drain more dispatch work per partition tick"
```

### Task 4: Verify The Optimized Scheduler Path

**Files:**
- Modify as needed based on test fallout

**Step 1: Run focused test suite**

Run: `mvn -pl call-task -Dtest=ActiveTaskQueueTest,DispatchConcurrencyLimiterTest,PartitionSchedulerWorkerTest,CallTaskDispatcherTest,RedisDialUnitQueueTest test`
Expected: PASS

**Step 2: Run broader module verification**

Run: `mvn -pl call-task test`
Expected: PASS

**Step 3: Commit follow-up fixes if needed**

```bash
git add <any adjusted files>
git commit -m "task: verify scheduler optimization changes"
```
