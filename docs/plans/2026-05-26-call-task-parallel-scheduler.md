# Call Task Parallel Scheduler Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Redesign `call-task` scheduling from full-running-task polling into a Redis-partitioned, multi-instance, weighted-fair parallel scheduler that does not depend on task-level `nextDispatchTime`.

**Architecture:** Introduce Redis-backed partition ownership, active-task queues, and partition workers so each instance only schedules its owned partitions. Move retry and processing-timeout handling from full task scans to due-item movers, and make callback, retry, and recovery all reactivate tasks through a unified activation service. Keep MySQL as the source of truth and use compare-and-set updates plus Redis Lua queue moves to preserve correctness under concurrency.

**Tech Stack:** Java 21, Spring Boot 3.2, MyBatis-Plus, Spring Data Redis, Redis Lua scripts, Micrometer, JUnit 5, Mockito

---

### Task 1: Freeze the target behavior with scheduler design tests

**Files:**
- Create: `call-task/src/test/java/com/callcenter/task/dispatch/TaskPriorityWeightTest.java`
- Create: `call-task/src/test/java/com/callcenter/task/dispatch/TaskPartitionAssignmentTest.java`
- Create: `call-task/src/test/java/com/callcenter/task/dispatch/TaskActivationPolicyTest.java`

**Step 1: Write the failing tests**

```java
@Test
void shouldMapPriorityToStableWeight() {
    assertThat(TaskPriorityWeight.fromPriority(1)).isEqualTo(16);
    assertThat(TaskPriorityWeight.fromPriority(2)).isEqualTo(8);
    assertThat(TaskPriorityWeight.fromPriority(3)).isEqualTo(4);
    assertThat(TaskPriorityWeight.fromPriority(4)).isEqualTo(2);
}
```

```java
@Test
void shouldAssignTaskToFixedPartition() {
    TaskPartitioner partitioner = new TaskPartitioner(128);

    assertThat(partitioner.partitionOf(1001L)).isBetween(0, 127);
}
```

```java
@Test
void shouldReactivateTaskWhenCapacityReturns() {
    assertThat(TaskActivationPolicy.shouldActivate(
            TaskBlockReason.CONCURRENCY_FULL,
            true,
            true
    )).isTrue();
}
```

**Step 2: Run tests to verify they fail**

Run: `mvn -pl call-task -Dtest=TaskPriorityWeightTest,TaskPartitionAssignmentTest,TaskActivationPolicyTest test`
Expected: FAIL because the new scheduler model classes do not exist.

**Step 3: Write minimal implementation**

```java
final class TaskPriorityWeight {

    static int fromPriority(int priority) {
        return switch (priority) {
            case 1 -> 16;
            case 2 -> 8;
            case 3 -> 4;
            default -> 2;
        };
    }
}
```

**Step 4: Run tests to verify they pass**

Run: `mvn -pl call-task -Dtest=TaskPriorityWeightTest,TaskPartitionAssignmentTest,TaskActivationPolicyTest test`
Expected: PASS

**Step 5: Commit**

```bash
git add call-task/src/test/java/com/callcenter/task/dispatch/TaskPriorityWeightTest.java \
  call-task/src/test/java/com/callcenter/task/dispatch/TaskPartitionAssignmentTest.java \
  call-task/src/test/java/com/callcenter/task/dispatch/TaskActivationPolicyTest.java \
  call-task/src/main/java/com/callcenter/task/dispatch/TaskPriorityWeight.java \
  call-task/src/main/java/com/callcenter/task/dispatch/TaskPartitioner.java \
  call-task/src/main/java/com/callcenter/task/dispatch/TaskActivationPolicy.java \
  call-task/src/main/java/com/callcenter/task/dispatch/TaskBlockReason.java
git commit -m "task: add scheduler core value objects"
```

### Task 2: Add partition ownership and instance identity

