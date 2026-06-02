# Caller ID Selection Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add caller ID selection to `call-task` so each outbound attempt chooses a suitable caller number from a hybrid pool, separates first-attempt and retry scoring, and updates caller health from realtime dial feedback.

**Architecture:** Keep the current `call-task` dispatch loop and insert caller selection between ready-queue claim and `DIALING` state transition. Extend task policy, dial unit attribution, caller asset tables, and writeback-driven caller health updates so the system forms a persistent dispatch-feedback-selection loop inside the existing scheduler.

**Tech Stack:** Java 21, Spring Boot, Spring Scheduling, Spring Data Redis, MyBatis-Plus, Flyway, RocketMQ, JUnit 5, Mockito

---

### Task 1: Add Schema For Task Policy, Dial Attribution, And Caller Asset Tables

**Files:**
- Create: `call-task/src/main/resources/db/migration/V3__add_caller_id_selection_schema.sql`
- Modify: `call-task/src/test/java/com/callcenter/task/db/TaskSchemaMigrationTest.java`

**Step 1: Write the failing test**

Extend the schema migration test so it proves:

- `call_task` contains caller ID policy columns
- `call_dial_unit_xx` contains selection attribution columns
- new tables `call_caller_id`, `call_task_caller_id_binding`, and `call_caller_id_stats` exist

Example assertions:

```java
assertThat(columns("call_task")).contains("caller_id_mode", "optimization_goal", "answer_weight");
assertThat(columns("call_dial_unit_00")).contains("selected_caller_id", "attempt_stage", "talk_duration_seconds");
assertThat(tables()).contains("call_caller_id", "call_task_caller_id_binding", "call_caller_id_stats");
```

**Step 2: Run test to verify it fails**

Run:

```bash
mvn -pl call-task -Dtest=TaskSchemaMigrationTest test
```

Expected:

- FAIL because the new schema objects do not exist yet

**Step 3: Write minimal implementation**

Create migration `V3__add_caller_id_selection_schema.sql` that:

- adds task-level caller selection columns to `call_task`
- adds dispatch attribution and callback feedback columns to all `call_dial_unit_xx` tables
- creates `call_caller_id`
- creates `call_task_caller_id_binding`
- creates `call_caller_id_stats`
- adds indexes for task lookup, caller lookup, and time-bucket stats aggregation

**Step 4: Run test to verify it passes**

Run:

```bash
mvn -pl call-task -Dtest=TaskSchemaMigrationTest test
```

Expected:

- PASS

**Step 5: Commit**

```bash
git add \
  call-task/src/main/resources/db/migration/V3__add_caller_id_selection_schema.sql \
  call-task/src/test/java/com/callcenter/task/db/TaskSchemaMigrationTest.java
git commit -m "task: add caller id selection schema"
```

### Task 2: Extend Domain Models And API Requests For Caller ID Policy And Feedback

**Files:**
- Modify: `call-common/src/main/java/com/callcenter/common/entity/CallTaskEntity.java`
- Modify: `call-common/src/main/java/com/callcenter/common/entity/CallDialUnitEntity.java`
- Modify: `call-task/src/main/java/com/callcenter/task/model/CreateTaskRequest.java`
- Modify: `call-task/src/main/java/com/callcenter/task/model/DialResultCallbackRequest.java`
- Modify: `call-task/src/main/java/com/callcenter/task/model/TaskSummaryResponse.java`
- Modify: `call-task/src/main/java/com/callcenter/task/service/CallTaskService.java`
- Test: `call-task/src/test/java/com/callcenter/task/service/CallTaskServiceTest.java`
- Test: `call-task/src/test/java/com/callcenter/task/controller/DialResultCallbackControllerTest.java`

**Step 1: Write the failing test**

Add tests that prove:

- task creation persists default caller selection policy when request omits fields
- task creation persists explicit weights and mode when request includes them
- dial result callback request accepts ring/talk duration and hangup code

Example assertions:

```java
assertEquals("HYBRID", savedTask.getCallerIdMode());
assertEquals("ANSWER", savedTask.getOptimizationGoal());
assertEquals(1.0d, savedTask.getAnswerWeight());
```

**Step 2: Run test to verify it fails**

Run:

