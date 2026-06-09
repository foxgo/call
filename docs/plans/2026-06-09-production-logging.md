# Production Logging Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build a production-grade structured logging foundation with mandatory sensitive-data masking and roll it out across `call-iam`, `call-search`, `call-task`, and `call-ingestion`.

**Architecture:** Add a new shared module `call-observability` that owns JSON logging configuration, request and MQ context propagation, MDC utilities, and log masking. Then wire the four backend services to that module, update async executors and request filters to propagate context, and finally add high-value structured event logs to the main operational flows.

**Tech Stack:** Java 21, Maven multi-module build, Spring Boot 3.2, Logback, logstash-logback-encoder, Micrometer Tracing, Spring Web, Spring Security, RocketMQ, JUnit 5, Mockito

---

### Task 1: Create `call-observability` and add shared logging dependencies

**Files:**
- Create: `call-observability/pom.xml`
- Modify: `pom.xml`
- Modify: `call-iam/pom.xml`
- Modify: `call-search/pom.xml`
- Modify: `call-task/pom.xml`
- Modify: `call-ingestion/pom.xml`

**Step 1: Write the failing compile target**

Run: `mvn -pl call-observability -DskipTests compile`
Expected: FAIL because the module does not exist yet.

**Step 2: Add the module to the root build**

- Add `call-observability` to the root `pom.xml` module list.
- Create `call-observability/pom.xml`.
- Add minimal dependencies:
  - `spring-boot-autoconfigure`
  - `spring-boot-starter-web`
  - `spring-boot-starter-aop`
  - `micrometer-tracing-bridge-otel`
  - `logstash-logback-encoder`
  - test dependencies matching the repo standard

**Step 3: Add service dependencies**

- Add `call-observability` as a dependency in:
  - `call-iam/pom.xml`
  - `call-search/pom.xml`
  - `call-task/pom.xml`
  - `call-ingestion/pom.xml`

**Step 4: Run compile to verify it passes**

Run: `mvn -pl call-observability,call-iam,call-search,call-task,call-ingestion -am -DskipTests compile`
Expected: PASS.

### Task 2: Add logging properties, field constants, and masking utilities

**Files:**
- Create: `call-observability/src/main/java/com/callcenter/observability/logging/config/LoggingProperties.java`
- Create: `call-observability/src/main/java/com/callcenter/observability/logging/StructuredLogFields.java`
- Create: `call-observability/src/main/java/com/callcenter/observability/logging/LogSanitizer.java`
- Create: `call-observability/src/test/java/com/callcenter/observability/logging/LogSanitizerTest.java`

**Step 1: Write the failing tests**

Create tests covering:

- full masking for `password`, `token`, `authorization`, `cookie`
- partial masking for phone numbers
- partial masking for ID card numbers
- nested `Map` / `List` masking

**Step 2: Run test to verify it fails**

Run: `mvn -pl call-observability -Dtest=LogSanitizerTest test`
Expected: FAIL because the classes do not exist yet.

**Step 3: Write the minimal implementation**

- Add `LoggingProperties` with explicit masking configuration.
- Add `StructuredLogFields` constants for shared field names.
- Implement `LogSanitizer` to sanitize:
  - field-name based values
  - nested map/list structures
  - raw text fallbacks such as `Bearer xxx`, `token=...`, phone patterns

**Step 4: Run test to verify it passes**

Run: `mvn -pl call-observability -Dtest=LogSanitizerTest test`
Expected: PASS.

### Task 3: Add shared MDC context helpers and auto-configuration