**Files:**
- Create: `call-task/src/main/java/com/callcenter/task/dispatch/InstanceIdentityProvider.java`
- Create: `call-task/src/main/java/com/callcenter/task/dispatch/TaskPartitionManager.java`
- Create: `call-task/src/main/java/com/callcenter/task/dispatch/PartitionLease.java`
- Modify: `call-task/src/main/java/com/callcenter/task/config/CallTaskDispatchProperties.java`
- Test: `call-task/src/test/java/com/callcenter/task/dispatch/TaskPartitionManagerTest.java`

**Step 1: Write the failing test**

```java
@Test
void shouldAcquireOnlyUnownedPartition() {
    when(redis.opsForValue().setIfAbsent("call:scheduler:partition:7:owner", "instance-a", lease))
            .thenReturn(true);

    assertThat(manager.tryAcquire(7)).isTrue();
}
```

**Step 2: Run test to verify it fails**

Run: `mvn -pl call-task -Dtest=TaskPartitionManagerTest test`
Expected: FAIL because partition ownership services do not exist.

**Step 3: Write minimal implementation**

```java
public boolean tryAcquire(int partition) {
    return Boolean.TRUE.equals(stringRedisTemplate.opsForValue().setIfAbsent(
            ownerKey(partition),
            instanceIdentityProvider.instanceId(),
            properties.getPartitionLeaseTtl()
    ));
}
```

**Step 4: Run test to verify it passes**

Run: `mvn -pl call-task -Dtest=TaskPartitionManagerTest test`
Expected: PASS

**Step 5: Commit**

```bash
git add call-task/src/main/java/com/callcenter/task/dispatch/InstanceIdentityProvider.java \
  call-task/src/main/java/com/callcenter/task/dispatch/TaskPartitionManager.java \
  call-task/src/main/java/com/callcenter/task/dispatch/PartitionLease.java \
  call-task/src/main/java/com/callcenter/task/config/CallTaskDispatchProperties.java \
  call-task/src/test/java/com/callcenter/task/dispatch/TaskPartitionManagerTest.java
git commit -m "task: add partition lease ownership"
```

### Task 3: Add active-task queue primitives

**Files:**
- Create: `call-task/src/main/java/com/callcenter/task/dispatch/ActiveTaskQueue.java`
- Create: `call-task/src/main/java/com/callcenter/task/dispatch/TaskSchedulingMeta.java`
- Create: `call-task/src/main/java/com/callcenter/task/dispatch/TaskSchedulingState.java`
- Test: `call-task/src/test/java/com/callcenter/task/dispatch/ActiveTaskQueueTest.java`

**Step 1: Write the failing test**

```java
@Test
void shouldPopLowestFairScoreTask() {
    queue.activate(3, 1001L, 10L);
    queue.activate(3, 1002L, 20L);

    assertThat(queue.pollNextTask(3)).contains(1001L);
}
```

**Step 2: Run test to verify it fails**

Run: `mvn -pl call-task -Dtest=ActiveTaskQueueTest test`
Expected: FAIL because the active-task queue does not exist.

**Step 3: Write minimal implementation**

```java
public Optional<Long> pollNextTask(int partition) {
    Set<String> ids = stringRedisTemplate.opsForZSet().range(activeKey(partition), 0, 0);
    return ids == null || ids.isEmpty()
            ? Optional.empty()
            : Optional.of(Long.parseLong(ids.iterator().next()));
}
```

**Step 4: Run test to verify it passes**

Run: `mvn -pl call-task -Dtest=ActiveTaskQueueTest test`
Expected: PASS

**Step 5: Commit**

```bash
git add call-task/src/main/java/com/callcenter/task/dispatch/ActiveTaskQueue.java \
  call-task/src/main/java/com/callcenter/task/dispatch/TaskSchedulingMeta.java \
  call-task/src/main/java/com/callcenter/task/dispatch/TaskSchedulingState.java \
  call-task/src/test/java/com/callcenter/task/dispatch/ActiveTaskQueueTest.java
git commit -m "task: add active task queue primitives"
```