```bash
mvn -pl call-task -Dtest=CallTaskServiceTest,DialResultCallbackControllerTest test
```

Expected:

- FAIL because the fields and mapping logic do not exist yet

**Step 3: Write minimal implementation**

Update the entities and API models to include:

- task policy fields on `CallTaskEntity` and `CreateTaskRequest`
- selection attribution and callback feedback fields on `CallDialUnitEntity`
- callback request fields for `ringDurationSeconds`, `talkDurationSeconds`, and `hangupCode`
- task creation defaults in `CallTaskService`
- task summary response serialization for the new task policy fields

**Step 4: Run test to verify it passes**

Run:

```bash
mvn -pl call-task -Dtest=CallTaskServiceTest,DialResultCallbackControllerTest test
```

Expected:

- PASS

**Step 5: Commit**

```bash
git add \
  call-common/src/main/java/com/callcenter/common/entity/CallTaskEntity.java \
  call-common/src/main/java/com/callcenter/common/entity/CallDialUnitEntity.java \
  call-task/src/main/java/com/callcenter/task/model/CreateTaskRequest.java \
  call-task/src/main/java/com/callcenter/task/model/DialResultCallbackRequest.java \
  call-task/src/main/java/com/callcenter/task/model/TaskSummaryResponse.java \
  call-task/src/main/java/com/callcenter/task/service/CallTaskService.java \
  call-task/src/test/java/com/callcenter/task/service/CallTaskServiceTest.java \
  call-task/src/test/java/com/callcenter/task/controller/DialResultCallbackControllerTest.java
git commit -m "task: add caller id policy models"
```

### Task 3: Add Caller Asset, Binding, And Stats Persistence

**Files:**
- Create: `call-common/src/main/java/com/callcenter/common/entity/CallCallerIdEntity.java`
- Create: `call-common/src/main/java/com/callcenter/common/entity/CallTaskCallerIdBindingEntity.java`
- Create: `call-common/src/main/java/com/callcenter/common/entity/CallCallerIdStatsEntity.java`
- Create: `call-common/src/main/java/com/callcenter/common/mapper/CallCallerIdMapper.java`
- Create: `call-common/src/main/java/com/callcenter/common/mapper/CallTaskCallerIdBindingMapper.java`
- Create: `call-common/src/main/java/com/callcenter/common/mapper/CallCallerIdStatsMapper.java`
- Create: `call-task/src/main/java/com/callcenter/task/repository/CallCallerIdRepository.java`
- Create: `call-task/src/main/java/com/callcenter/task/repository/CallTaskCallerIdBindingRepository.java`
- Create: `call-task/src/main/java/com/callcenter/task/repository/CallCallerIdStatsRepository.java`
- Test: `call-task/src/test/java/com/callcenter/task/repository/CallCallerIdRepositoryTest.java`
- Test: `call-task/src/test/java/com/callcenter/task/repository/CallCallerIdStatsRepositoryTest.java`

**Step 1: Write the failing test**

Add repository tests that prove:

- active caller IDs can be listed by tenant and pool type
- task allow and deny bindings can be loaded by task
- caller stats rows can be inserted and upserted by caller ID, attempt stage, and time bucket

Example assertions:

```java
assertThat(repository.listActiveByTenant(9L)).extracting(CallCallerIdEntity::getCallerId).contains("02166668888");
assertThat(bindingRepository.listByTask(9L, 1001L)).hasSize(2);
assertThat(statsRepository.findBucket(9L, 3001L, "FIRST_ATTEMPT", bucket)).isPresent();
```

**Step 2: Run test to verify it fails**

Run:

```bash
mvn -pl call-task -Dtest=CallCallerIdRepositoryTest,CallCallerIdStatsRepositoryTest test
```

Expected:

- FAIL because the entities, mappers, and repositories do not exist yet

**Step 3: Write minimal implementation**

Create:

- entities for caller asset, task binding, and stats
- MyBatis mapper interfaces
- repositories for listing active callers, task bindings, and upserting stats buckets

Keep the first version simple:

- caller asset queries are tenant-scoped
- binding queries are task-scoped
- stats upsert uses one bucket row per `tenantId + callerIdId + attemptStage + timeBucket`

**Step 4: Run test to verify it passes**

Run:

```bash
mvn -pl call-task -Dtest=CallCallerIdRepositoryTest,CallCallerIdStatsRepositoryTest test
```

Expected:

- PASS

**Step 5: Commit**

```bash
git add \
  call-common/src/main/java/com/callcenter/common/entity/CallCallerIdEntity.java \
  call-common/src/main/java/com/callcenter/common/entity/CallTaskCallerIdBindingEntity.java \
  call-common/src/main/java/com/callcenter/common/entity/CallCallerIdStatsEntity.java \
  call-common/src/main/java/com/callcenter/common/mapper/CallCallerIdMapper.java \
  call-common/src/main/java/com/callcenter/common/mapper/CallTaskCallerIdBindingMapper.java \
  call-common/src/main/java/com/callcenter/common/mapper/CallCallerIdStatsMapper.java \
  call-task/src/main/java/com/callcenter/task/repository/CallCallerIdRepository.java \
  call-task/src/main/java/com/callcenter/task/repository/CallTaskCallerIdBindingRepository.java \
  call-task/src/main/java/com/callcenter/task/repository/CallCallerIdStatsRepository.java \
  call-task/src/test/java/com/callcenter/task/repository/CallCallerIdRepositoryTest.java \
  call-task/src/test/java/com/callcenter/task/repository/CallCallerIdStatsRepositoryTest.java
git commit -m "task: add caller id persistence"
```

### Task 4: Add Candidate Pool Builder And Task Policy Translator

**Files:**
- Create: `call-task/src/main/java/com/callcenter/task/caller/TaskCallerIdPolicy.java`
- Create: `call-task/src/main/java/com/callcenter/task/caller/AttemptStage.java`
- Create: `call-task/src/main/java/com/callcenter/task/caller/CallerIdCandidate.java`
- Create: `call-task/src/main/java/com/callcenter/task/caller/TaskCallerIdPolicyService.java`
- Create: `call-task/src/main/java/com/callcenter/task/caller/CallerIdCandidateService.java`
- Test: `call-task/src/test/java/com/callcenter/task/caller/TaskCallerIdPolicyServiceTest.java`
- Test: `call-task/src/test/java/com/callcenter/task/caller/CallerIdCandidateServiceTest.java`

**Step 1: Write the failing test**

Add tests that prove:

- `retryCount == 0` maps to `FIRST_ATTEMPT`
- `retryCount > 0` maps to `RETRY_ATTEMPT`
- `HYBRID` mode returns shared callers plus allow-listed callers minus deny-listed callers
- callers in cooldown are excluded

Example assertions:

```java
assertEquals(AttemptStage.FIRST_ATTEMPT, AttemptStage.fromRetryCount(0));
assertEquals(AttemptStage.RETRY_ATTEMPT, AttemptStage.fromRetryCount(1));
assertThat(candidates).extracting(CallerIdCandidate::callerId).contains("02166668888");
assertThat(candidates).extracting(CallerIdCandidate::callerId).doesNotContain("02199990000");
```

**Step 2: Run test to verify it fails**

Run:

```bash
mvn -pl call-task -Dtest=TaskCallerIdPolicyServiceTest,CallerIdCandidateServiceTest test
```

Expected:

- FAIL because the policy translator and candidate builder do not exist yet

**Step 3: Write minimal implementation**

Create:

- `AttemptStage` enum with retry-count mapping
- immutable `TaskCallerIdPolicy`
- `TaskCallerIdPolicyService` that maps `CallTaskEntity` to runtime policy with defaults
- `CallerIdCandidateService` that combines shared pool, task allow-list, task deny-list, and cooldown filtering

The service should support:

- `SHARED_ONLY`
- `TASK_ONLY`
- `HYBRID`

**Step 4: Run test to verify it passes**

Run:

```bash
mvn -pl call-task -Dtest=TaskCallerIdPolicyServiceTest,CallerIdCandidateServiceTest test
```

Expected:

- PASS

**Step 5: Commit**

