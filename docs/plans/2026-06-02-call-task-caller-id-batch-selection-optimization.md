# Call Task Caller ID Batch Selection Optimization Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Remove per-unit caller stats lookups from `PartitionSchedulerWorker.runPartition(...)` while keeping caller selection inside the synchronous `READY -> DIALING` pipeline.

**Architecture:** Keep `AsyncDialDispatchService` unchanged as the post-`DIALING` async MQ sender. Refactor caller selection so `PartitionSchedulerWorker` preloads caller stats in batch by `AttemptStage`, then calls a pure in-memory selector for each unit before `markDialingSelectionsFromReady(...)`.

**Tech Stack:** Java 21, Spring Boot, MyBatis-Plus, JUnit 5, Mockito

---

### Task 1: Add Selector Tests For Preloaded Stats Path

**Files:**
- Modify: `call-task/src/test/java/com/callcenter/task/caller/CallerIdSelectorTest.java`

**Step 1: Write the failing test**

Add tests for a new selector entrypoint that accepts preloaded stats and does not need repository access:

```java
@Test
void shouldSelectBestCallerUsingPreloadedFirstAttemptStats() {
    CallCallerIdStatsRepository repository = mock(CallCallerIdStatsRepository.class);
    CallerIdSelector selector = new CallerIdSelector(repository);

    var result = selector.selectWithStats(
            dialUnit(0),
            new TaskCallerIdPolicy("HYBRID", "ANSWER", 1D, 0D, 0D, 0D, false, 3600, 200),
            List.of(
                    new CallerIdCandidate(3001L, "02166668888", "SHARED", 0D, 1D, 0),
                    new CallerIdCandidate(3002L, "02166668889", "SHARED", 0D, 1D, 0)
            ),
            Map.of(
                    3001L, stats(3001L, "FIRST_ATTEMPT", 10L, 7L, 5L, 300L),
                    3002L, stats(3002L, "FIRST_ATTEMPT", 10L, 3L, 2L, 30L)
            )
    );

    assertTrue(result.isPresent());
    assertEquals("02166668888", result.orElseThrow().callerId());
    verifyNoInteractions(repository);
}
```

Add a second test for retry units:

```java
@Test
void shouldUseRetryAttemptStageWhenSelectingWithPreloadedStats() {
    CallCallerIdStatsRepository repository = mock(CallCallerIdStatsRepository.class);
    CallerIdSelector selector = new CallerIdSelector(repository);

    var result = selector.selectWithStats(
            dialUnit(1),
            new TaskCallerIdPolicy("HYBRID", "ANSWER", 1D, 0.5D, 0D, 0D, false, 3600, 200),
            List.of(
                    new CallerIdCandidate(3001L, "02166668888", "SHARED", 0D, 1D, 0),
                    new CallerIdCandidate(3002L, "02166668889", "SHARED", 0D, 1D, 0)
            ),
            Map.of(
                    3001L, stats(3001L, "RETRY_ATTEMPT", 10L, 2L, 1L, 10L),
                    3002L, stats(3002L, "RETRY_ATTEMPT", 10L, 6L, 4L, 180L)
            )
    );

    assertTrue(result.isPresent());
    assertEquals("02166668889", result.orElseThrow().callerId());
}
```

**Step 2: Run test to verify it fails**

Run: `mvn -pl call-task -Dtest=CallerIdSelectorTest test`

Expected: FAIL because `CallerIdSelector` does not yet expose `selectWithStats(...)`.

**Step 3: Write minimal implementation**

Modify `call-task/src/main/java/com/callcenter/task/caller/CallerIdSelector.java`:

```java
public Optional<CallerIdSelection> selectWithStats(
        CallDialUnitEntity dialUnit,
        TaskCallerIdPolicy policy,
        List<CallerIdCandidate> candidates,
        Map<Long, CallCallerIdStatsEntity> statsByCaller
) {
    if (candidates == null || candidates.isEmpty()) {
        return Optional.empty();
    }
    AttemptStage attemptStage = AttemptStage.fromRetryCount(dialUnit.getRetryCount());
    return candidates.stream()
            .map(candidate -> toSelection(candidate, statsByCaller.get(candidate.callerIdId()), policy, attemptStage))
            .max(Comparator.comparingDouble(CallerIdSelection::score));
}

public Optional<CallerIdSelection> select(
        Long tenantId,
        CallDialUnitEntity dialUnit,
        TaskCallerIdPolicy policy,
        List<CallerIdCandidate> candidates
) {
    AttemptStage attemptStage = AttemptStage.fromRetryCount(dialUnit.getRetryCount());
    Map<Long, CallCallerIdStatsEntity> statsByCaller = callCallerIdStatsRepository.findLatestByCallerIds(
            tenantId,
            candidates.stream().map(CallerIdCandidate::callerIdId).toList(),
            attemptStage.name()
    );
    return selectWithStats(dialUnit, policy, candidates, statsByCaller);
}
```

