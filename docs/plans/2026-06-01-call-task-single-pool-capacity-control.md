# Call Task Single Pool Capacity Control Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add single-pool AI capacity control to `call-task` so multiple running tasks share one provider pool safely while each task receives a dynamic target concurrency.

**Architecture:** Keep the current partitioned scheduler execution path intact and add a control plane around it. Introduce a single-pool capacity provider, periodic metrics collection, a control engine that computes pool and task targets, a registry for target state, and a limiter upgrade that enforces dynamic targets during dispatch.

**Tech Stack:** Java 21, Spring Boot, Spring Scheduling, Spring Data Redis, Micrometer, MyBatis-Plus, RocketMQ, JUnit 5, Mockito

---

### Task 1: Add Capacity Control Configuration And Models

**Files:**
- Create: `call-task/src/main/java/com/callcenter/task/config/CallTaskCapacityControlProperties.java`
- Create: `call-task/src/main/java/com/callcenter/task/dispatch/capacity/CapacitySnapshot.java`
- Create: `call-task/src/main/java/com/callcenter/task/dispatch/capacity/DispatchMetricsSnapshot.java`
- Create: `call-task/src/main/java/com/callcenter/task/dispatch/capacity/ControlInput.java`
- Create: `call-task/src/main/java/com/callcenter/task/dispatch/capacity/ControlDecision.java`
- Modify: `call-task/src/main/resources/application.yml`
- Test: `call-task/src/test/java/com/callcenter/task/config/CallTaskCapacityControlPropertiesTest.java`

**Step 1: Write the failing test**

Add a configuration binding test that proves:

- `call.task.capacity.control-interval` binds to `Duration`
- `call.task.capacity.pool-key` binds to `String`
- `call.task.capacity.deadband-ratio` and `max-adjust-ratio` bind to `double`

Example assertions:

```java
assertEquals(Duration.ofSeconds(10), properties.getControlInterval());
assertEquals("ai-default", properties.getPoolKey());
assertEquals(0.05d, properties.getDeadbandRatio());
```

**Step 2: Run test to verify it fails**

Run:

```bash
mvn -pl call-task -Dtest=CallTaskCapacityControlPropertiesTest test
```

Expected:

- FAIL because the properties class and fields do not exist

**Step 3: Write minimal implementation**

Create:

- `CallTaskCapacityControlProperties` with validated fields for control interval, metrics interval, cooldown, deadband ratio, max adjust ratio, pool key, pool hard max, task min target, task base share, ewma alpha
- immutable records for `CapacitySnapshot`, `DispatchMetricsSnapshot`, `ControlInput`, and `ControlDecision`
- default config values in `application.yml`

**Step 4: Run test to verify it passes**

Run:

```bash
mvn -pl call-task -Dtest=CallTaskCapacityControlPropertiesTest test
```

Expected:

- PASS

**Step 5: Commit**

```bash
git add \
  call-task/src/main/java/com/callcenter/task/config/CallTaskCapacityControlProperties.java \
  call-task/src/main/java/com/callcenter/task/dispatch/capacity/CapacitySnapshot.java \
  call-task/src/main/java/com/callcenter/task/dispatch/capacity/DispatchMetricsSnapshot.java \
  call-task/src/main/java/com/callcenter/task/dispatch/capacity/ControlInput.java \
  call-task/src/main/java/com/callcenter/task/dispatch/capacity/ControlDecision.java \
  call-task/src/main/resources/application.yml \
  call-task/src/test/java/com/callcenter/task/config/CallTaskCapacityControlPropertiesTest.java
git commit -m "task: add capacity control config models"
```

### Task 2: Add Target Registry For Pool And Task Concurrency

**Files:**
- Create: `call-task/src/main/java/com/callcenter/task/dispatch/capacity/TaskTargetConcurrencyRegistry.java`
- Create: `call-task/src/main/java/com/callcenter/task/dispatch/capacity/TaskTargetState.java`
- Test: `call-task/src/test/java/com/callcenter/task/dispatch/capacity/TaskTargetConcurrencyRegistryTest.java`

**Step 1: Write the failing test**