```bash
git add \
  call-task/src/main/java/com/callcenter/task/caller/TaskCallerIdPolicy.java \
  call-task/src/main/java/com/callcenter/task/caller/AttemptStage.java \
  call-task/src/main/java/com/callcenter/task/caller/CallerIdCandidate.java \
  call-task/src/main/java/com/callcenter/task/caller/TaskCallerIdPolicyService.java \
  call-task/src/main/java/com/callcenter/task/caller/CallerIdCandidateService.java \
  call-task/src/test/java/com/callcenter/task/caller/TaskCallerIdPolicyServiceTest.java \
  call-task/src/test/java/com/callcenter/task/caller/CallerIdCandidateServiceTest.java
git commit -m "task: add caller candidate filtering"
```

### Task 5: Add Selector And Integrate It Into The Dispatch Path

**Files:**
- Create: `call-task/src/main/java/com/callcenter/task/caller/CallerIdSelection.java`
- Create: `call-task/src/main/java/com/callcenter/task/caller/CallerIdSelector.java`
- Modify: `call-task/src/main/java/com/callcenter/task/repository/CallDialUnitRepository.java`
- Modify: `call-task/src/main/java/com/callcenter/task/dispatch/PartitionSchedulerWorker.java`
- Modify: `call-task/src/main/java/com/callcenter/task/mq/DialDispatchMessage.java`
- Modify: `call-task/src/main/java/com/callcenter/task/mq/DialDispatchPublisher.java`
- Test: `call-task/src/test/java/com/callcenter/task/dispatch/PartitionSchedulerWorkerTest.java`
- Test: `call-task/src/test/java/com/callcenter/task/caller/CallerIdSelectorTest.java`

**Step 1: Write the failing test**

Add tests that prove:

- selector scores first-attempt and retry-attempt callers using separate stats views
- dispatch worker loads ready units, selects caller IDs, persists selection attribution, and publishes caller ID in MQ
- when no candidate is available, the ready unit is re-offered and no publish occurs

Example assertions:

```java
assertEquals("02166668888", selection.callerId());
verify(publisher).publish(argThat(unit -> "02166668888".equals(unit.getSelectedCallerId())));
verify(queue).offerReady(eq(1001L), eq(1), anyList());
```

**Step 2: Run test to verify it fails**

Run:

```bash
mvn -pl call-task -Dtest=PartitionSchedulerWorkerTest,CallerIdSelectorTest test
```

Expected:

- FAIL because the selector and repository hooks do not exist yet

**Step 3: Write minimal implementation**

Implement:

- `CallerIdSelector` with deterministic weighted scoring
- a repository read method to load claimed ready units with `retryCount`
- a repository write method to mark `DIALING` while saving `selectedCallerId`, `selectionScore`, `selectionReason`, and `attemptStage`
- dispatch message fields for `callerId`, `attemptStage`, and `selectionScore`
- worker integration between `claimReady` and `markDialingFromReady`

The selector must:

- use `FIRST_ATTEMPT` stats when `retryCount == 0`
- use `RETRY_ATTEMPT` stats when `retryCount > 0`
- apply task weights, failure penalties, cooldown checks, and exposure penalties

**Step 4: Run test to verify it passes**

Run:

```bash
mvn -pl call-task -Dtest=PartitionSchedulerWorkerTest,CallerIdSelectorTest test
```

Expected:

- PASS

**Step 5: Commit**

```bash
git add \
  call-task/src/main/java/com/callcenter/task/caller/CallerIdSelection.java \
  call-task/src/main/java/com/callcenter/task/caller/CallerIdSelector.java \
  call-task/src/main/java/com/callcenter/task/repository/CallDialUnitRepository.java \
  call-task/src/main/java/com/callcenter/task/dispatch/PartitionSchedulerWorker.java \
  call-task/src/main/java/com/callcenter/task/mq/DialDispatchMessage.java \
  call-task/src/main/java/com/callcenter/task/mq/DialDispatchPublisher.java \
  call-task/src/test/java/com/callcenter/task/dispatch/PartitionSchedulerWorkerTest.java \
  call-task/src/test/java/com/callcenter/task/caller/CallerIdSelectorTest.java
git commit -m "task: add realtime caller id selection"
```

### Task 6: Add Writeback-Driven Caller Health Updates

