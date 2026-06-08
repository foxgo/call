# Call IAM Web Full Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build a fully functional `call-iam-web` admin console against the existing `call-iam` APIs, including login/session flow and all secondary management actions on the current pages.

**Architecture:** Keep the current Vue 3 app structure, but replace static page data with typed API services, shared async state handling, and action dialogs. Fill the backend auth/profile gap with minimal REST controllers that reuse the existing `LoginUseCase`, `JwtTokenProvider`, and repositories instead of inventing a second auth path.

**Tech Stack:** Java 21, Spring Boot Test, Vue 3, Vite, TypeScript, Pinia, Vue Router, Element Plus, Vitest

---

### Task 1: Add Auth REST Coverage In Backend

**Files:**
- Create: `call-iam/src/main/java/com/callcenter/iam/interfaces/rest/auth/AuthController.java`
- Create: `call-iam/src/main/java/com/callcenter/iam/interfaces/rest/auth/request/LoginRequest.java`
- Create: `call-iam/src/main/java/com/callcenter/iam/interfaces/rest/auth/request/RefreshTokenRequest.java`
- Create: `call-iam/src/test/java/com/callcenter/iam/interfaces/rest/auth/AuthControllerTest.java`
- Modify: `call-iam/src/main/java/com/callcenter/iam/interfaces/rest/user/UserController.java`
- Modify: `call-iam/src/test/java/com/callcenter/iam/interfaces/rest/user/UserControllerTest.java`

**Step 1: Write the failing tests**

Add `AuthControllerTest` that verifies:

- `POST /api/iam/auth/login` returns tokens
- `POST /api/iam/auth/refresh` rotates tokens

Add a `UserControllerTest` case that verifies:

- `GET /api/iam/users/me` returns current user summary

**Step 2: Run tests to verify they fail**

Run: `mvn -pl call-iam -Dtest=AuthControllerTest,UserControllerTest test`

Expected: FAIL because the endpoints do not exist yet.

**Step 3: Write minimal implementation**

- Add auth request DTOs
- Add `AuthController` backed by `LoginUseCase`
- Add `GET /api/iam/users/me` to `UserController`
- Reuse token claims from `JwtAuthenticationFilter`

**Step 4: Run tests to verify they pass**

Run: `mvn -pl call-iam -Dtest=AuthControllerTest,UserControllerTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add call-iam/src/main/java/com/callcenter/iam/interfaces/rest/auth call-iam/src/main/java/com/callcenter/iam/interfaces/rest/user/UserController.java call-iam/src/test/java/com/callcenter/iam/interfaces/rest/auth call-iam/src/test/java/com/callcenter/iam/interfaces/rest/user/UserControllerTest.java
git commit -m "iam: add auth and current user endpoints"
```

### Task 2: Build Typed Frontend API Layer

**Files:**
- Modify: `call-iam-web/src/api/http.ts`
- Create: `call-iam-web/src/api/types.ts`
- Create: `call-iam-web/src/api/auth.ts`
- Create: `call-iam-web/src/api/tenant.ts`
- Create: `call-iam-web/src/api/user.ts`
- Create: `call-iam-web/src/api/role.ts`
- Create: `call-iam-web/src/api/department.ts`
- Create: `call-iam-web/src/api/audit.ts`
- Create: `call-iam-web/src/tests/api-services.spec.ts`

**Step 1: Write the failing tests**

Add API service tests that verify:

- envelope data is unwrapped correctly
- query params are passed correctly
- each service method hits the expected endpoint

**Step 2: Run tests to verify they fail**

Run: `npm test -- --run src/tests/api-services.spec.ts`

Expected: FAIL because the service modules do not exist.

**Step 3: Write minimal implementation**

- Add shared response and entity types
- Add an `unwrap` helper in the HTTP layer
- Implement small service modules per domain

**Step 4: Run tests to verify they pass**

Run: `npm test -- --run src/tests/api-services.spec.ts`

Expected: PASS

**Step 5: Commit**

```bash
git add call-iam-web/src/api call-iam-web/src/tests/api-services.spec.ts
git commit -m "iam-web: add typed api services"
```