Add tests that prove:

- the registry can store and load pool target
- the registry can store and load task target state
- missing task target falls back to empty

Example assertions:

```java
registry.savePoolTarget("ai-default", 320);
assertEquals(320, registry.loadPoolTarget("ai-default").orElseThrow());

registry.saveTaskTarget(1001L, new TaskTargetState(24, now, "pool_expand", now.plusSeconds(30)));
assertEquals(24, registry.loadTaskTarget(1001L).orElseThrow().targetConcurrency());
```

**Step 2: Run test to verify it fails**

Run:

```bash
mvn -pl call-task -Dtest=TaskTargetConcurrencyRegistryTest test
```

Expected:

- FAIL because the registry does not exist

**Step 3: Write minimal implementation**

Create a Redis-backed registry that manages:

- `call:capacity:pool:{poolKey}:target`
- `call:capacity:task:{taskId}:target`
- `call:capacity:task:{taskId}:control-meta`

Support:

- save/load pool target
- save/load task target state
- update cooldown metadata

**Step 4: Run test to verify it passes**

Run:

```bash
mvn -pl call-task -Dtest=TaskTargetConcurrencyRegistryTest test
```

Expected:

- PASS

**Step 5: Commit**

```bash
git add \
  call-task/src/main/java/com/callcenter/task/dispatch/capacity/TaskTargetConcurrencyRegistry.java \
  call-task/src/main/java/com/callcenter/task/dispatch/capacity/TaskTargetState.java \
  call-task/src/test/java/com/callcenter/task/dispatch/capacity/TaskTargetConcurrencyRegistryTest.java
git commit -m "task: add task target registry"
```

### Task 3: Add Single Pool Capacity Provider

**Files:**
- Create: `call-task/src/main/java/com/callcenter/task/dispatch/capacity/CapacityProvider.java`
- Create: `call-task/src/main/java/com/callcenter/task/dispatch/capacity/SinglePoolCapacityProvider.java`
- Modify: `call-task/src/main/java/com/callcenter/task/config/CallTaskConcurrencyProperties.java`
- Test: `call-task/src/test/java/com/callcenter/task/dispatch/capacity/SinglePoolCapacityProviderTest.java`

**Step 1: Write the failing test**

Add tests that prove:

- provider returns a snapshot using configured pool key and hard max
- utilization is derived from `busy / total`
- unavailable provider returns a degraded health score and `available() == false`

Example assertions:

```java
CapacitySnapshot snapshot = provider.snapshot();
assertEquals("ai-default", snapshot.poolKey());
assertEquals(1000, snapshot.total());
assertEquals(0.25d, snapshot.utilization());
```

**Step 2: Run test to verify it fails**

Run:

```bash
mvn -pl call-task -Dtest=SinglePoolCapacityProviderTest test
```

Expected:

- FAIL because the provider interface and implementation do not exist

**Step 3: Write minimal implementation**

Create:

- `CapacityProvider`
- `SinglePoolCapacityProvider`

P0 implementation can:

- use configured pool hard max as `total`
- read current pool in-flight or external busy count from Redis/config adapter
- derive `idle`, `utilization`, and `healthScore`

**Step 4: Run test to verify it passes**

Run:

```bash
mvn -pl call-task -Dtest=SinglePoolCapacityProviderTest test
```

Expected:

- PASS

**Step 5: Commit**

```bash
git add \
  call-task/src/main/java/com/callcenter/task/dispatch/capacity/CapacityProvider.java \
  call-task/src/main/java/com/callcenter/task/dispatch/capacity/SinglePoolCapacityProvider.java \
  call-task/src/main/java/com/callcenter/task/config/CallTaskConcurrencyProperties.java \
  call-task/src/test/java/com/callcenter/task/dispatch/capacity/SinglePoolCapacityProviderTest.java
git commit -m "task: add single pool capacity provider"
```

### Task 4: Add Dispatch Metrics Collector

