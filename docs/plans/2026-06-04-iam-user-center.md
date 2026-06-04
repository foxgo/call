# IAM User Center Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build a production-ready IAM user center for the call platform with tenant management, organization tree, user and role administration, JWT authentication, data authorization, audit logging, and a runnable Vue3 admin console.

**Architecture:** Add a new `call-iam` Spring Boot module and a new `call-iam-web` Vue3 app. Keep deployment as a modular monolith while enforcing DDD and hexagonal boundaries inside the backend. Reuse shared platform conventions where helpful, but keep IAM domain logic inside the new module rather than leaking it into `call-common`.

**Tech Stack:** Java 21, Spring Boot, Spring Security 6, MyBatis Plus, MySQL 8, Redis 7, RocketMQ 5, JWT, Maven, Vue3, Vite, TypeScript, Element Plus, JUnit 5, Mockito, Vitest

---

### Task 1: Create Backend Module Skeleton

**Files:**
- Modify: `pom.xml`
- Create: `call-iam/pom.xml`
- Create: `call-iam/src/main/java/com/callcenter/iam/IamApplication.java`
- Create: `call-iam/src/main/resources/application.yml`
- Create: `call-iam/src/test/java/com/callcenter/iam/IamApplicationTest.java`

**Step 1: Write the failing test**

Create a minimal Spring Boot context test:

```java
@SpringBootTest
class IamApplicationTest {

    @Test
    void contextLoads() {
    }
}
```

**Step 2: Run test to verify it fails**

Run: `mvn -pl call-iam -Dtest=IamApplicationTest test`

Expected: FAIL because the module does not exist yet.

**Step 3: Write minimal implementation**

- Add `call-iam` to root modules
- Create module `pom.xml`
- Add dependencies for:
  - `call-common`
  - web
  - validation
  - actuator
  - security
  - mybatis-plus
  - mysql driver
  - redis
  - rocketmq
  - lombok
  - test
- Add Spring Boot startup class
- Add baseline `application.yml`

**Step 4: Run test to verify it passes**

Run: `mvn -pl call-iam -Dtest=IamApplicationTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add pom.xml call-iam
git commit -m "iam: add service module skeleton"
```

### Task 2: Add Database Migration Baseline

**Files:**
- Create: `call-iam/src/main/resources/db/migration/V1__init_iam_schema.sql`
- Create: `call-iam/src/test/java/com/callcenter/iam/db/IamSchemaMigrationTest.java`
- Modify: `call-iam/pom.xml`

**Step 1: Write the failing test**

Create a Testcontainers migration test that asserts the core tables exist:

```java
assertThat(tables()).contains(
        "tenant",
        "department",
        "department_closure",
        "iam_user",
        "role",
        "permission",
        "user_role",
        "role_permission",
        "user_department",
        "role_data_scope",
        "audit_log"
);
```

**Step 2: Run test to verify it fails**

Run: `mvn -pl call-iam -Dtest=IamSchemaMigrationTest test`

Expected: FAIL because Flyway migration and schema do not exist yet.

**Step 3: Write minimal implementation**

- Add Flyway dependency
- Create migration with all core tables
- Include unique indexes and operational indexes
- Partition `audit_log` by month-ready DDL comments or direct partition DDL if local MySQL image supports it

**Step 4: Run test to verify it passes**

Run: `mvn -pl call-iam -Dtest=IamSchemaMigrationTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add call-iam/pom.xml call-iam/src/main/resources/db/migration call-iam/src/test/java/com/callcenter/iam/db
git commit -m "iam: add schema migration baseline"
```

### Task 3: Implement Domain Model And Repository Ports