### Task 3: Replace Auth Skeleton With Real Session Flow

**Files:**
- Modify: `call-iam-web/src/stores/auth.ts`
- Modify: `call-iam-web/src/views/LoginView.vue`
- Modify: `call-iam-web/src/components/HeaderBar.vue`
- Modify: `call-iam-web/src/router/index.ts`
- Modify: `call-iam-web/src/tests/auth-flow.spec.ts`

**Step 1: Write the failing tests**

Extend auth tests to verify:

- login form submits through the store
- current profile is loaded from `/users/me`
- logout clears session
- header shows current user display name

**Step 2: Run tests to verify they fail**

Run: `npm test -- --run src/tests/auth-flow.spec.ts`

Expected: FAIL because the page and header do not implement the flow yet.

**Step 3: Write minimal implementation**

- Swap static login screen for an Element Plus form
- Move login and refresh logic to service helpers
- Add logout action and user summary in header
- Keep 401 redirect behavior

**Step 4: Run tests to verify they pass**

Run: `npm test -- --run src/tests/auth-flow.spec.ts`

Expected: PASS

**Step 5: Commit**

```bash
git add call-iam-web/src/stores/auth.ts call-iam-web/src/views/LoginView.vue call-iam-web/src/components/HeaderBar.vue call-iam-web/src/router/index.ts call-iam-web/src/tests/auth-flow.spec.ts
git commit -m "iam-web: implement auth flow"
```

### Task 4: Implement Tenant And Dashboard Pages

**Files:**
- Modify: `call-iam-web/src/views/DashboardView.vue`
- Modify: `call-iam-web/src/views/tenant/TenantListView.vue`
- Modify: `call-iam-web/src/components/tenant/TenantFormDialog.vue`
- Create: `call-iam-web/src/tests/dashboard-and-tenant.spec.ts`

**Step 1: Write the failing tests**

Add tests that verify:

- tenant page loads API data
- tenant create and update submit the expected payloads
- dashboard renders counts from loaded domain data

**Step 2: Run tests to verify they fail**

Run: `npm test -- --run src/tests/dashboard-and-tenant.spec.ts`

Expected: FAIL because the page still uses static placeholders.

**Step 3: Write minimal implementation**

- Load tenants from the API
- Replace custom controls with Element Plus table, form, date picker, and confirm flows
- Build dashboard summary from tenant, user, role, and audit calls

**Step 4: Run tests to verify they pass**

Run: `npm test -- --run src/tests/dashboard-and-tenant.spec.ts`

Expected: PASS

**Step 5: Commit**

```bash
git add call-iam-web/src/views/DashboardView.vue call-iam-web/src/views/tenant/TenantListView.vue call-iam-web/src/components/tenant/TenantFormDialog.vue call-iam-web/src/tests/dashboard-and-tenant.spec.ts
git commit -m "iam-web: implement tenant and dashboard pages"
```

### Task 5: Implement User Management And Assignment Actions

**Files:**
- Modify: `call-iam-web/src/views/user/UserListView.vue`
- Modify: `call-iam-web/src/components/user/UserFormDialog.vue`
- Create: `call-iam-web/src/components/user/UserRolesDialog.vue`
- Create: `call-iam-web/src/components/user/UserDepartmentsDialog.vue`
- Create: `call-iam-web/src/components/user/ResetPasswordDialog.vue`
- Create: `call-iam-web/src/tests/user-management.spec.ts`

**Step 1: Write the failing tests**

Add tests that verify:

- users load from the API
- create and update actions submit the right payload
- status change, role assignment, department assignment, and reset password call the right endpoints

**Step 2: Run tests to verify they fail**

Run: `npm test -- --run src/tests/user-management.spec.ts`

Expected: FAIL because the dialogs and actions are not implemented.

**Step 3: Write minimal implementation**

- Add user table and department filter
- Add form dialog for create/edit
- Add separate dialogs for roles, departments, and password reset
- Refresh user data after each mutation

**Step 4: Run tests to verify they pass**

Run: `npm test -- --run src/tests/user-management.spec.ts`

Expected: PASS

**Step 5: Commit**