**Files:**
- Create: `call-task/src/main/java/com/callcenter/task/dispatch/capacity/DispatchMetricsCollector.java`
- Modify: `call-task/src/main/java/com/callcenter/task/metrics/CallTaskMetrics.java`
- Modify: `call-task/src/main/java/com/callcenter/task/service/DialResultWritebackService.java`
- Modify: `call-task/src/main/java/com/callcenter/task/dispatch/DialingRecoveryJob.java`
- Test: `call-task/src/test/java/com/callcenter/task/dispatch/capacity/DispatchMetricsCollectorTest.java`

**Step 1: Write the failing test**

Add tests that prove:

- collector returns task metrics for a running task
- collector smooths connect rate using EWMA
- collector reads current in-flight counts and remaining calls

Example assertions:

```java
DispatchMetricsSnapshot snapshot = collector.collectForTask(9L, 1001L);
assertEquals(1001L, snapshot.taskId());
assertTrue(snapshot.connectRate() >= 0.0d);
assertEquals(12L, snapshot.activeCalls());
```

**Step 2: Run test to verify it fails**

Run:

```bash
mvn -pl call-task -Dtest=DispatchMetricsCollectorTest test
```

Expected:

- FAIL because the collector does not exist

**Step 3: Write minimal implementation**

Create a collector that:

- reads in-flight counts from the concurrency limiter or Redis keys
- consumes success/failure/recovery counters exposed by `CallTaskMetrics`
- computes EWMA connect rate using configured alpha
- reads remaining pending/ready counts through repository queries or lightweight aggregation

**Step 4: Run test to verify it passes**

Run:

```bash
mvn -pl call-task -Dtest=DispatchMetricsCollectorTest test
```

Expected:

- PASS

**Step 5: Commit**

```bash
git add \
  call-task/src/main/java/com/callcenter/task/dispatch/capacity/DispatchMetricsCollector.java \
  call-task/src/main/java/com/callcenter/task/metrics/CallTaskMetrics.java \
  call-task/src/main/java/com/callcenter/task/service/DialResultWritebackService.java \
  call-task/src/main/java/com/callcenter/task/dispatch/DialingRecoveryJob.java \
  call-task/src/test/java/com/callcenter/task/dispatch/capacity/DispatchMetricsCollectorTest.java
git commit -m "task: add dispatch metrics collector"
```

### Task 5: Implement Capacity Control Engine

**Files:**
- Create: `call-task/src/main/java/com/callcenter/task/dispatch/capacity/CapacityControlEngine.java`
- Create: `call-task/src/main/java/com/callcenter/task/dispatch/capacity/TaskPolicy.java`
- Test: `call-task/src/test/java/com/callcenter/task/dispatch/capacity/CapacityControlEngineTest.java`

**Step 1: Write the failing test**

Add tests that prove:

- healthy signals increase target within max adjust ratio
- poor trunk health or high LLM load reduce target
- changes below deadband are ignored
- cooldown blocks repeated changes

Example assertions:

```java
ControlDecision decision = engine.decide(input);
assertEquals(55, decision.targetConcurrency());
assertTrue(decision.reason().contains("connect"));
```

**Step 2: Run test to verify it fails**

Run:

```bash
mvn -pl call-task -Dtest=CapacityControlEngineTest test
```

Expected:

- FAIL because the engine does not exist

**Step 3: Write minimal implementation**

Implement:

- factor mapping for connect rate, occupancy, trunk health, and LLM load
- cooldown window
- deadband suppression
- clamp to last target `0.9x ~ 1.1x`
- lower bound using `taskMinTarget`

**Step 4: Run test to verify it passes**

Run:

```bash
mvn -pl call-task -Dtest=CapacityControlEngineTest test
```

Expected:

- PASS

**Step 5: Commit**

```bash
git add \
  call-task/src/main/java/com/callcenter/task/dispatch/capacity/CapacityControlEngine.java \
  call-task/src/main/java/com/callcenter/task/dispatch/capacity/TaskPolicy.java \
  call-task/src/test/java/com/callcenter/task/dispatch/capacity/CapacityControlEngineTest.java
git commit -m "task: add capacity control engine"
```

### Task 6: Add Pool-To-Task Target Allocator And Control Job

