# Call Task Async Dispatch Compensation Design

## Goal

Move MQ send latency off the `runPartition` hot path while keeping the current scheme-2 scheduler model and adding immediate compensation when a dispatch send fails or returns an unknown result.

## Context

The current scheduler flow in `call-task` is:

1. claim active task
2. preload ready units
3. acquire concurrency quota
4. claim ready ids from Redis
5. mark units `DIALING` in MySQL
6. synchronously publish one MQ message per unit
7. block or reactivate the task

The synchronous MQ call in `DialDispatchPublisher` is now the slowest stage left on the partition hot path. After recent Redis and MySQL optimizations, it is the next bottleneck.

## Constraints

- Keep the current scheme-2 architecture. Do not introduce an outbox or a new dispatch state.
- Keep `PartitionSchedulerWorker.runPartition(...)` as the authoritative serial task-state transition pipeline.
- When MQ send fails or the result is unknown, immediately compensate the unit back to `READY`.
- Do not wait for `DialingRecoveryJob` as the primary recovery path. Recovery remains only as a safety net.

## Options Considered

### Option 1: Lightweight async send with single-unit immediate compensation

`runPartition(...)` still marks units `DIALING`, but instead of calling `syncSend` inline it submits each unit to a dedicated async dispatch service. Each async send either succeeds or triggers immediate compensation for that one unit.

Pros:

- Minimal change to current scheduler flow
- Removes MQ latency from partition drain loop
- Keeps compensation logic localized and testable

Cons:

- Compensation stays per unit, not batched
- Process crash between task submission and actual send is still covered only by timeout recovery

### Option 2: Bounded async batch submission with single-unit compensation

`runPartition(...)` submits a whole batch to a dedicated async sender, but each unit is still independently sent and independently compensated on failure.

Pros:

- Higher throughput than option 1
- Scheduler thread returns even faster

Cons:

- More concurrent pressure on MQ, Redis, and MySQL
- More scheduling complexity without changing correctness semantics

### Option 3: Async send with batched compensation aggregation

Async send still happens per unit, but failures are collected and compensated in grouped task/shard batches.

Pros:

- Lower compensation overhead under burst failures

Cons:

- Noticeably more complex error handling
- Higher implementation and testing cost

## Decision

Choose **Option 1**.

It removes the current MQ bottleneck with the smallest semantic change. The system already has a recovery job for timeout scenarios, so the remaining crash window is acceptable inside scheme 2. The immediate value comes from moving MQ latency off `runPartition(...)` and compensating failed sends right away instead of waiting for timeout recovery.

## Design

### 1. Keep the scheduler state machine unchanged up to `DIALING`

`PartitionSchedulerWorker` continues to:

- acquire quota
- claim ready ids
- mark rows `DIALING`
- compute task block/reactivate decisions

The only change is that it no longer waits synchronously for `DialDispatchPublisher.publish(...)` to finish. Instead, it submits the unit to an async dispatch service.

### 2. Introduce `AsyncDialDispatchService`

New service responsibilities:

- accept a `CallDialUnitEntity` and its `ShardKey`
- submit the send task to a bounded executor
- call `DialDispatchPublisher.publish(...)` from the async thread
- on success, record publish metrics
- on any exception or unknown send result, invoke compensation

This keeps `PartitionSchedulerWorker` free of async and compensation details.

### 3. Add `DialDispatchCompensationService`

New service responsibilities:

- perform immediate compensation for one failed dispatch
- attempt `DIALING -> READY` rollback in MySQL using `taskId + dialUnitId + dispatchToken`
- only when that rollback succeeds:
  - re-offer the unit to Redis ready queue
  - release one quota slot
  - record compensation metrics

If the rollback update affects zero rows, the compensation service must not requeue or release quota. That means another flow has already taken ownership or the state is no longer compatible with rollback.

### 4. Repository support

Add a repository method:

- `revertDialingToReady(ShardKey shardKey, long taskId, long dialUnitId, String dispatchToken, LocalDateTime updatedAt)`

Required update conditions:

- `task_id = ?`
- `id = ?`
- `status = DIALING`
- `dispatch_token = ?`

Updated fields:

- `status = READY`
- `dispatch_token = null`
- `inflight_expire_at = null`
- `updated_at = ?`

This conditional update is the core idempotency guard.

### 5. Metrics

Existing publish success metric should move from scheduler thread submission time to actual send-success time.

Add new compensation counters:

- dispatch send failed
- dispatch compensated
- dispatch compensation skipped

These make async send behavior observable and distinguish transport failures from successful rollback.

## Failure Handling

### Send success

- MQ send returns successfully
- increment dispatch-published metric
- no DB state change

### Send failure

- async sender catches exception
- compensation service attempts rollback
- on successful rollback:
  - Redis ready requeue
  - quota release
  - compensation success metric
- on failed rollback:
  - compensation skipped metric
  - do not double release quota
  - do not requeue

### Send result unknown or client timeout

Treat exactly the same as a send failure. The agreed policy is to prefer immediate recovery over waiting for timeout.

### Process crash

If the process dies after scheduling the async task but before the send or compensation runs, `DialingRecoveryJob` remains the safety net. This is the main residual risk that remains until an outbox-based design is adopted.

## Testing Strategy

Add focused tests for:

- async service submitting and sending successfully
- async service invoking compensation on publish exception
- compensation service requeueing and releasing quota only after successful DB rollback
- scheduler now delegating to async service instead of blocking on publish

Keep module-wide regression tests as the final verification step.

## Non-Goals

- No outbox table
- No `DISPATCHING` intermediate state
- No batch compensation aggregator
- No cross-process durable handoff of pending async sends
