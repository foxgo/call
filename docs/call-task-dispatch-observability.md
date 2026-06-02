# Call Task Dispatch Observability

This document describes the local monitoring assets for `call-task` async dispatch.

## Local stack

`deploy/docker-compose.yml` now includes:

- `call-task` on port `8084`
- Prometheus on port `9090`
- Grafana on port `3000`

Start the local stack:

```bash
docker compose -f deploy/docker-compose.yml up -d --build call-task prometheus grafana
```

Open:

- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3000`
- Grafana login: `admin` / `admin123`

Grafana auto-provisions:

- datasource `Prometheus`
- dashboard `Call Task Dispatch Observability`

## Key metrics

- `call_task_dispatch_send_executor_active`
  Current number of busy async send workers.
- `call_task_dispatch_send_executor_queue_size`
  Current number of queued async send jobs waiting for a worker.
- `call_task_dispatch_published_total`
  Number of successful MQ publishes.
- `call_task_dispatch_send_failed_total`
  Number of publish failures inside the async worker.
- `call_task_dispatch_send_rejected_total`
  Number of executor submission rejections before publish starts.
- `call_task_dispatch_compensated_total`
  Number of dispatch units compensated back to `READY`.

## Dashboard intent

Use the dashboard panels together:

- `Active Workers` tells you whether the send pool is saturated.
- `Queue Size` tells you whether publish throughput is lagging behind dispatch throughput.
- `Published (5m)` is the current successful send volume.
- `Send Failed (5m)` and `Send Rejected (5m)` separate MQ failure from thread-pool saturation.
- `Compensated (5m)` shows user-visible rollback pressure.

Typical reading patterns:

- `active` near pool size and `queue size` rising:
  send workers are the bottleneck.
- `send failed` rising while queue is flat:
  MQ or broker path is unstable.
- `rejected` rising:
  the send executor is already full and dispatch is outrunning publish.
- `compensated` rising with `failed` or `rejected`:
  rollback is actively protecting units from getting stuck in `DIALING`.

## Prometheus alerts

The local Prometheus rules file is `deploy/docker/prometheus/rules/call-task-dispatch.yml`.

It defines:

- `CallTaskDispatchSendExecutorQueueBacklog`
  Fires when queue size stays non-zero for 5 minutes.
- `CallTaskDispatchSendRejected`
  Fires when any async send submission is rejected in the last 5 minutes.
- `CallTaskDispatchCompensationBurst`
  Fires when compensation volume reaches 10 or more in 5 minutes for 5 consecutive minutes.

## Operational notes

- Queue backlog is an early warning. Rejections mean the bounded executor is already at its limit.
- Compensation is expected during isolated failures. Sustained compensation means publish capacity or MQ stability should be investigated.
- The current rules are intentionally conservative and local-default oriented. Production thresholds should be tuned to real task volume and `dispatch-send-parallelism`.