### Task 4: Add task activation service and blocked-state transitions

**Files:**
- Create: `call-task/src/main/java/com/callcenter/task/dispatch/TaskActivationService.java`
- Create: `call-task/src/main/java/com/callcenter/task/dispatch/TaskActivationRequest.java`
- Test: `call-task/src/test/java/com/callcenter/task/dispatch/TaskActivationServiceTest.java`

**Step 1: Write the failing test**

```java
@Test
void shouldActivateRunningTaskIntoOwnedPartitionQueue() {
    TaskActivationRequest request = new TaskActivationRequest(9L, 1001L, 1, 8, 7);

    service.activate(request);

    verify(activeTaskQueue).activate(7, 1001L, 0L);
}
```

**Step 2: Run test to verify it fails**

Run: `mvn -pl call-task -Dtest=TaskActivationServiceTest test`
Expected: FAIL because activation orchestration does not exist.

**Step 3: Write minimal implementation**

```java
public void activate(TaskActivationRequest request) {
    activeTaskQueue.upsertMeta(request.taskId(), request.tenantId(), request.priority(), request.weight(), request.partition());
    activeTaskQueue.activate(request.partition(), request.taskId(), 0L);
}
```

**Step 4: Run test to verify it passes**

Run: `mvn -pl call-task -Dtest=TaskActivationServiceTest test`
Expected: PASS

**Step 5: Commit**

```bash
git add call-task/src/main/java/com/callcenter/task/dispatch/TaskActivationService.java \
  call-task/src/main/java/com/callcenter/task/dispatch/TaskActivationRequest.java \
  call-task/src/test/java/com/callcenter/task/dispatch/TaskActivationServiceTest.java
git commit -m "task: add task activation orchestration"
```

### Task 5: Refactor dispatch worker to consume active tasks instead of scanning running tasks

**Files:**
- Modify: `call-task/src/main/java/com/callcenter/task/dispatch/CallTaskDispatcher.java`
- Create: `call-task/src/main/java/com/callcenter/task/dispatch/PartitionSchedulerWorker.java`
- Modify: `call-task/src/main/java/com/callcenter/task/repository/CallTaskRepository.java`
- Test: `call-task/src/test/java/com/callcenter/task/dispatch/PartitionSchedulerWorkerTest.java`
- Test: `call-task/src/test/java/com/callcenter/task/dispatch/CallTaskDispatcherTest.java`

**Step 1: Write the failing tests**

```java
@Test
void shouldDispatchOwnedPartitionTaskWithSmallBudget() {
    when(activeTaskQueue.pollNextTask(7)).thenReturn(Optional.of(1001L));
    when(concurrencyLimiter.available(9L, 1001L, 20)).thenReturn(3);
    when(queue.claimReady(eq(1001L), eq(1), eq(3), any())).thenReturn(List.of(11L, 12L, 13L));

    worker.runPartition(7);

    verify(publisher, times(3)).publish(any());
}
```

```java
@Test
void shouldStopLoadingAllRunningTasksFromDispatcherLoop() {
    dispatcher.dispatchOwnedPartitions();

    verify(taskRepository, never()).loadRunningTasks();
}
```

**Step 2: Run tests to verify they fail**

Run: `mvn -pl call-task -Dtest=PartitionSchedulerWorkerTest,CallTaskDispatcherTest test`
Expected: FAIL because dispatch still depends on task scanning.

**Step 3: Write minimal implementation**

```java
@Scheduled(fixedDelayString = "${call.task.dispatch.poll-interval:PT1S}")
public void dispatchOwnedPartitions() {
    for (int partition : taskPartitionManager.ownedPartitions()) {
        partitionSchedulerWorker.runPartition(partition);
    }
}
```

**Step 4: Run test to verify it passes**

