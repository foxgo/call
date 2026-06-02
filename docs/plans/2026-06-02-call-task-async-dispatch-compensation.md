# Call Task Async Dispatch Compensation Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Remove synchronous MQ send latency from `runPartition(...)` and immediately compensate failed or unknown sends back to `READY`.

**Architecture:** Keep the existing scheme-2 scheduler and `DIALING` state transition, but replace inline MQ sends with a bounded async dispatch service. A dedicated compensation service performs conditional `DIALING -> READY` rollback, Redis ready requeue, and quota release only when the DB rollback succeeds.

**Tech Stack:** Java 21, Spring Boot, RocketMQ Spring, Spring scheduling/executors, Redis, MyBatis-Plus, JUnit 5, Mockito, Micrometer

---

### Task 1: Add Repository Rollback Support For Failed Sends

**Files:**
- Modify: `call-task/src/main/java/com/callcenter/task/repository/CallDialUnitRepository.java`
- Create: `call-task/src/test/java/com/callcenter/task/repository/CallDialUnitRepositoryRollbackTest.java`

**Step 1: Write the failing test**

Add a repository test that verifies a new `revertDialingToReady(...)` method issues one conditional update requiring `task_id`, `id`, `status = DIALING`, and `dispatch_token`, and returns `true` only when the update count is positive.

**Step 2: Run test to verify it fails**

Run: `mvn -o -pl call-task -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=CallDialUnitRepositoryRollbackTest test`
Expected: FAIL because the repository does not yet expose `revertDialingToReady(...)`.

**Step 3: Write minimal implementation**

Implement:

```java
public boolean revertDialingToReady(
        ShardKey shardKey,
        long taskId,
        long dialUnitId,
        String dispatchToken,
        LocalDateTime updatedAt
) {
    UpdateWrapper<CallDialUnitEntity> update = new UpdateWrapper<>();
    update.eq("task_id", taskId)
            .eq("id", dialUnitId)
            .eq("status", CallDialUnitStatus.DIALING.name())
            .eq("dispatch_token", dispatchToken)
            .set("status", CallDialUnitStatus.READY.name())
            .set("dispatch_token", null)
            .set("inflight_expire_at", null)
            .set("updated_at", updatedAt);
    return callDialUnitMapper.update(null, update) > 0;
}
```

**Step 4: Run test to verify it passes**

Run: `mvn -o -pl call-task -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=CallDialUnitRepositoryRollbackTest test`
Expected: PASS

**Step 5: Commit**

```bash
git add call-task/src/main/java/com/callcenter/task/repository/CallDialUnitRepository.java \
  call-task/src/test/java/com/callcenter/task/repository/CallDialUnitRepositoryRollbackTest.java
git commit -m "task: add dispatch rollback repository support"
```

### Task 2: Add Dispatch Compensation Service

**Files:**
- Create: `call-task/src/main/java/com/callcenter/task/dispatch/DialDispatchCompensationService.java`
- Modify: `call-task/src/main/java/com/callcenter/task/metrics/CallTaskMetrics.java`
- Create: `call-task/src/test/java/com/callcenter/task/dispatch/DialDispatchCompensationServiceTest.java`
- Modify: `call-task/src/test/java/com/callcenter/task/metrics/CallTaskMetricsTest.java`

**Step 1: Write the failing tests**

Add tests that verify:
- successful DB rollback triggers Redis `offerReady(...)` and `concurrencyLimiter.release(...)`
- failed DB rollback triggers neither Redis requeue nor quota release
- metrics register and increment compensation counters

**Step 2: Run tests to verify they fail**

Run: `mvn -o -pl call-task -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=DialDispatchCompensationServiceTest,CallTaskMetricsTest test`
Expected: FAIL because the compensation service and metrics do not yet exist.

**Step 3: Write minimal implementation**

Implement a service shaped like:

```java
public void compensateFailedDispatch(ShardKey shardKey, CallDialUnitEntity unit) {
    boolean reverted = callDialUnitRepository.revertDialingToReady(
            shardKey,
            unit.getTaskId(),
            unit.getId(),
            unit.getDispatchToken(),
            LocalDateTime.now()
    );
    if (!reverted) {
        metrics.incrementDispatchCompensationSkipped();
        return;
    }
    unit.setStatus(CallDialUnitStatus.READY.name());
    unit.setDispatchToken(null);
    unit.setInflightExpireAt(null);
    redisDialUnitQueue.offerReady(unit.getTaskId(), shardKey.tableIndex(), List.of(unit));
    concurrencyLimiter.release(unit.getTenantId(), unit.getTaskId());
    metrics.incrementDispatchCompensated();
}
```

Add Micrometer counters for send failure, compensation success, and compensation skipped.

**Step 4: Run tests to verify they pass**

Run: `mvn -o -pl call-task -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=DialDispatchCompensationServiceTest,CallTaskMetricsTest test`
Expected: PASS

**Step 5: Commit**

