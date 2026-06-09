# RocketMQ Dashboard Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a minimal RocketMQ Dashboard service to the local Docker Compose deployment.

**Architecture:** Extend `deploy/docker-compose.yml` with a new stateless `rocketmq-dashboard` service that connects to the existing RocketMQ nameserver via `JAVA_OPTS`. No application modules or broker settings change.

**Tech Stack:** Docker Compose, RocketMQ Dashboard, existing Apache RocketMQ 5.3.2 local stack

---

### Task 1: Add the dashboard service

**Files:**
- Modify: `deploy/docker-compose.yml`

**Step 1: Add the service definition**

- Add `rocketmq-dashboard` near the existing RocketMQ services.
- Use `apacherocketmq/rocketmq-dashboard:latest`.
- Set `depends_on` for `rocketmq-nameserver` and `rocketmq-broker`.
- Set `JAVA_OPTS=-Drocketmq.namesrv.addr=rocketmq-nameserver:9876 -Dcom.rocketmq.sendMessageWithVIPChannel=false`.
- Publish the UI port to the host.

**Step 2: Validate the compose file**

Run: `docker compose -f deploy/docker-compose.yml config`
Expected: Compose renders successfully with the new `rocketmq-dashboard` service.

### Task 2: Confirm local usage

**Files:**
- No additional files

**Step 1: Start the dashboard**

Run: `docker compose -f deploy/docker-compose.yml up -d rocketmq-dashboard`
Expected: Container starts after RocketMQ services are available.

**Step 2: Open the UI**

Visit: `http://localhost:8080`
Expected: RocketMQ Dashboard loads and shows the connected cluster.