**Files:**
- Create: `call-iam/src/main/java/com/callcenter/iam/domain/tenant/Tenant.java`
- Create: `call-iam/src/main/java/com/callcenter/iam/domain/tenant/TenantStatus.java`
- Create: `call-iam/src/main/java/com/callcenter/iam/domain/tenant/TenantRepository.java`
- Create: `call-iam/src/main/java/com/callcenter/iam/domain/organization/Department.java`
- Create: `call-iam/src/main/java/com/callcenter/iam/domain/organization/DepartmentRepository.java`
- Create: `call-iam/src/main/java/com/callcenter/iam/domain/user/User.java`
- Create: `call-iam/src/main/java/com/callcenter/iam/domain/user/UserStatus.java`
- Create: `call-iam/src/main/java/com/callcenter/iam/domain/user/UserType.java`
- Create: `call-iam/src/main/java/com/callcenter/iam/domain/user/UserRepository.java`
- Create: `call-iam/src/main/java/com/callcenter/iam/domain/authorization/Role.java`
- Create: `call-iam/src/main/java/com/callcenter/iam/domain/authorization/Permission.java`
- Create: `call-iam/src/main/java/com/callcenter/iam/domain/audit/AuditLog.java`
- Create: `call-iam/src/test/java/com/callcenter/iam/domain/tenant/TenantTest.java`
- Create: `call-iam/src/test/java/com/callcenter/iam/domain/user/UserTest.java`

**Step 1: Write the failing test**

Add domain tests that prove:

- deleted tenant cannot be reactivated
- invalid password format is rejected
- platform user can have null `tenantId`
- tenant user must have non-null `tenantId`

Example:

```java
@Test
void shouldRejectReactivateDeletedTenant() {
    Tenant tenant = Tenant.deleted(1L, "acme", "Acme");
    assertThrows(DomainRuleViolationException.class, tenant::reactivate);
}
```

**Step 2: Run test to verify it fails**

Run: `mvn -pl call-iam -Dtest=TenantTest,UserTest test`

Expected: FAIL because the domain model does not exist yet.

**Step 3: Write minimal implementation**

- Implement aggregates and enums
- Keep Spring annotations out of domain
- Add rule methods such as `suspend()`, `expire()`, `delete()`, `reactivate()`
- Add password policy validation helpers

**Step 4: Run test to verify it passes**

Run: `mvn -pl call-iam -Dtest=TenantTest,UserTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add call-iam/src/main/java/com/callcenter/iam/domain call-iam/src/test/java/com/callcenter/iam/domain
git commit -m "iam: add core domain model"
```

### Task 4: Implement Infrastructure Persistence Adapters

**Files:**
- Create: `call-iam/src/main/java/com/callcenter/iam/infrastructure/persistence/dataobject/TenantDO.java`
- Create: `call-iam/src/main/java/com/callcenter/iam/infrastructure/persistence/dataobject/DepartmentDO.java`
- Create: `call-iam/src/main/java/com/callcenter/iam/infrastructure/persistence/dataobject/UserDO.java`
- Create: `call-iam/src/main/java/com/callcenter/iam/infrastructure/persistence/dataobject/RoleDO.java`
- Create: `call-iam/src/main/java/com/callcenter/iam/infrastructure/persistence/mapper/TenantMapper.java`
- Create: `call-iam/src/main/java/com/callcenter/iam/infrastructure/persistence/mapper/DepartmentMapper.java`
- Create: `call-iam/src/main/java/com/callcenter/iam/infrastructure/persistence/mapper/UserMapper.java`
- Create: `call-iam/src/main/java/com/callcenter/iam/infrastructure/persistence/adapter/MybatisTenantRepository.java`
- Create: `call-iam/src/main/java/com/callcenter/iam/infrastructure/persistence/adapter/MybatisUserRepository.java`
- Create: `call-iam/src/main/java/com/callcenter/iam/infrastructure/persistence/adapter/MybatisDepartmentRepository.java`
- Create: `call-iam/src/test/java/com/callcenter/iam/infrastructure/persistence/MybatisTenantRepositoryTest.java`

**Step 1: Write the failing test**

Create an integration test that inserts and reloads a tenant aggregate through the repository adapter.

**Step 2: Run test to verify it fails**

Run: `mvn -pl call-iam -Dtest=MybatisTenantRepositoryTest test`

Expected: FAIL because persistence adapters do not exist yet.

**Step 3: Write minimal implementation**

- Add data objects and mappers
- Add aggregate/data-object assemblers
- Implement repository adapters against repository ports
- Use MyBatis Plus for CRUD and custom lookups

**Step 4: Run test to verify it passes**