Keep `select(...)` for compatibility so current callers and tests continue working.

**Step 4: Run test to verify it passes**

Run: `mvn -pl call-task -Dtest=CallerIdSelectorTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add call-task/src/main/java/com/callcenter/task/caller/CallerIdSelector.java \
  call-task/src/test/java/com/callcenter/task/caller/CallerIdSelectorTest.java
git commit -m "task: extract pure caller id selector path"
```

### Task 2: Add Worker Tests For Batch Stats Prefetch

**Files:**
- Modify: `call-task/src/test/java/com/callcenter/task/dispatch/PartitionSchedulerWorkerTest.java`

**Step 1: Write the failing test**

Add a test that mixes first-attempt and retry units in one batch and asserts stats are fetched once per stage instead of once per unit:

```java
@Test
void shouldBatchLoadCallerStatsByAttemptStageBeforeSelecting() {
    Fixture fixture = new Fixture();
    fixture.properties.setDispatchBatchSize(3);
    when(fixture.concurrencyLimiter.tryAcquireBatch(9L, 1001L, 20, 3)).thenReturn(3);
    when(fixture.queue.claimReady(eq(9L), eq(1001L), eq(1), eq(3), any())).thenReturn(List.of(11L, 12L, 13L));
    when(fixture.dialUnitRepository.listByTaskIdAndIds(any(), eq(1001L), eq(List.of(11L, 12L, 13L))))
            .thenReturn(List.of(unitWithRetry(11L, 0), unitWithRetry(12L, 1), unitWithRetry(13L, 1)));
    when(fixture.candidateService.listCandidates(eq(9L), eq(1001L), any(), any()))
            .thenReturn(List.of(candidate(3001L, "02166668888")));
    when(fixture.selector.selectWithStats(any(), any(), any(), any()))
            .thenReturn(Optional.of(selection(3001L, "02166668888")));
    when(fixture.statsRepository.findLatestByCallerIds(9L, List.of(3001L), "FIRST_ATTEMPT"))
            .thenReturn(Map.of(3001L, stats(3001L, "FIRST_ATTEMPT", 10L, 7L, 5L, 300L)));
    when(fixture.statsRepository.findLatestByCallerIds(9L, List.of(3001L), "RETRY_ATTEMPT"))
            .thenReturn(Map.of(3001L, stats(3001L, "RETRY_ATTEMPT", 10L, 6L, 4L, 180L)));

    assertTrue(fixture.worker().runPartition(7));

    verify(fixture.statsRepository, times(1)).findLatestByCallerIds(9L, List.of(3001L), "FIRST_ATTEMPT");
    verify(fixture.statsRepository, times(1)).findLatestByCallerIds(9L, List.of(3001L), "RETRY_ATTEMPT");
    verify(fixture.selector, times(3)).selectWithStats(any(), any(), any(), any());
    verify(fixture.selector, never()).select(eq(9L), any(), any(), any());
}
```

Also update existing worker tests to stub `selectWithStats(...)` instead of `select(...)` after the worker changes.

**Step 2: Run test to verify it fails**

Run: `mvn -pl call-task -Dtest=PartitionSchedulerWorkerTest test`

Expected: FAIL because the worker still calls `selector.select(...)` for each unit and does not depend on `CallCallerIdStatsRepository` directly.

**Step 3: Write minimal implementation**

Modify `call-task/src/main/java/com/callcenter/task/dispatch/PartitionSchedulerWorker.java` constructor to inject `CallCallerIdStatsRepository`.

Add private helpers like:

```java
private Map<AttemptStage, Map<Long, CallCallerIdStatsEntity>> preloadStatsByStage(
        Long tenantId,
        List<CallDialUnitEntity> units,
        List<CallerIdCandidate> candidates
) {
    List<Long> callerIds = candidates.stream().map(CallerIdCandidate::callerIdId).distinct().toList();
    return units.stream()
            .map(unit -> AttemptStage.fromRetryCount(unit.getRetryCount()))
            .distinct()
            .collect(Collectors.toMap(
                    stage -> stage,
                    stage -> callCallerIdStatsRepository.findLatestByCallerIds(tenantId, callerIds, stage.name())
            ));
}

private Optional<CallerIdSelection> selectWithPreloadedStats(
        Long tenantId,
        CallDialUnitEntity unit,
        TaskCallerIdPolicy policy,
        List<CallerIdCandidate> candidates,
        Map<AttemptStage, Map<Long, CallCallerIdStatsEntity>> statsByStage
) {
    AttemptStage stage = AttemptStage.fromRetryCount(unit.getRetryCount());
    return callerIdSelector.selectWithStats(unit, policy, candidates, statsByStage.getOrDefault(stage, Map.of()));
}
```