```bash
git add call-iam-web/src/views/user/UserListView.vue call-iam-web/src/components/user call-iam-web/src/tests/user-management.spec.ts
git commit -m "iam-web: implement user management actions"
```

### Task 6: Implement Role Management And Data Scope Actions

**Files:**
- Modify: `call-iam-web/src/views/role/RoleListView.vue`
- Modify: `call-iam-web/src/components/role/RoleFormDialog.vue`
- Create: `call-iam-web/src/components/role/RoleDataScopeDialog.vue`
- Create: `call-iam-web/src/tests/role-management.spec.ts`

**Step 1: Write the failing tests**

Add tests that verify:

- roles load from the API
- permissions are loaded as options
- create/update/delete/permission assignment/data scope assignment call the expected endpoints

**Step 2: Run tests to verify they fail**

Run: `npm test -- --run src/tests/role-management.spec.ts`

Expected: FAIL because the role page is static.

**Step 3: Write minimal implementation**

- Build a table-driven role page
- Use a form dialog for base fields
- Use a dedicated data scope dialog
- Use permission multi-select or checkbox group based on loaded permissions

**Step 4: Run tests to verify they pass**

Run: `npm test -- --run src/tests/role-management.spec.ts`

Expected: PASS

**Step 5: Commit**

```bash
git add call-iam-web/src/views/role/RoleListView.vue call-iam-web/src/components/role call-iam-web/src/tests/role-management.spec.ts
git commit -m "iam-web: implement role management actions"
```

### Task 7: Implement Department Tree And Audit Views

**Files:**
- Modify: `call-iam-web/src/views/department/DepartmentTreeView.vue`
- Modify: `call-iam-web/src/views/audit/AuditLogView.vue`
- Create: `call-iam-web/src/components/department/DepartmentFormDialog.vue`
- Create: `call-iam-web/src/components/department/DepartmentMoveDialog.vue`
- Create: `call-iam-web/src/components/audit/AuditDetailDialog.vue`
- Create: `call-iam-web/src/tests/department-and-audit.spec.ts`

**Step 1: Write the failing tests**

Add tests that verify:

- department tree loads nested API data
- create/update/move/delete actions hit the correct endpoints
- audit filters produce the expected query calls
- audit detail fetches a single log entry

**Step 2: Run tests to verify they fail**

Run: `npm test -- --run src/tests/department-and-audit.spec.ts`

Expected: FAIL because the pages are placeholders.

**Step 3: Write minimal implementation**

- Render a tree component backed by `/departments/tree`
- Add dialogs for create/edit/move
- Render audit list filters and detail modal

**Step 4: Run tests to verify they pass**

Run: `npm test -- --run src/tests/department-and-audit.spec.ts`

Expected: PASS

**Step 5: Commit**

```bash
git add call-iam-web/src/views/department/DepartmentTreeView.vue call-iam-web/src/views/audit/AuditLogView.vue call-iam-web/src/components/department call-iam-web/src/components/audit call-iam-web/src/tests/department-and-audit.spec.ts
git commit -m "iam-web: implement department and audit pages"
```

### Task 8: Verify Full Frontend And Targeted Backend Coverage

**Files:**
- Modify: `call-iam-web/src/tests/tenant-page.spec.ts`
- Modify: `call-iam-web/src/tests/smoke.spec.ts`

**Step 1: Write the failing tests**

Update existing smoke tests so they assert real loaded UI states instead of placeholder copy.

**Step 2: Run tests to verify they fail**

Run: `npm test`

Expected: FAIL because old assertions still match the placeholder app.

**Step 3: Write minimal implementation**

- Align legacy tests with the real UI
- Remove obsolete placeholder expectations

**Step 4: Run tests to verify they pass**

Run: `npm test`

Expected: PASS

Then run:

- `npm run build`
- `mvn -pl call-iam -Dtest=AuthControllerTest,UserControllerTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add call-iam-web/src/tests call-iam-web/src/views call-iam-web/src/components call-iam-web/src/api call-iam-web/src/stores call-iam/src/main/java/com/callcenter/iam/interfaces/rest call-iam/src/test/java/com/callcenter/iam/interfaces/rest
git commit -m "iam-web: complete full interface implementation"
```