Run: `mvn -pl call-iam -Dtest=MybatisTenantRepositoryTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add call-iam/src/main/java/com/callcenter/iam/infrastructure/persistence call-iam/src/test/java/com/callcenter/iam/infrastructure/persistence
git commit -m "iam: add persistence adapters"
```

### Task 5: Implement Security Baseline And JWT

**Files:**
- Create: `call-iam/src/main/java/com/callcenter/iam/infrastructure/security/SecurityConfiguration.java`
- Create: `call-iam/src/main/java/com/callcenter/iam/infrastructure/security/JwtTokenProvider.java`
- Create: `call-iam/src/main/java/com/callcenter/iam/infrastructure/security/JwtAuthenticationFilter.java`
- Create: `call-iam/src/main/java/com/callcenter/iam/infrastructure/security/RefreshTokenStore.java`
- Create: `call-iam/src/main/java/com/callcenter/iam/infrastructure/security/RedisRefreshTokenStore.java`
- Create: `call-iam/src/main/java/com/callcenter/iam/application/auth/LoginUseCase.java`
- Create: `call-iam/src/test/java/com/callcenter/iam/infrastructure/security/JwtTokenProviderTest.java`
- Create: `call-iam/src/test/java/com/callcenter/iam/application/auth/LoginUseCaseTest.java`

**Step 1: Write the failing test**

Add tests that verify:

- token contains `tenantId`, `userId`, `roleIds`, and `deptIds`
- refresh token rotation invalidates the old token
- locked user cannot log in
- suspended tenant user cannot log in

**Step 2: Run test to verify it fails**

Run: `mvn -pl call-iam -Dtest=JwtTokenProviderTest,LoginUseCaseTest test`

Expected: FAIL because auth components do not exist yet.

**Step 3: Write minimal implementation**

- Add JWT provider
- Add authentication filter
- Implement refresh token store in Redis
- Implement login use case with password verification and tenant checks
- Publish login success and failure audit events

**Step 4: Run test to verify it passes**

Run: `mvn -pl call-iam -Dtest=JwtTokenProviderTest,LoginUseCaseTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add call-iam/src/main/java/com/callcenter/iam/infrastructure/security call-iam/src/main/java/com/callcenter/iam/application/auth call-iam/src/test/java/com/callcenter/iam
git commit -m "iam: add jwt authentication flow"
```

### Task 6: Implement Tenant Management Use Cases And APIs

**Files:**
- Create: `call-iam/src/main/java/com/callcenter/iam/application/tenant/CreateTenantCommand.java`
- Create: `call-iam/src/main/java/com/callcenter/iam/application/tenant/CreateTenantUseCase.java`
- Create: `call-iam/src/main/java/com/callcenter/iam/application/tenant/UpdateTenantUseCase.java`
- Create: `call-iam/src/main/java/com/callcenter/iam/interfaces/rest/tenant/TenantController.java`
- Create: `call-iam/src/main/java/com/callcenter/iam/interfaces/rest/tenant/request/CreateTenantRequest.java`
- Create: `call-iam/src/main/java/com/callcenter/iam/interfaces/rest/tenant/response/TenantResponse.java`
- Create: `call-iam/src/test/java/com/callcenter/iam/interfaces/rest/tenant/TenantControllerTest.java`

**Step 1: Write the failing test**

Add MVC tests for:

- `POST /api/iam/tenants`
- `GET /api/iam/tenants`
- forbidden access for non-platform admin

**Step 2: Run test to verify it fails**

Run: `mvn -pl call-iam -Dtest=TenantControllerTest test`

Expected: FAIL because the API does not exist yet.

**Step 3: Write minimal implementation**

- Implement create/list/get/update/delete flows
- Enforce platform admin only
- Return unified response envelopes

**Step 4: Run test to verify it passes**

Run: `mvn -pl call-iam -Dtest=TenantControllerTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add call-iam/src/main/java/com/callcenter/iam/application/tenant call-iam/src/main/java/com/callcenter/iam/interfaces/rest/tenant call-iam/src/test/java/com/callcenter/iam/interfaces/rest/tenant
git commit -m "iam: add tenant management api"
```

### Task 7: Implement Department Tree Use Cases And APIs