Replace the per-unit `selector.select(...)` call with the preloaded path:

```java
Map<AttemptStage, Map<Long, CallCallerIdStatsEntity>> statsByStage =
        preloadStatsByStage(task.getTenantId(), claimedUnits, candidates);

for (CallDialUnitEntity unit : claimedUnits) {
    Optional<CallerIdSelection> selection = selectWithPreloadedStats(
            task.getTenantId(),
            unit,
            policy,
            candidates,
            statsByStage
    );
    ...
}
```

**Step 4: Run test to verify it passes**

Run: `mvn -pl call-task -Dtest=PartitionSchedulerWorkerTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add call-task/src/main/java/com/callcenter/task/dispatch/PartitionSchedulerWorker.java \
  call-task/src/test/java/com/callcenter/task/dispatch/PartitionSchedulerWorkerTest.java
git commit -m "task: batch preload caller stats in dispatch worker"
```

### Task 3: Keep Scheduler And Async Dispatch Boundaries Explicit

**Files:**
- Modify: `call-task/src/test/java/com/callcenter/task/dispatch/PartitionSchedulerWorkerTest.java`
- Modify: `call-task/src/test/java/com/callcenter/task/dispatch/AsyncDialDispatchServiceTest.java` or create if absent

**Step 1: Write the failing test**

Add a regression test proving caller selection still happens before async submit:

```java
@Test
void shouldSubmitOnlyUnitsThatAlreadyContainSelectedCallerFields() {
    Fixture fixture = new Fixture();
    ...

    assertTrue(fixture.worker().runPartition(7));

    verify(fixture.asyncDialDispatchService).submit(
            any(),
            argThat(unit -> unit.getDispatchToken() != null
                    && unit.getSelectedCallerId() != null
                    && unit.getSelectedCallerNumber() != null
                    && unit.getAttemptStage() != null)
    );
}
```

If `AsyncDialDispatchServiceTest` does not exist, add a focused test that `submit(...)` still only publishes and compensates, and does not gain any selector dependency.

**Step 2: Run test to verify it fails**

Run: `mvn -pl call-task -Dtest=PartitionSchedulerWorkerTest,AsyncDialDispatchServiceTest test`

Expected: FAIL if the worker changes are incomplete or if a new async-selection coupling was accidentally introduced.

**Step 3: Write minimal implementation**

Do not move any selection code into `AsyncDialDispatchService`.

Keep this contract unchanged:

```java
for (CallDialUnitEntity unit : units) {
    asyncDialDispatchService.submit(shardKey, unit);
}
```

The only code change in this task should be test-only or assertion-only cleanup required by the new selector path.

**Step 4: Run test to verify it passes**

Run: `mvn -pl call-task -Dtest=PartitionSchedulerWorkerTest,AsyncDialDispatchServiceTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add call-task/src/test/java/com/callcenter/task/dispatch/PartitionSchedulerWorkerTest.java \
  call-task/src/test/java/com/callcenter/task/dispatch/AsyncDialDispatchServiceTest.java
git commit -m "task: lock caller selection before async dispatch"
```

### Task 4: Verify End-To-End Behavior

**Files:**
- Modify as needed based on test fallout

**Step 1: Run focused tests**

Run: `mvn -pl call-task -Dtest=CallerIdSelectorTest,PartitionSchedulerWorkerTest,AsyncDialDispatchServiceTest test`

Expected: PASS

**Step 2: Run repository regression tests**

Run: `mvn -pl call-task -Dtest=CallCallerIdStatsRepositoryTest test`

Expected: PASS

**Step 3: Run broader module verification**

Run: `mvn -pl call-task test`

Expected: PASS

**Step 4: Commit follow-up fixes if needed**

```bash
git add <any adjusted files>
git commit -m "task: verify caller id batch selection optimization"
```

## Notes

- Do not move `callerIdSelector.select(...)` into `AsyncDialDispatchService.submit(...)`.
- Do not introduce a new dispatch state.
- Do not change `DialDispatchCompensationService` semantics.
- Keep any optional CPU parallelism for caller scoring out of this plan; first remove the DB round-trip bottleneck.
