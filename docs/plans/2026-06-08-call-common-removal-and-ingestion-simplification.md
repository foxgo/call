# Call Common Removal And Ingestion Simplification Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Remove `call-common`, introduce focused shared modules, and simplify `call-ingestion` into a pragmatic write-and-postprocess module without changing behavior.

**Architecture:** The refactor proceeds in four passes. First create `call-kernel` and `call-persistence` and move stable shared code there. Then migrate business-owned code out of `call-common` into `call-ingestion`, `call-task`, and `call-search`. After ownership is correct, flatten `call-ingestion` into workflow-oriented packages. Finally delete `call-common`, fix module imports, and run focused verification across the affected services.

**Tech Stack:** Java 21, Maven multi-module build, Spring Boot 3.2, MyBatis-Plus, MySQL, RocketMQ, Elasticsearch Java client, JUnit 5, Mockito, Testcontainers

---

### Task 1: Create `call-kernel` and move pure foundation code

**Files:**
- Create: `call-kernel/pom.xml`
- Create: `call-kernel/src/main/java/com/callcenter/kernel/config/CallIdProperties.java`
- Create: `call-kernel/src/main/java/com/callcenter/kernel/util/HashUtil.java`
- Modify: `pom.xml`

**Step 1: Write the failing compile target**

Run: `mvn -pl call-kernel -DskipTests compile`

Expected: FAIL because the new module and files do not exist yet.

**Step 2: Add the new module and copy exact implementations**

- Add `call-kernel` to the root `pom.xml` module list.
- Copy the foundation classes from `call-common`.
- Keep logic unchanged apart from package names.

**Step 3: Run compile to verify it passes**

Run: `mvn -pl call-kernel -am -DskipTests compile`

Expected: PASS.

### Task 2: Create `call-persistence` and move sharding plus MySQL support

**Files:**
- Create: `call-persistence/pom.xml`
- Create: `call-persistence/src/main/java/com/callcenter/persistence/config/ShardProperties.java`
- Create: `call-persistence/src/main/java/com/callcenter/persistence/config/CallDatasourceProperties.java`
- Create: `call-persistence/src/main/java/com/callcenter/persistence/config/MybatisPlusConfig.java`
- Create: `call-persistence/src/main/java/com/callcenter/persistence/config/RoutingDataSourceConfig.java`
- Create: `call-persistence/src/main/java/com/callcenter/persistence/context/DbRouteContextHolder.java`
- Create: `call-persistence/src/main/java/com/callcenter/persistence/context/ShardContextHolder.java`
- Create: `call-persistence/src/main/java/com/callcenter/persistence/route/ShardContext.java`
- Create: `call-persistence/src/main/java/com/callcenter/persistence/route/ShardKey.java`
- Create: `call-persistence/src/main/java/com/callcenter/persistence/route/ShardingRouter.java`
- Create: `call-persistence/src/main/java/com/callcenter/persistence/util/ShardedSnowflakeIdGenerator.java`
- Modify: `pom.xml`
- Modify: `call-ingestion/pom.xml`
- Modify: `call-task/pom.xml`
- Modify: `call-iam/pom.xml`

**Step 1: Write the failing compile target**

Run: `mvn -pl call-persistence -DskipTests compile`

Expected: FAIL because the module does not exist yet.

**Step 2: Add the module and move persistence support classes**

- Add `call-persistence` to the root `pom.xml`.
- Copy the listed sharding and MySQL support classes from `call-common`.
- Move `ShardedSnowflakeIdGenerator` here so it can continue to depend on shard settings without reversing module boundaries.
- Keep behavior unchanged.

**Step 3: Rewrite imports in dependent modules**

- Update `call-ingestion`, `call-task`, and `call-iam` imports from `com.callcenter.common.*` to `com.callcenter.persistence.*`.
- Add the new dependency in each affected module `pom.xml`.

**Step 4: Run compile to verify it passes**

Run: `mvn -pl call-persistence,call-ingestion,call-task,call-iam -am -DskipTests compile`

Expected: PASS.

### Task 3: Move `call-ingestion`-owned models and persistence types out of `call-common`