**Files:**
- Create: `call-iam/src/main/java/com/callcenter/iam/application/organization/CreateDepartmentUseCase.java`
- Create: `call-iam/src/main/java/com/callcenter/iam/application/organization/MoveDepartmentUseCase.java`
- Create: `call-iam/src/main/java/com/callcenter/iam/infrastructure/persistence/adapter/MybatisDepartmentTreeRepository.java`
- Create: `call-iam/src/main/java/com/callcenter/iam/interfaces/rest/organization/DepartmentController.java`
- Create: `call-iam/src/test/java/com/callcenter/iam/application/organization/MoveDepartmentUseCaseTest.java`
- Create: `call-iam/src/test/java/com/callcenter/iam/interfaces/rest/organization/DepartmentControllerTest.java`

**Step 1: Write the failing test**

Add tests that prove:

- closure rows are created for a new child department
- moving a node under its own descendant is rejected
- `GET /api/iam/departments/tree` returns nested nodes

**Step 2: Run test to verify it fails**

Run: `mvn -pl call-iam -Dtest=MoveDepartmentUseCaseTest,DepartmentControllerTest test`

Expected: FAIL because tree logic does not exist yet.

**Step 3: Write minimal implementation**

- Implement closure-table repository operations
- Implement create, move, update, delete, and tree query use cases
- Keep cycle validation in application/domain service

**Step 4: Run test to verify it passes**

Run: `mvn -pl call-iam -Dtest=MoveDepartmentUseCaseTest,DepartmentControllerTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add call-iam/src/main/java/com/callcenter/iam/application/organization call-iam/src/main/java/com/callcenter/iam/interfaces/rest/organization call-iam/src/test/java/com/callcenter/iam/application/organization call-iam/src/test/java/com/callcenter/iam/interfaces/rest/organization
git commit -m "iam: add department tree management"
```

### Task 8: Implement User Management APIs

**Files:**
- Create: `call-iam/src/main/java/com/callcenter/iam/application/user/CreateUserUseCase.java`
- Create: `call-iam/src/main/java/com/callcenter/iam/application/user/UpdateUserUseCase.java`
- Create: `call-iam/src/main/java/com/callcenter/iam/application/user/AssignUserRolesUseCase.java`
- Create: `call-iam/src/main/java/com/callcenter/iam/application/user/AssignUserDepartmentsUseCase.java`
- Create: `call-iam/src/main/java/com/callcenter/iam/interfaces/rest/user/UserController.java`
- Create: `call-iam/src/test/java/com/callcenter/iam/interfaces/rest/user/UserControllerTest.java`

**Step 1: Write the failing test**

Add tests for:

- create user with password policy validation
- query users by department
- update status
- reset password

**Step 2: Run test to verify it fails**

Run: `mvn -pl call-iam -Dtest=UserControllerTest test`

Expected: FAIL because the API does not exist yet.

**Step 3: Write minimal implementation**

- Implement create/list/get/update/delete
- Implement status update and password reset
- Implement role and department assignment
- Enforce tenant isolation on all mutations

**Step 4: Run test to verify it passes**

Run: `mvn -pl call-iam -Dtest=UserControllerTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add call-iam/src/main/java/com/callcenter/iam/application/user call-iam/src/main/java/com/callcenter/iam/interfaces/rest/user call-iam/src/test/java/com/callcenter/iam/interfaces/rest/user
git commit -m "iam: add user management api"
```

### Task 9: Implement Role, Permission, And Data Scope APIs

**Files:**
- Create: `call-iam/src/main/java/com/callcenter/iam/application/authorization/CreateRoleUseCase.java`
- Create: `call-iam/src/main/java/com/callcenter/iam/application/authorization/UpdateRolePermissionsUseCase.java`
- Create: `call-iam/src/main/java/com/callcenter/iam/application/authorization/UpdateRoleDataScopeUseCase.java`
- Create: `call-iam/src/main/java/com/callcenter/iam/application/authorization/DataScopeResolver.java`
- Create: `call-iam/src/main/java/com/callcenter/iam/interfaces/rest/authorization/RoleController.java`
- Create: `call-iam/src/main/java/com/callcenter/iam/interfaces/rest/authorization/PermissionController.java`
- Create: `call-iam/src/test/java/com/callcenter/iam/application/authorization/DataScopeResolverTest.java`
- Create: `call-iam/src/test/java/com/callcenter/iam/interfaces/rest/authorization/RoleControllerTest.java`