**Files:**
- Create: `call-observability/src/main/java/com/callcenter/observability/logging/config/LoggingAutoConfiguration.java`
- Create: `call-observability/src/main/java/com/callcenter/observability/logging/MdcUtils.java`
- Create: `call-observability/src/main/java/com/callcenter/observability/logging/MdcTaskDecorator.java`
- Create: `call-observability/src/test/java/com/callcenter/observability/logging/MdcTaskDecoratorTest.java`
- Create: `call-observability/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

**Step 1: Write the failing test**

Create a test that captures MDC values before submitting a task and asserts they are visible inside the decorated task.

**Step 2: Run test to verify it fails**

Run: `mvn -pl call-observability -Dtest=MdcTaskDecoratorTest test`
Expected: FAIL because the decorator does not exist yet.

**Step 3: Write the minimal implementation**

- Add `MdcUtils` helpers to set, copy, and clear common MDC fields.
- Implement `MdcTaskDecorator` using MDC snapshot copy/restore.
- Register beans from `LoggingAutoConfiguration`.
- Export the auto-configuration through `AutoConfiguration.imports`.

**Step 4: Run test to verify it passes**

Run: `mvn -pl call-observability -Dtest=MdcTaskDecoratorTest test`
Expected: PASS.

### Task 4: Add HTTP request logging and request ID propagation

**Files:**
- Create: `call-observability/src/main/java/com/callcenter/observability/logging/http/RequestLoggingFilter.java`
- Create: `call-observability/src/test/java/com/callcenter/observability/logging/http/RequestLoggingFilterTest.java`
- Modify: `call-observability/src/main/java/com/callcenter/observability/logging/config/LoggingAutoConfiguration.java`

**Step 1: Write the failing integration-style test**

Test cases:

- request without `X-Request-Id` gets one generated
- request with `X-Request-Id` keeps the same value
- response contains the effective request ID header
- MDC contains `requestId`, `httpMethod`, and `httpPath`

**Step 2: Run test to verify it fails**

Run: `mvn -pl call-observability -Dtest=RequestLoggingFilterTest test`
Expected: FAIL because the filter does not exist yet.

**Step 3: Write the minimal implementation**

- Implement `RequestLoggingFilter` as a `OncePerRequestFilter`.
- Generate or propagate `X-Request-Id`.
- Write request context into MDC before the chain.
- Clear MDC after the chain.
- Keep request-body logging disabled by default.

**Step 4: Run test to verify it passes**

Run: `mvn -pl call-observability -Dtest=RequestLoggingFilterTest test`
Expected: PASS.

### Task 5: Add RocketMQ logging context support

**Files:**
- Create: `call-observability/src/main/java/com/callcenter/observability/logging/mq/MqLoggingContext.java`
- Create: `call-observability/src/test/java/com/callcenter/observability/logging/mq/MqLoggingContextTest.java`

**Step 1: Write the failing tests**

Cover:

- topic, message ID, queue metadata, and reconsume times enter MDC
- cleanup removes those values after handling

**Step 2: Run test to verify it fails**

Run: `mvn -pl call-observability -Dtest=MqLoggingContextTest test`
Expected: FAIL because the helper does not exist yet.

**Step 3: Write the minimal implementation**

- Implement a helper that accepts `MessageExt`.
- Populate MDC with:
  - `topic`
  - `messageId`
  - `messageKeys`
  - `queueId`
  - `queueOffset`
  - `reconsumeTimes`
- Provide a matching clear method.

**Step 4: Run test to verify it passes**

Run: `mvn -pl call-observability -Dtest=MqLoggingContextTest test`
Expected: PASS.

### Task 6: Add shared Logback JSON configuration and service-level logging settings

**Files:**
- Create: `call-observability/src/main/resources/logback/call-json-appender.xml`
- Create: `call-observability/src/main/resources/logback/call-console-appender.xml`
- Create: `call-observability/src/main/resources/logback/base-logback-spring.xml`
- Create: `call-iam/src/main/resources/logback-spring.xml`
- Create: `call-search/src/main/resources/logback-spring.xml`
- Create: `call-task/src/main/resources/logback-spring.xml`
- Create: `call-ingestion/src/main/resources/logback-spring.xml`
- Modify: `call-iam/src/main/resources/application.yml`
- Modify: `call-search/src/main/resources/application.yml`
- Modify: `call-task/src/main/resources/application.yml`
- Modify: `call-ingestion/src/main/resources/application.yml`

**Step 1: Write the failing verification target**

Run: `rg -n "logging:\\s*$|pattern:\\s*console" call-iam/src/main/resources/application.yml call-search/src/main/resources/application.yml call-task/src/main/resources/application.yml call-ingestion/src/main/resources/application.yml`
Expected: Existing matches showing per-service console pattern drift.

**Step 2: Add shared logback fragments**

- Create reusable JSON and console appender fragments under `call-observability`.
- Ensure JSON output includes:
  - timestamp
  - level
  - service
  - env
  - traceId
  - spanId
  - requestId
  - thread
  - logger
  - message
- Add a masking decorator or provider that applies the fallback sanitizer.

**Step 3: Add per-service `logback-spring.xml`**

- Each service should import the shared base configuration.
- Production defaults to JSON output.
- Development can keep readable console output if controlled by Spring profile.

**Step 4: Remove ad hoc console pattern config**

- Delete service-local `logging.pattern.console` entries from `application.yml`.

**Step 5: Run compile to verify it passes**

Run: `mvn -pl call-observability,call-iam,call-search,call-task,call-ingestion -am -DskipTests compile`
Expected: PASS.

### Task 7: Wire MDC propagation into `call-task` executors

**Files:**
- Modify: `call-task/src/main/java/com/callcenter/task/config/DispatchExecutorConfiguration.java`
- Create: `call-task/src/test/java/com/callcenter/task/config/DispatchExecutorConfigurationTest.java`

**Step 1: Write the failing test**

Create a test that gets the configured executor bean, submits a task after setting MDC values, and asserts the task sees the same values.

**Step 2: Run test to verify it fails**

Run: `mvn -pl call-task -Dtest=DispatchExecutorConfigurationTest test`
Expected: FAIL because the executors do not use the decorator yet.

**Step 3: Write the minimal implementation**

- Inject `MdcTaskDecorator` into `DispatchExecutorConfiguration`.
- Apply it to:
  - `callTaskDispatchExecutor`
  - `callTaskDispatchSendExecutor`

**Step 4: Run test to verify it passes**

Run: `mvn -pl call-task -Dtest=DispatchExecutorConfigurationTest test`
Expected: PASS.

### Task 8: Add structured exception logging to `call-iam`

**Files:**
- Modify: `call-iam/src/main/java/com/callcenter/iam/interfaces/rest/support/GlobalExceptionHandler.java`
- Create: `call-iam/src/test/java/com/callcenter/iam/interfaces/rest/support/GlobalExceptionHandlerTest.java`

**Step 1: Write the failing test**

Create tests that invoke the advice and assert:

- the HTTP response status and body stay unchanged
- an error log is emitted with structured fields such as `event`, `requestId`, and error class

**Step 2: Run test to verify it fails**

Run: `mvn -pl call-iam -Dtest=GlobalExceptionHandlerTest test`
Expected: FAIL because no structured exception logging exists yet.

**Step 3: Write the minimal implementation**

- Add a logger to `GlobalExceptionHandler`.
- Log before returning the response.
- Include safe fields only.
- Do not log request body or authorization header values.

**Step 4: Run test to verify it passes**

Run: `mvn -pl call-iam -Dtest=GlobalExceptionHandlerTest test`
Expected: PASS.

### Task 9: Add user and tenant MDC population in `call-iam`

**Files:**
- Modify: `call-iam/src/main/java/com/callcenter/iam/infrastructure/security/JwtAuthenticationFilter.java`
- Create: `call-iam/src/test/java/com/callcenter/iam/infrastructure/security/JwtAuthenticationFilterTest.java`

**Step 1: Write the failing test**

Cover:

- valid bearer token sets authentication as before
- MDC gets `userId` and `tenantId`
- invalid bearer token clears security context and does not leave stale MDC values

**Step 2: Run test to verify it fails**

Run: `mvn -pl call-iam -Dtest=JwtAuthenticationFilterTest test`
Expected: FAIL because the filter does not manage MDC yet.

**Step 3: Write the minimal implementation**

- After parsing claims, write `userId` and `tenantId` into MDC.
- Ensure cleanup on invalid token paths and at filter completion.

**Step 4: Run test to verify it passes**

Run: `mvn -pl call-iam -Dtest=JwtAuthenticationFilterTest test`
Expected: PASS.

### Task 10: Add RocketMQ MDC setup in `call-ingestion` consumers

**Files:**
- Modify: `call-ingestion/src/main/java/com/callcenter/ingestion/consumer/RocketMqCallRecordConsumer.java`
- Modify: `call-ingestion/src/main/java/com/callcenter/ingestion/consumer/RocketMqCallRoundConsumer.java`
- Modify: `call-ingestion/src/main/java/com/callcenter/ingestion/consumer/RocketMqCallAnalysisConsumer.java`
- Modify: `call-ingestion/src/main/java/com/callcenter/ingestion/consumer/RocketMqRecordIndexConsumer.java`
- Modify: `call-ingestion/src/main/java/com/callcenter/ingestion/consumer/*DeadLetterConsumer.java`
- Create: `call-ingestion/src/test/java/com/callcenter/ingestion/consumer/RocketMqCallRecordConsumerTest.java`

**Step 1: Write the failing test**

Add or extend a consumer test to assert that:

- MDC is populated during handling
- MDC is cleared afterwards

**Step 2: Run test to verify it fails**

Run: `mvn -pl call-ingestion -Dtest=RocketMqCallRecordConsumerTest test`
Expected: FAIL because the consumer does not use shared MQ logging context.

**Step 3: Write the minimal implementation**

- Wrap consumer handling with `MqLoggingContext`.
- Preserve current behavior for retries and exception wrapping.

**Step 4: Run test to verify it passes**

Run: `mvn -pl call-ingestion -Dtest=RocketMqCallRecordConsumerTest test`
Expected: PASS.

### Task 11: Add structured failure logs to `call-ingestion` write paths

**Files:**
- Modify: `call-ingestion/src/main/java/com/callcenter/ingestion/service/CallRecordIngestionService.java`
- Modify: `call-ingestion/src/main/java/com/callcenter/ingestion/service/CallRoundIngestionService.java`
- Modify: `call-ingestion/src/main/java/com/callcenter/ingestion/service/CallAnalysisResultService.java`
- Create: `call-ingestion/src/test/java/com/callcenter/ingestion/service/CallRecordIngestionServiceTest.java`

**Step 1: Write the failing test**

Create or extend tests to verify:

- service still returns `false` on transient failure paths where required
- failure logs include `event`, `tenantId`, `callId`, and safe error details

**Step 2: Run test to verify it fails**

Run: `mvn -pl call-ingestion -Dtest=CallRecordIngestionServiceTest test`
Expected: FAIL because the service currently swallows exceptions without structured logging.

**Step 3: Write the minimal implementation**

- Add structured error logging in the catch blocks.
- Include safe business keys and exception class.
- Do not serialize the full payload blindly.

**Step 4: Run test to verify it passes**

Run: `mvn -pl call-ingestion -Dtest=CallRecordIngestionServiceTest test`
Expected: PASS.

### Task 12: Normalize `call-task` operational event logs

**Files:**
- Modify: `call-task/src/main/java/com/callcenter/task/dispatch/AsyncDialDispatchService.java`
- Modify: `call-task/src/main/java/com/callcenter/task/dispatch/CallTaskDispatcher.java`
- Create: `call-task/src/test/java/com/callcenter/task/dispatch/AsyncDialDispatchServiceTest.java`

**Step 1: Write the failing test**

Cover representative failure cases:

- executor rejection
- dispatch gate rejection
- publish exception

Assert that emitted logs carry stable event names and structured identifiers.

**Step 2: Run test to verify it fails**

Run: `mvn -pl call-task -Dtest=AsyncDialDispatchServiceTest test`
Expected: FAIL because current logs are message-template based and not normalized.

**Step 3: Write the minimal implementation**

- Add stable event naming such as:
  - `dispatch_submission_rejected`
  - `dispatch_gate_rejected`
  - `dispatch_publish_failed`
- Keep existing metrics and compensation behavior unchanged.

**Step 4: Run test to verify it passes**

Run: `mvn -pl call-task -Dtest=AsyncDialDispatchServiceTest test`
Expected: PASS.

### Task 13: Add structured query failure logging to `call-search`

**Files:**
- Modify: `call-search/src/main/java/com/callcenter/search/service/CallRecordQueryService.java`
- Create: `call-search/src/test/java/com/callcenter/search/service/CallRecordQueryServiceTest.java`

**Step 1: Write the failing test**

Add a test covering Elasticsearch query failure and assert a structured error log includes:

- `event`
- `tenantId`
- query type
- safe timing / exception information

**Step 2: Run test to verify it fails**

Run: `mvn -pl call-search -Dtest=CallRecordQueryServiceTest test`
Expected: FAIL because failure logging is not standardized yet.

**Step 3: Write the minimal implementation**

- Normalize search failure logs.
- Keep request payload logging summarized and sanitized.

**Step 4: Run test to verify it passes**

Run: `mvn -pl call-search -Dtest=CallRecordQueryServiceTest test`
Expected: PASS.

### Task 14: Run focused multi-module verification

**Files:**
- Modify: `docs/plans/2026-06-09-production-logging.md`

**Step 1: Run module test suites for the new logging foundation**

Run: `mvn -pl call-observability,call-iam,call-search,call-task,call-ingestion -am test`
Expected: PASS.

**Step 2: Run a final grep for unsafe config and likely sensitive literals**

Run: `rg -n "logging:\\s*$|pattern:\\s*console|Authorization|password=|token=" call-iam call-search call-task call-ingestion call-observability`
Expected: Remaining matches are either tests, safe constant names, or sanitization code, not production raw logging.

**Step 3: Record the verification result in the plan**

- Update the plan notes or execution log with the exact commands that passed.

**Step 4: Commit**

```bash
git add pom.xml call-observability call-iam call-search call-task call-ingestion docs/plans/2026-06-09-production-logging-design.md docs/plans/2026-06-09-production-logging.md
git commit -m "observability: add production logging foundation"
```