```bash
git add call-task/src/main/java/com/callcenter/task/dispatch/DialDispatchCompensationService.java \
  call-task/src/main/java/com/callcenter/task/metrics/CallTaskMetrics.java \
  call-task/src/test/java/com/callcenter/task/dispatch/DialDispatchCompensationServiceTest.java \
  call-task/src/test/java/com/callcenter/task/metrics/CallTaskMetricsTest.java
git commit -m "task: add dispatch compensation service"
```

### Task 3: Add Async Dispatch Executor And Async Send Service

**Files:**
- Modify: `call-task/src/main/java/com/callcenter/task/config/CallTaskDispatchProperties.java`
- Modify: `call-task/src/main/resources/application.yml`
- Modify: `call-task/src/main/java/com/callcenter/task/config/DispatchExecutorConfiguration.java`
- Create: `call-task/src/main/java/com/callcenter/task/dispatch/AsyncDialDispatchService.java`
- Create: `call-task/src/test/java/com/callcenter/task/dispatch/AsyncDialDispatchServiceTest.java`

**Step 1: Write the failing tests**

Add tests that verify:
- the async service submits work to an executor instead of running inline
- successful publish increments publish metrics
- publisher exception or unknown-result path invokes compensation

**Step 2: Run tests to verify they fail**

Run: `mvn -o -pl call-task -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=AsyncDialDispatchServiceTest test`
Expected: FAIL because the async service does not yet exist.

**Step 3: Write minimal implementation**

Add dispatch properties such as:
- `dispatchSendParallelism`

Extend executor configuration with a dedicated bean for MQ dispatch send tasks.

Implement the service shape:

```java
public void submit(ShardKey shardKey, CallDialUnitEntity unit) {
    dispatchSendExecutor.execute(() -> {
        try {
            dialDispatchPublisher.publish(unit);
            metrics.incrementDispatchPublished();
        } catch (Exception ex) {
            metrics.incrementDispatchSendFailed();
            dialDispatchCompensationService.compensateFailedDispatch(shardKey, unit);
        }
    });
}
```

Treat exceptions and unknown-send-result wrapper outcomes the same way.

**Step 4: Run tests to verify they pass**

Run: `mvn -o -pl call-task -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=AsyncDialDispatchServiceTest test`
Expected: PASS

**Step 5: Commit**

```bash
git add call-task/src/main/java/com/callcenter/task/config/CallTaskDispatchProperties.java \
  call-task/src/main/resources/application.yml \
  call-task/src/main/java/com/callcenter/task/config/DispatchExecutorConfiguration.java \
  call-task/src/main/java/com/callcenter/task/dispatch/AsyncDialDispatchService.java \
  call-task/src/test/java/com/callcenter/task/dispatch/AsyncDialDispatchServiceTest.java
git commit -m "task: add async dial dispatch send path"
```

### Task 4: Switch Scheduler Publish To Async Dispatch

**Files:**
- Modify: `call-task/src/main/java/com/callcenter/task/dispatch/PartitionSchedulerWorker.java`
- Modify: `call-task/src/test/java/com/callcenter/task/dispatch/PartitionSchedulerWorkerTest.java`

**Step 1: Write the failing test**

Update the worker test so it expects:
- `AsyncDialDispatchService.submit(...)` is invoked per dispatched unit
- `DialDispatchPublisher.publish(...)` is no longer called directly from the worker
- dispatch-published metrics are no longer incremented by the worker itself

**Step 2: Run test to verify it fails**

Run: `mvn -o -pl call-task -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=PartitionSchedulerWorkerTest test`
Expected: FAIL because the worker still calls the publisher directly.

**Step 3: Write minimal implementation**

Inject `AsyncDialDispatchService` into `PartitionSchedulerWorker` and replace:

```java
for (CallDialUnitEntity unit : units) {
    dialDispatchPublisher.publish(unit);
    metrics.incrementDispatchPublished();
}
```

with:

```java
for (CallDialUnitEntity unit : units) {
    asyncDialDispatchService.submit(shardKey, unit);
}
```

Leave all task block/reactivate logic unchanged.

**Step 4: Run test to verify it passes**

Run: `mvn -o -pl call-task -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=PartitionSchedulerWorkerTest test`
Expected: PASS

**Step 5: Commit**

```bash
git add call-task/src/main/java/com/callcenter/task/dispatch/PartitionSchedulerWorker.java \
  call-task/src/test/java/com/callcenter/task/dispatch/PartitionSchedulerWorkerTest.java
git commit -m "task: move scheduler publish to async dispatch service"
```

### Task 5: Verify The Async Dispatch Compensation Path

**Files:**
- Modify as needed based on test fallout

**Step 1: Run focused async dispatch suite**

Run: `mvn -o -pl call-task -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=CallDialUnitRepositoryRollbackTest,DialDispatchCompensationServiceTest,AsyncDialDispatchServiceTest,PartitionSchedulerWorkerTest,CallTaskMetricsTest test`
Expected: PASS

**Step 2: Run broader module verification**

Run: `mvn -o -pl call-task -am -Dsurefire.failIfNoSpecifiedTests=false test`
Expected: PASS with existing Docker/Testcontainers-based skips unchanged.

**Step 3: Commit follow-up fixes if needed**

```bash
git add <any adjusted files>
git commit -m "task: verify async dispatch compensation path"
```