**Files:**
- Modify: `call-ingestion/src/main/java/com/callcenter/ingestion/**/*.java`
- Modify: `call-ingestion/src/test/java/com/callcenter/ingestion/**/*.java`
- Create: `call-ingestion/src/main/java/com/callcenter/ingestion/model/**/*.java`
- Create: `call-ingestion/src/main/java/com/callcenter/ingestion/repository/**/*.java`
- Modify: `call-ingestion/pom.xml`

**Step 1: Write the failing import search**

Run: `rg -n "com\\.callcenter\\.common\\." call-ingestion/src/main/java call-ingestion/src/test/java`

Expected: Matches for ingestion-owned models, entities, mappers, and config imports.

**Step 2: Move ingestion-owned code into `call-ingestion`**

Move these types into `call-ingestion` with package-only adjustments:

- `CallRecordMessage`
- `CallRoundMessage`
- `DomainEventMessage`
- `AnalysisResultStatus`
- `CallAnalysisResultEntity`
- `CallAnalysisResultMapper`
- `CallRecordEntity`
- `CallRecordMapper`
- `CallRoundEntity`
- `CallRoundMapper`
- `CallEventOutboxEntity`
- `CallEventOutboxMapper`
- `CallDeadLetterTaskEntity`
- `CallDeadLetterTaskMapper`

**Step 3: Rewrite imports**

- Update all main and test imports to the new `call-ingestion` packages.
- Keep contents unchanged.

**Step 4: Run compile to verify it passes**

Run: `mvn -pl call-ingestion -am -DskipTests compile`

Expected: PASS.

### Task 4: Move `call-task`-owned entities, enums, and mappers out of `call-common`

**Files:**
- Modify: `call-task/src/main/java/com/callcenter/task/**/*.java`
- Modify: `call-task/src/test/java/com/callcenter/task/**/*.java`
- Create: `call-task/src/main/java/com/callcenter/task/model/**/*.java`
- Create: `call-task/src/main/java/com/callcenter/task/repository/**/*.java`

**Step 1: Write the failing import search**

Run: `rg -n "com\\.callcenter\\.common\\.(entity|mapper|enums)" call-task/src/main/java call-task/src/test/java`

Expected: Matches for task-owned entities, enums, and mappers.

**Step 2: Move task-owned code into `call-task`**

Move these families into `call-task`:

- `CallTask*`
- `CallDialUnit*`
- `CallCallerId*`
- `CallTaskCallerIdBinding*`
- task-related enums
- corresponding Mapper interfaces

**Step 3: Rewrite imports**

- Update all main and test imports to the new `call-task` packages.
- Keep repository behavior unchanged.

**Step 4: Run focused tests**

Run: `mvn -pl call-task -Dtest=CallTaskImportServiceTest,CallTaskServiceTest,CallDialUnitRepositoryTest test`

Expected: PASS.

### Task 5: Move search-owned Elasticsearch models and initialization into `call-search`

**Files:**
- Modify: `call-search/src/main/java/com/callcenter/search/**/*.java`
- Create: `call-search/src/main/java/com/callcenter/search/model/**/*.java`
- Create: `call-search/src/main/java/com/callcenter/search/config/**/*.java`
- Create: `call-search/src/main/resources/es/*.json`

**Step 1: Write the failing import search**

Run: `rg -n "com\\.callcenter\\.common\\.(es|initializer|config\\.CallElasticsearchProperties|config\\.ElasticsearchConfig)" call-search/src/main/java call-search/src/test/java`

Expected: Matches for search-owned ES types and config references.

**Step 2: Move search-owned ES code into `call-search`**

- Move ES document models into `call-search`.
- Move index initializer and ES resources into `call-search`.
- If `call-ingestion` also needs its own ES config, duplicate only the config it truly owns instead of reintroducing a shared ES module.

**Step 3: Run focused tests**

Run: `mvn -pl call-search -am -DskipTests compile`

Expected: PASS.

### Task 6: Flatten `call-ingestion` package structure around workflow

**Files:**
- Modify: `call-ingestion/src/main/java/com/callcenter/ingestion/CallIngestionApplication.java`
- Modify: `call-ingestion/src/main/java/com/callcenter/ingestion/**/**/*.java`
- Modify: `call-ingestion/src/test/java/com/callcenter/ingestion/**/**/*.java`

**Step 1: Write the failing structure inventory**