**Step 1: Write the failing test**

Add tests that prove:

- role permissions can be updated
- `DEPARTMENT_AND_CHILD` resolves descendant departments
- `SELF` scope only returns operator-owned rows

**Step 2: Run test to verify it fails**

Run: `mvn -pl call-iam -Dtest=DataScopeResolverTest,RoleControllerTest test`

Expected: FAIL because authorization management does not exist yet.

**Step 3: Write minimal implementation**

- Implement role CRUD
- Seed permissions
- Implement role-permission binding
- Implement role data scope update and resolution

**Step 4: Run test to verify it passes**

Run: `mvn -pl call-iam -Dtest=DataScopeResolverTest,RoleControllerTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add call-iam/src/main/java/com/callcenter/iam/application/authorization call-iam/src/main/java/com/callcenter/iam/interfaces/rest/authorization call-iam/src/test/java/com/callcenter/iam/application/authorization call-iam/src/test/java/com/callcenter/iam/interfaces/rest/authorization
git commit -m "iam: add authorization management api"
```

### Task 10: Add Tenant And Data Scope Query Interceptors

**Files:**
- Create: `call-iam/src/main/java/com/callcenter/iam/infrastructure/persistence/interceptor/TenantLineInterceptorConfig.java`
- Create: `call-iam/src/main/java/com/callcenter/iam/infrastructure/persistence/interceptor/DataScopeQueryCustomizer.java`
- Create: `call-iam/src/test/java/com/callcenter/iam/infrastructure/persistence/interceptor/TenantLineInterceptorConfigTest.java`

**Step 1: Write the failing test**

Add tests that prove:

- tenant user queries always include current `tenant_id`
- platform admin can access platform tenant APIs without tenant filter contamination
- user list query with `DEPARTMENT` scope injects department filter

**Step 2: Run test to verify it fails**

Run: `mvn -pl call-iam -Dtest=TenantLineInterceptorConfigTest test`

Expected: FAIL because automatic scoping does not exist yet.

**Step 3: Write minimal implementation**

- Configure MyBatis Plus tenant line support
- Add bypass rules for platform tables and platform-admin endpoints
- Add query customizer for department and self filters

**Step 4: Run test to verify it passes**

Run: `mvn -pl call-iam -Dtest=TenantLineInterceptorConfigTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add call-iam/src/main/java/com/callcenter/iam/infrastructure/persistence/interceptor call-iam/src/test/java/com/callcenter/iam/infrastructure/persistence/interceptor
git commit -m "iam: add tenant and data scope interceptors"
```

### Task 11: Implement Audit Pipeline

**Files:**
- Create: `call-iam/src/main/java/com/callcenter/iam/application/audit/AuditCommand.java`
- Create: `call-iam/src/main/java/com/callcenter/iam/infrastructure/audit/AuditEventPublisher.java`
- Create: `call-iam/src/main/java/com/callcenter/iam/infrastructure/audit/RocketMqAuditEventPublisher.java`
- Create: `call-iam/src/main/java/com/callcenter/iam/mq/AuditEventConsumer.java`
- Create: `call-iam/src/main/java/com/callcenter/iam/interfaces/rest/audit/AuditLogController.java`
- Create: `call-iam/src/test/java/com/callcenter/iam/application/audit/AuditPipelineTest.java`

**Step 1: Write the failing test**

Add tests that prove:

- create user emits audit command
- login emits success or failure audit event
- audit log query supports operator and resource filters

**Step 2: Run test to verify it fails**

Run: `mvn -pl call-iam -Dtest=AuditPipelineTest test`

Expected: FAIL because audit pipeline does not exist yet.

**Step 3: Write minimal implementation**

- Add audit command model
- Publish audit events after transactional use cases
- Consume and persist audit events
- Expose audit query endpoints

**Step 4: Run test to verify it passes**

Run: `mvn -pl call-iam -Dtest=AuditPipelineTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add call-iam/src/main/java/com/callcenter/iam/application/audit call-iam/src/main/java/com/callcenter/iam/infrastructure/audit call-iam/src/main/java/com/callcenter/iam/mq call-iam/src/main/java/com/callcenter/iam/interfaces/rest/audit call-iam/src/test/java/com/callcenter/iam/application/audit
git commit -m "iam: add audit pipeline"
```

