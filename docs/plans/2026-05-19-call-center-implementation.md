# Call Center Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build a production-ready multi-module Spring Boot skeleton for post-call processing, including shared sharding infrastructure, write/query services, and container deployment assets.

**Architecture:** Use a Maven parent project with `call-common`, `call-ingestion`, `call-search`, and `call-ops`. Keep shared routing, MyBatis-Plus, ES, ID generation, and DTO logic in `call-common`; implement the runtime write pipeline in `call-ingestion`; expose reserved query APIs in `call-search`; package deployment assets at the repository root.

**Tech Stack:** Java 21, Spring Boot 3.2, Spring Kafka, MyBatis-Plus, MySQL 8, Elasticsearch 8, Redis 7, Docker Compose, Kubernetes YAML.

---

### Task 1: Scaffold the repository

**Files:**
- Create: `pom.xml`
- Create: `README.md`
- Create: `.gitignore`
- Create: `docs/plans/2026-05-19-call-center-design.md`
- Create: `docs/plans/2026-05-19-call-center-implementation.md`

**Step 1: Write the module layout**

Create the parent Maven project and declare the four modules.

**Step 2: Add repo docs**

Document the architecture and local startup flow.

### Task 2: Build shared infrastructure

**Files:**
- Create: `call-common/pom.xml`
- Create: `call-common/src/main/java/com/callcenter/common/**`
- Create: `call-common/src/main/resources/es/**`

**Step 1: Define DTOs and entities**

Create call record / round DTOs and MyBatis entities.

**Step 2: Implement sharding primitives**

Add shard context, router, shard key, and ID generator.

**Step 3: Add infrastructure configuration**

Configure MyBatis-Plus dynamic table naming, Elasticsearch client, routing datasource support, and ES bootstrapper.

### Task 3: Build the write service

**Files:**
- Create: `call-ingestion/pom.xml`
- Create: `call-ingestion/src/main/java/com/callcenter/ingestion/**`
- Create: `call-ingestion/src/main/resources/application.yml`

**Step 1: Add application bootstrap and configs**

Enable mapper scanning, configuration properties, Kafka batch listener, and virtual-thread executor.

**Step 2: Implement processors and persistence services**

Add grouped MySQL writes, ES bulk indexing, retry execution, and DLQ publishing.

**Step 3: Add topic consumers**

Create record and round listeners using manual ack.

### Task 4: Build the query service

**Files:**
- Create: `call-search/pom.xml`
- Create: `call-search/src/main/java/com/callcenter/search/**`
- Create: `call-search/src/main/resources/application.yml`

**Step 1: Add bootstrap**

Set up the query service with shared config.

**Step 2: Add reserved API**

Expose `GET /api/v1/{tenantId}/call-records` with request model and stub response.

### Task 5: Add operations and deployment assets

**Files:**
- Create: `call-ops/**`
- Create: `docker-compose.yml`
- Create: `docker/**`
- Create: `k8s/**`

**Step 1: Add service Dockerfiles**

Build the write/query/ops modules with multi-stage images.

**Step 2: Add local infrastructure**

Compose MySQL, Redis, Kafka, Elasticsearch, Kibana, Prometheus, Grafana, and both services.

**Step 3: Add cluster manifests**

Provide baseline Kubernetes deployment and config templates.

### Task 6: Verify build health

**Files:**
- Modify: project files as needed after verification

**Step 1: Run compilation**

Run `mvn test` or `mvn -DskipTests package` depending on dependency availability.

**Step 2: Fix scaffold issues**

Resolve missing imports, Spring config errors, and resource issues.

**Step 3: Record residual gaps**

Document any runtime-only gaps that require external systems.