Run: `mvn -pl call-task -Dtest=PartitionSchedulerWorkerTest,CallTaskDispatcherTest test`
Expected: PASS

**Step 5: Commit**

```bash
git add call-task/src/main/java/com/callcenter/task/dispatch/CallTaskDispatcher.java \
  call-task/src/main/java/com/callcenter/task/dispatch/PartitionSchedulerWorker.java \
  call-task/src/main/java/com/callcenter/task/repository/CallTaskRepository.java \
  call-task/src/test/java/com/callcenter/task/dispatch/PartitionSchedulerWorkerTest.java \
  call-task/src/test/java/com/callcenter/task/dispatch/CallTaskDispatcherTest.java
git commit -m "task: dispatch from owned active partitions"
```

### Task 6: Extend concurrency limiter with observable available capacity

**Files:**
- Modify: `call-task/src/main/java/com/callcenter/task/dispatch/DispatchConcurrencyLimiter.java`
- Test: `call-task/src/test/java/com/callcenter/task/dispatch/DispatchConcurrencyLimiterTest.java`

**Step 1: Write the failing test**

```java
@Test
void shouldReportRemainingTaskCapacity() {
    when(redis.opsForValue().get("call:concurrency:task:1001")).thenReturn("4");

    assertThat(limiter.available(9L, 1001L, 10)).isEqualTo(6);
}
```

**Step 2: Run test to verify it fails**

Run: `mvn -pl call-task -Dtest=DispatchConcurrencyLimiterTest test`
Expected: FAIL because the limiter does not expose remaining capacity.

**Step 3: Write minimal implementation**

```java
public int available(Long tenantId, Long taskId, int taskMaxConcurrency) {
    int current = currentCount(taskKey(taskId));
    return Math.max(taskMaxConcurrency - current, 0);
}
```

**Step 4: Run test to verify it passes**

Run: `mvn -pl call-task -Dtest=DispatchConcurrencyLimiterTest test`
Expected: PASS

**Step 5: Commit**

```bash
git add call-task/src/main/java/com/callcenter/task/dispatch/DispatchConcurrencyLimiter.java \
  call-task/src/test/java/com/callcenter/task/dispatch/DispatchConcurrencyLimiterTest.java
git commit -m "task: expose available dispatch capacity"
```

### Task 7: Move retry handling to partition due queues

**Files:**
- Modify: `call-task/src/main/java/com/callcenter/task/dispatch/RetryQueueScheduler.java`
- Modify: `call-task/src/main/java/com/callcenter/task/dispatch/RedisDialUnitQueue.java`
- Modify: `call-task/src/main/java/com/callcenter/task/dispatch/RedisQueueScriptRepository.java`
- Test: `call-task/src/test/java/com/callcenter/task/dispatch/RetryQueueSchedulerTest.java`

**Step 1: Write the failing test**

```java
@Test
void shouldMoveDueRetryItemsAndReactivateTask() {
    when(queue.moveDueRetryItems(7, now, 100)).thenReturn(List.of(new RetryDueItem(1001L, 1, 11L)));

    scheduler.requeueDueRetries();

    verify(taskActivationService).activateRetryReady(1001L);
}
```

**Step 2: Run test to verify it fails**

Run: `mvn -pl call-task -Dtest=RetryQueueSchedulerTest test`
Expected: FAIL because retry still scans running tasks.

**Step 3: Write minimal implementation**

```java
for (int partition : taskPartitionManager.ownedPartitions()) {
    List<RetryDueItem> items = redisDialUnitQueue.moveDueRetryItems(partition, now, properties.getRetryMoveBatchSize());
    items.stream().map(RetryDueItem::taskId).distinct().forEach(taskActivationService::activateRetryReady);
}
```

**Step 4: Run test to verify it passes**

Run: `mvn -pl call-task -Dtest=RetryQueueSchedulerTest test`
Expected: PASS

**Step 5: Commit**