**Files:**
- Create: `call-task/src/main/java/com/callcenter/task/dispatch/capacity/TaskTargetAllocator.java`
- Create: `call-task/src/main/java/com/callcenter/task/dispatch/capacity/CapacityControlJob.java`
- Modify: `call-task/src/main/java/com/callcenter/task/dispatch/ActiveTaskQueue.java`
- Modify: `call-task/src/main/java/com/callcenter/task/dispatch/TaskActivationService.java`
- Test: `call-task/src/test/java/com/callcenter/task/dispatch/capacity/CapacityControlJobTest.java`

**Step 1: Write the failing test**

Add tests that prove:

- pool target is distributed across active tasks using task weights
- tasks previously blocked by `CONCURRENCY_FULL` are reactivated when target increases
- paused tasks do not receive fresh target allocation

Example assertions:

```java
verify(registry).saveTaskTarget(eq(1001L), argThat(state -> state.targetConcurrency() == 24));
verify(taskActivationService).activate(9L, 1001L);
```

**Step 2: Run test to verify it fails**

Run:

```bash
mvn -pl call-task -Dtest=CapacityControlJobTest test
```

Expected:

- FAIL because allocator and scheduled control job do not exist

**Step 3: Write minimal implementation**

Implement:

- a simple weighted allocator using `TaskPriorityWeight`
- a scheduled job that runs every control interval
- save pool target and task target into the registry
- reactivate blocked tasks only when target grows above current in-flight

**Step 4: Run test to verify it passes**

Run:

```bash
mvn -pl call-task -Dtest=CapacityControlJobTest test
```

Expected:

- PASS

**Step 5: Commit**

```bash
git add \
  call-task/src/main/java/com/callcenter/task/dispatch/capacity/TaskTargetAllocator.java \
  call-task/src/main/java/com/callcenter/task/dispatch/capacity/CapacityControlJob.java \
  call-task/src/main/java/com/callcenter/task/dispatch/ActiveTaskQueue.java \
  call-task/src/main/java/com/callcenter/task/dispatch/TaskActivationService.java \
  call-task/src/test/java/com/callcenter/task/dispatch/capacity/CapacityControlJobTest.java
git commit -m "task: add task target allocation"
```

### Task 7: Upgrade DispatchConcurrencyLimiter To Enforce Dynamic Targets

**Files:**
- Modify: `call-task/src/main/java/com/callcenter/task/dispatch/DispatchConcurrencyLimiter.java`
- Modify: `call-task/src/test/java/com/callcenter/task/dispatch/DispatchConcurrencyLimiterTest.java`

**Step 1: Write the failing test**

Add tests that prove:

- pool target limits granted slots even when static global max allows more
- task target limits granted slots even when task static max allows more
- release preserves counter symmetry after partial grants

Example assertions:

```java
assertEquals(2, limiter.tryAcquireBatch(9L, 1001L, 10, 5));
assertEquals(0, limiter.tryAcquireBatch(9L, 1001L, 10, 1));
```

**Step 2: Run test to verify it fails**

Run:

```bash
mvn -pl call-task -Dtest=DispatchConcurrencyLimiterTest test
```

Expected:

- FAIL because limiter only considers static global, tenant, and task caps

**Step 3: Write minimal implementation**

Update limiter to read:

- pool target
- current pool in-flight
- task target
- current task in-flight

Grant using:

```text
min(
  requested,
  remainingGlobalHardCap,
  remainingPoolTargetCap,
  remainingTenantCap,
  remainingTaskStaticCap,
  remainingTaskTargetCap
)
```