**Files:**
- Create: `call-task/src/main/java/com/callcenter/task/caller/CallerIdHealthEvent.java`
- Create: `call-task/src/main/java/com/callcenter/task/caller/CallerIdHealthService.java`
- Modify: `call-task/src/main/java/com/callcenter/task/service/DialResultWritebackService.java`
- Modify: `call-task/src/test/java/com/callcenter/task/service/DialResultWritebackServiceTest.java`
- Test: `call-task/src/test/java/com/callcenter/task/caller/CallerIdHealthServiceTest.java`

**Step 1: Write the failing test**

Add tests that prove:

- successful callbacks update caller stats for the selected caller and attempt stage
- failed callbacks update failure-code counts and health score
- talk duration contributes to conversion-oriented scoring inputs

Example assertions:

```java
verify(healthService).recordFeedback(argThat(event ->
        event.callerIdId().equals(3001L)
        && event.attemptStage() == AttemptStage.FIRST_ATTEMPT
        && event.talkDurationSeconds() == 45
));
```

**Step 2: Run test to verify it fails**

Run:

```bash
mvn -pl call-task -Dtest=DialResultWritebackServiceTest,CallerIdHealthServiceTest test
```

Expected:

- FAIL because the health service and feedback wiring do not exist yet

**Step 3: Write minimal implementation**

Implement:

- `CallerIdHealthEvent`
- `CallerIdHealthService`
- writeback integration that loads caller selection attribution from the dial unit
- stats updates keyed by `tenantId + callerIdId + attemptStage + currentBucket`
- health score updates based on answer rate, talk duration, and failure-code penalties

Keep the first version bounded:

- compute one rolling bucket update per callback
- persist the raw counters and a derived `healthScore`
- leave complaint and spam-mark penalties for future work

**Step 4: Run test to verify it passes**

Run:

```bash
mvn -pl call-task -Dtest=DialResultWritebackServiceTest,CallerIdHealthServiceTest test
```

Expected:

- PASS

**Step 5: Commit**

```bash
git add \
  call-task/src/main/java/com/callcenter/task/caller/CallerIdHealthEvent.java \
  call-task/src/main/java/com/callcenter/task/caller/CallerIdHealthService.java \
  call-task/src/main/java/com/callcenter/task/service/DialResultWritebackService.java \
  call-task/src/test/java/com/callcenter/task/service/DialResultWritebackServiceTest.java \
  call-task/src/test/java/com/callcenter/task/caller/CallerIdHealthServiceTest.java
git commit -m "task: add caller id health feedback"
```

### Task 7: Add End-To-End Verification For The Dispatch-Feedback Loop

**Files:**
- Modify: `call-task/src/test/java/com/callcenter/task/CallTaskFlowIntegrationTest.java`
- Modify: `call-task/src/test/java/com/callcenter/task/controller/CallTaskControllerTest.java`
- Modify: `docs/call-task-number-scheduling-architecture.md`

**Step 1: Write the failing test**

Extend integration coverage so it proves:

- task creation accepts caller policy fields
- dispatch messages include the selected caller ID
- callback updates caller attribution fields and caller stats

Example assertions:

```java
assertThat(dispatchMessage.callerId()).isEqualTo("02166668888");
assertThat(savedUnit.getAttemptStage()).isEqualTo("FIRST_ATTEMPT");
assertThat(stats.getAttemptCount()).isEqualTo(1);
```

**Step 2: Run test to verify it fails**

Run:

```bash
mvn -pl call-task -Dtest=CallTaskFlowIntegrationTest,CallTaskControllerTest test
```

Expected:

- FAIL because the new dispatch-feedback assertions are not satisfied yet

**Step 3: Write minimal implementation**

Adjust integration fixtures and docs so they reflect:

- task-level Caller ID strategy
- dispatch path selection step
- writeback-driven health feedback

Update `docs/call-task-number-scheduling-architecture.md` with the new caller selection components and message fields.

**Step 4: Run test to verify it passes**

Run:

```bash
mvn -pl call-task -Dtest=CallTaskFlowIntegrationTest,CallTaskControllerTest test
```

Expected:

- PASS

**Step 5: Commit**

```bash
git add \
  call-task/src/test/java/com/callcenter/task/CallTaskFlowIntegrationTest.java \
  call-task/src/test/java/com/callcenter/task/controller/CallTaskControllerTest.java \
  docs/call-task-number-scheduling-architecture.md
git commit -m "docs: wire caller id selection flow"
```
