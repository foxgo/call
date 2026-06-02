# Async Dispatch Validation And Gate Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add validation and lightweight gating to async dial dispatch before publish while keeping `PartitionSchedulerWorker` as the owner of scheduling decisions and `READY -> DIALING` transitions.

**Architecture:** Introduce a validator for prepared dispatch units and a lightweight gate service for send-time allow/reject decisions. Keep caller-id selection, concurrency allocation, and state transitions in `runPartition(...)`; only validation, send gating, publish, and compensation run inside `AsyncDialDispatchService`.

**Tech Stack:** Java 21, Spring Boot, JUnit 5, Mockito, Micrometer

---

### Task 1: Add Failing Tests For Async Dispatch Flow

**Files:**
- Modify: `call-task/src/test/java/com/callcenter/task/dispatch/AsyncDialDispatchServiceTest.java`

**Step 1: Write the failing test**

Add tests that assert:
- validation failure compensates without publish
- gate rejection compensates without publish
- publish success still increments published
- publish exception still compensates
- executor rejection still compensates

**Step 2: Run test to verify it fails**

Run: `mvn -pl call-task -Dtest=AsyncDialDispatchServiceTest test`
Expected: FAIL because `AsyncDialDispatchService` has no validator/gate dependencies or related metrics.

**Step 3: Write minimal implementation**

Inject validator and gate service into `AsyncDialDispatchService`, add async execution flow with validation, gate evaluation, publish, and unified compensation.

**Step 4: Run test to verify it passes**

Run: `mvn -pl call-task -Dtest=AsyncDialDispatchServiceTest test`
Expected: PASS

### Task 2: Add Validator And Gate Tests

**Files:**
- Create: `call-task/src/test/java/com/callcenter/task/dispatch/DefaultDispatchUnitValidatorTest.java`
- Create: `call-task/src/test/java/com/callcenter/task/dispatch/DefaultDispatchGateServiceTest.java`

**Step 1: Write the failing test**

Add tests that assert:
- validator rejects missing required fields and bad phone format
- validator accepts a prepared unit
- gate rejects non-running task
- gate allows running task

**Step 2: Run test to verify it fails**

Run: `mvn -pl call-task -Dtest=DefaultDispatchUnitValidatorTest,DefaultDispatchGateServiceTest test`
Expected: FAIL because the validator, gate service, and related types do not exist.

**Step 3: Write minimal implementation**

Create:
- `DispatchUnitValidator`
- `DefaultDispatchUnitValidator`
- `DispatchPreparationException`
- `DispatchGateDecision`
- `DispatchGateService`
- `DefaultDispatchGateService`

**Step 4: Run test to verify it passes**

Run: `mvn -pl call-task -Dtest=DefaultDispatchUnitValidatorTest,DefaultDispatchGateServiceTest test`
Expected: PASS

### Task 3: Add Metrics Coverage

**Files:**
- Modify: `call-task/src/main/java/com/callcenter/task/metrics/CallTaskMetrics.java`
- Modify: `call-task/src/test/java/com/callcenter/task/metrics/CallTaskMetricsTest.java`

**Step 1: Write the failing test**

Add expectations for:
- `call.task.dispatch.validation.failed`
- `call.task.dispatch.gate.rejected`

**Step 2: Run test to verify it fails**

Run: `mvn -pl call-task -Dtest=CallTaskMetricsTest test`
Expected: FAIL because the counters and increment methods are missing.

**Step 3: Write minimal implementation**

Register the counters and add increment methods in `CallTaskMetrics`.

**Step 4: Run test to verify it passes**

Run: `mvn -pl call-task -Dtest=CallTaskMetricsTest test`
Expected: PASS

### Task 4: Run Focused Verification

**Files:**
- Modify as needed based on test fallout

**Step 1: Run focused suite**

Run: `mvn -pl call-task -Dtest=AsyncDialDispatchServiceTest,DefaultDispatchUnitValidatorTest,DefaultDispatchGateServiceTest,CallTaskMetricsTest test`
Expected: PASS

**Step 2: Fix fallout if needed**

Adjust only the touched async dispatch, metrics, and tests.

**Step 3: Re-run focused suite**

Run: `mvn -pl call-task -Dtest=AsyncDialDispatchServiceTest,DefaultDispatchUnitValidatorTest,DefaultDispatchGateServiceTest,CallTaskMetricsTest test`
Expected: PASS