**Step 4: Run test to verify it passes**

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
git commit -m "task: enforce dynamic capacity targets"
```

### Task 8: Wire Dynamic Targets Into Worker Flow

**Files:**
- Modify: `call-task/src/main/java/com/callcenter/task/dispatch/PartitionSchedulerWorker.java`
- Modify: `call-task/src/test/java/com/callcenter/task/dispatch/PartitionSchedulerWorkerTest.java`

**Step 1: Write the failing test**

Add tests that prove:

- worker blocks with `CONCURRENCY_FULL` when dynamic pool or task target has no remaining capacity
- worker continues to reactivate after successful dispatch under a partial dynamic grant
- worker does not exceed task target even if `dispatchBatchSize` is larger

Example assertions:

```java
verify(activeTaskQueue).block(any(TaskSchedulingMeta.class), eq(TaskBlockReason.CONCURRENCY_FULL));
verify(concurrencyLimiter).tryAcquireBatch(9L, 1001L, 50, 20);
```

**Step 2: Run test to verify it fails**

Run:

```bash
mvn -pl call-task -Dtest=PartitionSchedulerWorkerTest test
```

Expected:

- FAIL because worker behavior only reflects static concurrency caps

**Step 3: Write minimal implementation**

Update worker logic so that:

- requested budget still comes from batch size and task static max
- granted budget is entirely limiter-driven
- block/reactivate decisions remain based on actual grant and actual dispatch results

**Step 4: Run test to verify it passes**

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
git commit -m "task: wire dynamic targets into worker"
```

### Task 9: Add Observability For Capacity Decisions

**Files:**
- Modify: `call-task/src/main/java/com/callcenter/task/metrics/CallTaskMetrics.java`
- Modify: `call-task/src/main/resources/application.yml`
- Test: `call-task/src/test/java/com/callcenter/task/metrics/CallTaskMetricsTest.java`

**Step 1: Write the failing test**

Add tests that prove:

- pool target gauge is registered
- task target gauge or counter family is registered
- cooldown-skipped and deadband-skipped counters are registered

Example assertions:

```java
assertNotNull(registry.find("call.task.capacity.pool.target").gauge());
assertNotNull(registry.find("call.task.capacity.control.cooldown.skipped").counter());
```

**Step 2: Run test to verify it fails**

Run:

```bash
mvn -pl call-task -Dtest=CallTaskMetricsTest test
```

Expected:

- FAIL because capacity control metrics are missing

**Step 3: Write minimal implementation**

Extend metrics to expose:

- pool target
- pool busy
- pool utilization
- target update counters
- cooldown skip counter
- deadband skip counter
- capacity reject counters

**Step 4: Run test to verify it passes**

Run:

```bash
mvn -pl call-task -Dtest=CallTaskMetricsTest test
```

Expected:

- PASS

**Step 5: Commit**

```bash
git add \
  call-task/src/main/java/com/callcenter/task/metrics/CallTaskMetrics.java \
  call-task/src/main/resources/application.yml \
  call-task/src/test/java/com/callcenter/task/metrics/CallTaskMetricsTest.java
git commit -m "task: add capacity control observability"
```

### Task 10: Run Module Verification And Document Rollout Notes

**Files:**
- Modify: `docs/call-task-number-scheduling-architecture.md`
- Modify: `docs/plans/2026-06-01-call-task-single-pool-capacity-control-design.md`

**Step 1: Run focused tests**

Run:

```bash
mvn -pl call-task -Dtest=\
CallTaskCapacityControlPropertiesTest,\
TaskTargetConcurrencyRegistryTest,\
SinglePoolCapacityProviderTest,\
DispatchMetricsCollectorTest,\
CapacityControlEngineTest,\
CapacityControlJobTest,\
DispatchConcurrencyLimiterTest,\
PartitionSchedulerWorkerTest,\
CallTaskMetricsTest test
```

Expected:

- PASS

**Step 2: Run module tests**

Run:

```bash
mvn -pl call-task test
```

Expected:

- PASS

**Step 3: Update docs**

Document:

- new capacity control loop
- new config keys
- degraded-mode behavior when provider metrics are unavailable
- rollout sequence and rollback switch

**Step 4: Commit**

```bash
git add \
  docs/call-task-number-scheduling-architecture.md \
  docs/plans/2026-06-01-call-task-single-pool-capacity-control-design.md
git commit -m "task: document capacity control rollout"
```

Plan complete and saved to `docs/plans/2026-06-01-call-task-single-pool-capacity-control.md`. Two execution options:

**1. Subagent-Driven (this session)** - I dispatch fresh subagent per task, review between tasks, fast iteration

**2. Parallel Session (separate)** - Open new session with executing-plans, batch execution with checkpoints

Which approach?