### Task 12: Add OpenAPI, Error Handling, And Seed Data

**Files:**
- Create: `call-iam/src/main/java/com/callcenter/iam/interfaces/rest/support/ApiResponse.java`
- Create: `call-iam/src/main/java/com/callcenter/iam/interfaces/rest/support/GlobalExceptionHandler.java`
- Create: `call-iam/src/main/java/com/callcenter/iam/interfaces/rest/support/OpenApiConfiguration.java`
- Create: `call-iam/src/main/resources/db/migration/V2__seed_permissions_and_roles.sql`
- Create: `call-iam/src/test/java/com/callcenter/iam/interfaces/rest/support/GlobalExceptionHandlerTest.java`

**Step 1: Write the failing test**

Add tests that prove:

- validation errors map to `400`
- forbidden errors map to `403`
- duplicate data maps to `409`
- OpenAPI endpoint is exposed

**Step 2: Run test to verify it fails**

Run: `mvn -pl call-iam -Dtest=GlobalExceptionHandlerTest test`

Expected: FAIL because error mapping and API docs are not configured yet.

**Step 3: Write minimal implementation**

- Add unified response model
- Add global exception mapping
- Add springdoc configuration
- Seed built-in permissions and roles

**Step 4: Run test to verify it passes**

Run: `mvn -pl call-iam -Dtest=GlobalExceptionHandlerTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add call-iam/src/main/java/com/callcenter/iam/interfaces/rest/support call-iam/src/main/resources/db/migration/V2__seed_permissions_and_roles.sql call-iam/src/test/java/com/callcenter/iam/interfaces/rest/support
git commit -m "iam: add api support and seed data"
```

### Task 13: Create Frontend App Skeleton

**Files:**
- Create: `call-iam-web/package.json`
- Create: `call-iam-web/tsconfig.json`
- Create: `call-iam-web/vite.config.ts`
- Create: `call-iam-web/index.html`
- Create: `call-iam-web/src/main.ts`
- Create: `call-iam-web/src/App.vue`
- Create: `call-iam-web/src/router/index.ts`
- Create: `call-iam-web/src/stores/auth.ts`
- Create: `call-iam-web/src/api/http.ts`
- Create: `call-iam-web/src/styles/index.css`
- Create: `call-iam-web/src/views/LoginView.vue`
- Create: `call-iam-web/src/views/DashboardView.vue`
- Create: `call-iam-web/src/tests/smoke.spec.ts`

**Step 1: Write the failing test**

Create a Vitest smoke test that mounts `App.vue` and verifies the router renders the login page by default.

**Step 2: Run test to verify it fails**

Run: `cd call-iam-web && npm test`

Expected: FAIL because the app does not exist yet.

**Step 3: Write minimal implementation**

- Scaffold Vite + Vue3 + TypeScript
- Add Element Plus
- Add router, store, HTTP client, and baseline CSS variables
- Add login and dashboard views

**Step 4: Run test to verify it passes**

Run: `cd call-iam-web && npm test`

Expected: PASS

**Step 5: Commit**

```bash
git add call-iam-web
git commit -m "iam-web: add frontend app skeleton"
```

### Task 14: Implement Frontend Auth Flow And Layout

**Files:**
- Create: `call-iam-web/src/layouts/ConsoleLayout.vue`
- Create: `call-iam-web/src/components/SideNav.vue`
- Create: `call-iam-web/src/components/HeaderBar.vue`
- Modify: `call-iam-web/src/stores/auth.ts`
- Modify: `call-iam-web/src/router/index.ts`
- Create: `call-iam-web/src/tests/auth-flow.spec.ts`

**Step 1: Write the failing test**

Add tests that prove:

- successful login stores tokens and profile
- unauthorized API response triggers logout redirect
- protected routes redirect to login when no session exists

**Step 2: Run test to verify it fails**

Run: `cd call-iam-web && npm test -- auth-flow.spec.ts`

Expected: FAIL because auth flow does not exist yet.