```bash
git add call-task/src/main/java/com/callcenter/task/dispatch/RetryQueueScheduler.java \
  call-task/src/main/java/com/callcenter/task/dispatch/RedisDialUnitQueue.java \
  call-task/src/main/java/com/callcenter/task/dispatch/RedisQueueScriptRepository.java \
  call-task/src/test/java/com/callcenter/task/dispatch/RetryQueueSchedulerTest.java
git commit -m "task: move retry by due partition queue"
```

### Task 8: Replace processing recovery scan with due-timeout mover and exact release counting

**Files:**
- Modify: `call-task/src/main/java/com/callcenter/task/dispatch/ProcessingTimeoutRecoveryJob.java`
- Modify: `call-task/src/main/java/com/callcenter/task/repository/CallDialUnitRepository.java`
- Create: `call-task/src/main/java/com/callcenter/task/model/RecoveredDialUnit.java`
- Test: `call-task/src/test/java/com/callcenter/task/dispatch/ProcessingTimeoutRecoveryJobTest.java`
- Test: `call-task/src/test/java/com/callcenter/task/repository/CallDialUnitRepositoryTest.java`

**Step 1: Write the failing tests**

```java
@Test
void shouldReleaseOnlyRowsActuallyRecovered() {
    when(repository.recoverExpiredDialing(any(), eq(1001L), anyList(), any()))
            .thenReturn(List.of(new RecoveredDialUnit(11L, true), new RecoveredDialUnit(12L, false)));

    job.recoverExpiredProcessing();

    verify(limiter, times(1)).release(9L, 1001L);
}
```

```java
@Test
void shouldRequireDispatchTokenAndExpireAtToRecoverDialing() {
    assertThat(repository.recoverExpiredDialing(shardKey, 1001L, items, retryAt)).hasSize(1);
}
```

**Step 2: Run tests to verify they fail**

Run: `mvn -pl call-task -Dtest=ProcessingTimeoutRecoveryJobTest,CallDialUnitRepositoryTest test`
Expected: FAIL because recovery still releases by attempted row count.

**Step 3: Write minimal implementation**

```java
List<RecoveredDialUnit> recovered = repository.recoverExpiredDialing(shardKey, taskId, items, retryAt);
for (RecoveredDialUnit unit : recovered) {
    if (unit.recovered()) {
        concurrencyLimiter.release(tenantId, taskId);
    }
}
```

**Step 4: Run test to verify it passes**

Run: `mvn -pl call-task -Dtest=ProcessingTimeoutRecoveryJobTest,CallDialUnitRepositoryTest test`
Expected: PASS

**Step 5: Commit**

```bash
git add call-task/src/main/java/com/callcenter/task/dispatch/ProcessingTimeoutRecoveryJob.java \
  call-task/src/main/java/com/callcenter/task/repository/CallDialUnitRepository.java \
  call-task/src/main/java/com/callcenter/task/model/RecoveredDialUnit.java \
  call-task/src/test/java/com/callcenter/task/dispatch/ProcessingTimeoutRecoveryJobTest.java \
  call-task/src/test/java/com/callcenter/task/repository/CallDialUnitRepositoryTest.java
git commit -m "task: recover processing by exact successful rows"
```

### Task 9: Reactivate tasks from callback, retry, and recovery events

**Files:**
- Modify: `call-task/src/main/java/com/callcenter/task/service/DialResultWritebackService.java`
- Modify: `call-task/src/main/java/com/callcenter/task/dispatch/ProcessingTimeoutRecoveryJob.java`
- Modify: `call-task/src/main/java/com/callcenter/task/dispatch/RetryQueueScheduler.java`
- Test: `call-task/src/test/java/com/callcenter/task/service/DialResultWritebackServiceTest.java`

**Step 1: Write the failing test**