Run: `find call-ingestion/src/main/java/com/callcenter/ingestion -maxdepth 4 -type d | sort`

Expected: Shows the current `application / domain / inbound / infrastructure` hierarchy and many one-class subpackages.

**Step 2: Move classes into the simplified directories**

Target structure:

- `config`
- `consumer`
- `service`
- `repository`
- `postprocess`
- `model`
- `mq`
- `support`

Move classes without changing behavior:

- RocketMQ consumers -> `consumer`
- orchestration services -> `service`
- entities and mappers -> `repository`
- ES write, third-party push, outbox publish -> `postprocess`
- internal message/event/request/response/status types -> `model`
- MQ publisher and listener base classes -> `mq`
- metrics helpers -> `support`

**Step 3: Rewrite tests to match new packages**

- Move or repackage `call-ingestion` tests to track the new layout.
- Keep assertion behavior unchanged.

**Step 4: Run focused tests**

Run: `mvn -pl call-ingestion -Dtest=CallRecordIngestionServiceTest,CallRoundIngestionServiceTest,CallAnalysisResultServiceTest,OutboxPublisherTest,RocketMqRecordDeadLetterConsumerTest test`

Expected: PASS.

### Task 7: Remove `CallCommonAutoConfiguration` imports and wire modules explicitly

**Files:**
- Modify: `call-ingestion/src/main/java/com/callcenter/ingestion/CallIngestionApplication.java`
- Modify: `call-search/src/main/java/com/callcenter/search/CallSearchApplication.java`
- Modify: `call-task/src/main/java/com/callcenter/task/CallTaskApplication.java`
- Modify: `call-iam/src/main/java/com/callcenter/iam/IamApplication.java`

**Step 1: Write the failing search**

Run: `rg -n "CallCommonAutoConfiguration" call-ingestion call-search call-task call-iam`

Expected: Matches in the four application entry points and any leftover imports.

**Step 2: Replace the old import strategy**

- Remove `@Import(CallCommonAutoConfiguration.class)`.
- Enable only the properties/config classes that each module actually needs.
- Prefer local module config over a new umbrella auto-configuration.

**Step 3: Run compile to verify it passes**

Run: `mvn -pl call-ingestion,call-search,call-task,call-iam -am -DskipTests compile`

Expected: PASS.

### Task 8: Delete `call-common` and clean root/module dependencies

**Files:**
- Modify: `pom.xml`
- Modify: `call-ingestion/pom.xml`
- Modify: `call-search/pom.xml`
- Modify: `call-task/pom.xml`
- Modify: `call-iam/pom.xml`
- Delete: `call-common/pom.xml`
- Delete: `call-common/src/main/java/com/callcenter/common/**/*.java`
- Delete: `call-common/src/main/resources/es/*.json`

**Step 1: Write the failing dependency search**

Run: `rg -n "<artifactId>call-common</artifactId>|<module>call-common</module>|com\\.callcenter\\.common\\." pom.xml call-ingestion call-search call-task call-iam`

Expected: Remaining references to the old module before cleanup.

**Step 2: Remove the old module**

- Remove `call-common` from the root module list.
- Remove all `call-common` dependencies.
- Delete the module after all imports are clear.

**Step 3: Run full affected-module verification**

Run: `mvn -pl call-kernel,call-persistence,call-ingestion,call-task,call-search,call-iam -am test`

Expected: PASS.

### Task 9: Final hygiene and documentation check

**Files:**
- Modify: `docs/plans/2026-06-08-call-common-removal-and-ingestion-simplification-design.md`
- Modify: `docs/plans/2026-06-08-call-common-removal-and-ingestion-simplification.md`

**Step 1: Verify the final structure**

Run: `find call-ingestion/src/main/java/com/callcenter/ingestion -maxdepth 2 -type d | sort`

Expected: Only the simplified top-level directories remain.

**Step 2: Verify no old references remain**

Run: `rg -n "com\\.callcenter\\.common\\.|call-common" /Users/johnny/github/call`

Expected: Zero code references, with docs-only mentions acceptable if they describe the migration.

**Step 3: Commit**

```bash
git add pom.xml call-kernel call-persistence call-ingestion call-task call-search call-iam docs/plans
git commit -m "refactor: remove call-common and simplify ingestion"
```