**Step 3: Write minimal implementation**

- Add auth store with access and refresh token management
- Add HTTP interceptor
- Add route guard
- Add shared console layout

**Step 4: Run test to verify it passes**

Run: `cd call-iam-web && npm test -- auth-flow.spec.ts`

Expected: PASS

**Step 5: Commit**

```bash
git add call-iam-web/src
git commit -m "iam-web: add auth flow and app shell"
```

### Task 15: Implement Frontend Management Pages

**Files:**
- Create: `call-iam-web/src/views/tenant/TenantListView.vue`
- Create: `call-iam-web/src/views/user/UserListView.vue`
- Create: `call-iam-web/src/views/role/RoleListView.vue`
- Create: `call-iam-web/src/views/department/DepartmentTreeView.vue`
- Create: `call-iam-web/src/views/audit/AuditLogView.vue`
- Create: `call-iam-web/src/components/tenant/TenantFormDialog.vue`
- Create: `call-iam-web/src/components/user/UserFormDialog.vue`
- Create: `call-iam-web/src/components/role/RoleFormDialog.vue`
- Create: `call-iam-web/src/tests/tenant-page.spec.ts`

**Step 1: Write the failing test**

Add tests that prove:

- tenant list page loads and renders rows
- user page can open create dialog
- department page renders tree data

**Step 2: Run test to verify it fails**

Run: `cd call-iam-web && npm test -- tenant-page.spec.ts`

Expected: FAIL because the pages do not exist yet.

**Step 3: Write minimal implementation**

- Add CRUD list pages
- Add forms and dialogs
- Add basic search and pagination
- Add role permission assignment UI
- Add audit log filters

**Step 4: Run test to verify it passes**

Run: `cd call-iam-web && npm test -- tenant-page.spec.ts`

Expected: PASS

**Step 5: Commit**

```bash
git add call-iam-web/src
git commit -m "iam-web: add management pages"
```

### Task 16: Add Docker, Compose, Kubernetes, And README

**Files:**
- Create: `call-iam/Dockerfile`
- Modify: `deploy/docker-compose.yml`
- Create: `deploy/k8s/call-iam-deployment.yaml`
- Modify: `deploy/k8s/services.yaml`
- Modify: `deploy/k8s/configmap.yaml`
- Modify: `README.md`
- Create: `call-iam/README.md`

**Step 1: Write the failing test**

Add a lightweight verification step by checking:

- `call-iam` Dockerfile builds
- compose contains a `call-iam` service
- k8s service and deployment manifests reference `call-iam`

This can be captured in a documentation or smoke verification script if needed.

**Step 2: Run test to verify it fails**

Run: `rg "call-iam" deploy/docker-compose.yml deploy/k8s README.md`

Expected: FAIL because deployment assets do not exist yet.

**Step 3: Write minimal implementation**

- Add backend Dockerfile
- Wire service into compose
- Add Kubernetes deployment and service
- Update root README and add module-specific README

**Step 4: Run test to verify it passes**

Run: `rg "call-iam" deploy/docker-compose.yml deploy/k8s README.md`

Expected: PASS

**Step 5: Commit**

```bash
git add call-iam/Dockerfile deploy/docker-compose.yml deploy/k8s README.md call-iam/README.md
git commit -m "iam: add deployment assets and docs"
```

### Task 17: Run End-To-End Verification

**Files:**
- Modify: `README.md`
- Optionally Create: `docs/plans/2026-06-04-iam-user-center-verification.md`

**Step 1: Write the failing test**

Define the verification matrix:

- backend unit tests
- backend MVC tests
- migration tests
- frontend unit tests
- local startup smoke checks

**Step 2: Run test to verify current gaps**

Run:

```bash
mvn -pl call-iam test
cd call-iam-web && npm test
```

Expected: One or more failures until all prior tasks are complete.

**Step 3: Write minimal implementation**

- Fix remaining failures
- Tighten flaky tests
- Update docs with known environment variables and startup steps

**Step 4: Run test to verify it passes**

Run:

```bash
mvn -pl call-iam test
cd call-iam-web && npm test
```

Expected: PASS

**Step 5: Commit**

```bash
git add README.md
git commit -m "iam: finalize verification and docs"
```