```java
@Test
void shouldReactivateTaskAfterSuccessfulCapacityRelease() {
    when(repository.markSuccess(any(), eq(1001L), eq(11L), eq("token-1"))).thenReturn(true);

    service.handleCallback(9L, request);

    verify(taskActivationService).activateCapacityAvailable(9L, 1001L);
}
```

**Step 2: Run test to verify it fails**

Run: `mvn -pl call-task -Dtest=DialResultWritebackServiceTest test`
Expected: FAIL because callback does not reactivate tasks.

**Step 3: Write minimal implementation**

```java
if (updated) {
    redisDialUnitQueue.ackProcessing(...);
    concurrencyLimiter.release(...);
    taskActivationService.activateCapacityAvailable(tenantId, request.getTaskId());
}
```

**Step 4: Run test to verify it passes**

Run: `mvn -pl call-task -Dtest=DialResultWritebackServiceTest test`
Expected: PASS

**Step 5: Commit**

```bash
git add call-task/src/main/java/com/callcenter/task/service/DialResultWritebackService.java \
  call-task/src/main/java/com/callcenter/task/dispatch/ProcessingTimeoutRecoveryJob.java \
  call-task/src/main/java/com/callcenter/task/dispatch/RetryQueueScheduler.java \
  call-task/src/test/java/com/callcenter/task/service/DialResultWritebackServiceTest.java
git commit -m "task: reactivate tasks from scheduling events"
```

### Task 10: Stop using task-level `nextDispatchTime` in scheduling flow

Status: completed in code. `TaskSummaryResponse` no longer exposes `nextDispatchTime`, and the scheduler no longer relies on task-level time gating.

**Files:**
- Modify: `call-task/src/main/java/com/callcenter/task/dispatch/CallTaskDispatcher.java`
- Modify: `call-task/src/main/java/com/callcenter/task/repository/CallTaskRepository.java`
- Modify: `call-task/src/main/java/com/callcenter/task/model/TaskSummaryResponse.java`
- Test: `call-task/src/test/java/com/callcenter/task/dispatch/CallTaskDispatcherTest.java`

**Step 1: Write the failing test**

```java
@Test
void shouldNotGateDispatchOnTaskNextDispatchTime() {
    task.setNextDispatchTime(LocalDateTime.now().plusMinutes(5));

    assertThat(dispatcher.shouldDispatch(task)).isTrue();
}
```

**Step 2: Run test to verify it fails**

Run: `mvn -pl call-task -Dtest=CallTaskDispatcherTest test`
Expected: FAIL because dispatch still reads `nextDispatchTime`.

**Step 3: Write minimal implementation**

```java
public void dispatchOwnedPartitions() {
    for (int partition : taskPartitionManager.ownedPartitions()) {
        partitionSchedulerWorker.runPartition(partition);
    }
}
```

**Step 4: Run test to verify it passes**

Run: `mvn -pl call-task -Dtest=CallTaskDispatcherTest test`
Expected: PASS

**Step 5: Commit**

```bash
git add call-task/src/main/java/com/callcenter/task/dispatch/CallTaskDispatcher.java \
  call-task/src/main/java/com/callcenter/task/repository/CallTaskRepository.java \
  call-task/src/main/java/com/callcenter/task/model/TaskSummaryResponse.java \
  call-task/src/test/java/com/callcenter/task/dispatch/CallTaskDispatcherTest.java
git commit -m "task: remove next dispatch time from scheduler loop"
```

### Task 11: Add metrics for partitions, activations, and release correctness

**Files:**
- Modify: `call-task/src/main/java/com/callcenter/task/metrics/CallTaskMetrics.java`
- Test: `call-task/src/test/java/com/callcenter/task/metrics/CallTaskMetricsTest.java`

**Step 1: Write the failing test**

```java
@Test
void shouldRegisterPartitionSchedulerMeters() {
    assertThat(meterRegistry.find("call.task.scheduler.partition.owned").gauge()).isNotNull();
    assertThat(meterRegistry.find("call.task.scheduler.active.count").gauge()).isNotNull();
    assertThat(meterRegistry.find("call.task.scheduler.recovery.release.skew").gauge()).isNotNull();
}
```

**Step 2: Run test to verify it fails**

Run: `mvn -pl call-task -Dtest=CallTaskMetricsTest test`
Expected: FAIL because scheduler redesign metrics are not registered.

**Step 3: Write minimal implementation**

```java
Gauge.builder("call.task.scheduler.partition.owned", schedulerMetrics, SchedulerMetrics::ownedPartitions).register(meterRegistry);
Gauge.builder("call.task.scheduler.active.count", schedulerMetrics, SchedulerMetrics::activeTasks).register(meterRegistry);
Gauge.builder("call.task.scheduler.recovery.release.skew", schedulerMetrics, SchedulerMetrics::recoveryReleaseSkew).register(meterRegistry);
```

**Step 4: Run test to verify it passes**

Run: `mvn -pl call-task -Dtest=CallTaskMetricsTest test`
Expected: PASS

**Step 5: Commit**

```bash
git add call-task/src/main/java/com/callcenter/task/metrics/CallTaskMetrics.java \
  call-task/src/test/java/com/callcenter/task/metrics/CallTaskMetricsTest.java
git commit -m "task: add parallel scheduler metrics"
```

### Task 12: Add concurrency and scale verification coverage

**Files:**
- Create: `call-task/src/test/java/com/callcenter/task/dispatch/ParallelSchedulerIntegrationTest.java`
- Modify: `docs/plans/2026-05-26-call-task-parallel-scheduler-design.md`

**Step 1: Write the failing test**

```java
@Test
void shouldShareOwnedPartitionsAcrossTwoInstancesWithoutDuplicateDispatch() {
    assertThat(simulateTwoSchedulersAndCollectDispatches())
            .doesNotHaveDuplicates();
}
```

**Step 2: Run test to verify it fails**

Run: `mvn -pl call-task -Dtest=ParallelSchedulerIntegrationTest test`
Expected: FAIL because multi-instance scheduler coverage does not exist.

**Step 3: Write minimal implementation**

```java
assertThat(dispatchedDialUnitIds).doesNotHaveDuplicates();
assertThat(lowPriorityTaskDispatchCount).isPositive();
```

**Step 4: Run test to verify it passes**

Run: `mvn -pl call-task -Dtest=ParallelSchedulerIntegrationTest test`
Expected: PASS

**Step 5: Commit**

```bash
git add call-task/src/test/java/com/callcenter/task/dispatch/ParallelSchedulerIntegrationTest.java \
  docs/plans/2026-05-26-call-task-parallel-scheduler-design.md
git commit -m "task: verify parallel scheduler scale behavior"
```

### Task 13: Run focused verification and document rollout notes

**Files:**
- Modify: `docs/plans/2026-05-26-call-task-parallel-scheduler.md`

**Step 1: Run focused scheduler tests**

Run: `mvn -pl call-task -Dtest=TaskPriorityWeightTest,TaskPartitionManagerTest,ActiveTaskQueueTest,PartitionSchedulerWorkerTest,RetryQueueSchedulerTest,ProcessingTimeoutRecoveryJobTest,DialResultWritebackServiceTest,ParallelSchedulerIntegrationTest test`
Expected: PASS

**Step 2: Run broader module verification**

Run: `mvn -pl call-task test`
Expected: PASS

**Step 3: Update rollout notes in the plan**

```markdown
## Rollout Notes

- Roll out partition ownership first with metrics enabled
- Compare Redis inflight counters against DB `DIALING` counts
- Disable legacy full-scan scheduler once active-task metrics stabilize
```

**Step 4: Commit**

```bash
git add docs/plans/2026-05-26-call-task-parallel-scheduler.md
git commit -m "task: finalize parallel scheduler rollout plan"
```
