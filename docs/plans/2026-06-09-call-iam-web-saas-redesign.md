# Call IAM Web SaaS Redesign Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Refactor `call-iam-web` into a modern, concise, enterprise SaaS IAM console without changing its routes, API contracts, or core workflows.

**Architecture:** Keep the existing Vue view and dialog structure, but introduce a shared visual system in global CSS and refactor each page to consume the same shell, toolbar, panel, list, badge, and dialog patterns. Preserve business logic and typed API usage while improving hierarchy, consistency, and responsive behavior.

**Tech Stack:** Vue 3, TypeScript, Vite, Pinia, Vue Router, Vitest

---

### Task 1: Lock the redesign with tests and a shared style target

**Files:**
- Modify: `call-iam-web/src/tests/auth-flow.spec.ts`
- Modify: `call-iam-web/src/tests/smoke.spec.ts`
- Modify: `call-iam-web/src/tests/tenant-page.spec.ts`

**Step 1: Write the failing tests**

Add or update assertions for the redesigned shell and key affordances:

- login page exposes the new title copy and submit action
- shell navigation still renders the main sections
- tenant page still supports search and open-create action after template refactor

**Step 2: Run test to verify it fails**

Run: `npm test -- --run src/tests/auth-flow.spec.ts src/tests/smoke.spec.ts src/tests/tenant-page.spec.ts`
Expected: FAIL because the current UI does not match the new shell and copy.

**Step 3: Write minimal implementation**

- Refactor markup only enough to satisfy the new UI contract
- Keep existing behaviors stable

**Step 4: Run test to verify it passes**

Run: `npm test -- --run src/tests/auth-flow.spec.ts src/tests/smoke.spec.ts src/tests/tenant-page.spec.ts`
Expected: PASS

**Step 5: Commit**

```bash
git add call-iam-web/src/tests/auth-flow.spec.ts call-iam-web/src/tests/smoke.spec.ts call-iam-web/src/tests/tenant-page.spec.ts
git commit -m "iam-web: lock redesigned shell expectations"
```

### Task 2: Build the shared SaaS visual system

**Files:**
- Modify: `call-iam-web/src/styles/index.css`
- Modify: `call-iam-web/src/layouts/ConsoleLayout.vue`
- Modify: `call-iam-web/src/components/SideNav.vue`
- Modify: `call-iam-web/src/components/HeaderBar.vue`

**Step 1: Write the failing test**

Use the updated shell tests from Task 1 as the failing proof for the new frame and navigation structure.

**Step 2: Run test to verify it fails**

Run: `npm test -- --run src/tests/smoke.spec.ts`
Expected: FAIL against the old shell markup and copy.

**Step 3: Write minimal implementation**

- Add color, spacing, radius, shadow, and semantic state tokens
- Add shared utility classes for page, toolbar, panel, badge, button, and form controls
- Refactor console layout, side nav, and header to use the new shell

**Step 4: Run test to verify it passes**

Run: `npm test -- --run src/tests/smoke.spec.ts`
Expected: PASS

**Step 5: Commit**

```bash
git add call-iam-web/src/styles/index.css call-iam-web/src/layouts/ConsoleLayout.vue call-iam-web/src/components/SideNav.vue call-iam-web/src/components/HeaderBar.vue
git commit -m "iam-web: add shared saas shell styles"
```

### Task 3: Redesign login and dashboard surfaces

**Files:**
- Modify: `call-iam-web/src/views/LoginView.vue`
- Modify: `call-iam-web/src/views/DashboardView.vue`

**Step 1: Write the failing test**

Update auth and smoke assertions to cover:

- redesigned login hero copy and CTA
- dashboard summary cards and recent activity section still render data

**Step 2: Run test to verify it fails**

Run: `npm test -- --run src/tests/auth-flow.spec.ts src/tests/smoke.spec.ts`
Expected: FAIL because the old login and dashboard layouts are still present.

**Step 3: Write minimal implementation**

- Convert login page into a branded split-screen layout
- Refactor dashboard into hero, stat cards, and activity panel

**Step 4: Run test to verify it passes**

Run: `npm test -- --run src/tests/auth-flow.spec.ts src/tests/smoke.spec.ts`
Expected: PASS

**Step 5: Commit**

```bash
git add call-iam-web/src/views/LoginView.vue call-iam-web/src/views/DashboardView.vue
git commit -m "iam-web: redesign login and dashboard"
```

### Task 4: Unify tenant, user, role, department, and audit pages

**Files:**
- Modify: `call-iam-web/src/views/tenant/TenantListView.vue`
- Modify: `call-iam-web/src/views/user/UserListView.vue`
- Modify: `call-iam-web/src/views/role/RoleListView.vue`
- Modify: `call-iam-web/src/views/department/DepartmentTreeView.vue`
- Modify: `call-iam-web/src/views/audit/AuditLogView.vue`

**Step 1: Write the failing test**

Keep tenant page tests failing against the old structure and add any minimal selector assertions needed for list actions to ensure the redesigned pages still expose the same workflows.

**Step 2: Run test to verify it fails**

Run: `npm test -- --run src/tests/tenant-page.spec.ts`
Expected: FAIL because the old page shell and action structure are still in place.

**Step 3: Write minimal implementation**

- Move pages onto the shared shell classes
- Improve toolbars, record rows, status display, and action grouping
- Keep all list loading and mutation flows unchanged

**Step 4: Run test to verify it passes**

Run: `npm test -- --run src/tests/tenant-page.spec.ts`
Expected: PASS

**Step 5: Commit**

```bash
git add call-iam-web/src/views/tenant/TenantListView.vue call-iam-web/src/views/user/UserListView.vue call-iam-web/src/views/role/RoleListView.vue call-iam-web/src/views/department/DepartmentTreeView.vue call-iam-web/src/views/audit/AuditLogView.vue
git commit -m "iam-web: unify management page surfaces"
```

### Task 5: Unify dialogs and form interactions

**Files:**
- Modify: `call-iam-web/src/components/tenant/TenantFormDialog.vue`
- Modify: `call-iam-web/src/components/user/UserFormDialog.vue`
- Modify: `call-iam-web/src/components/user/UserRolesDialog.vue`
- Modify: `call-iam-web/src/components/user/UserDepartmentsDialog.vue`
- Modify: `call-iam-web/src/components/user/ResetPasswordDialog.vue`
- Modify: `call-iam-web/src/components/role/RoleFormDialog.vue`
- Modify: `call-iam-web/src/components/role/RoleDataScopeDialog.vue`
- Modify: `call-iam-web/src/components/department/DepartmentFormDialog.vue`
- Modify: `call-iam-web/src/components/department/DepartmentMoveDialog.vue`
- Modify: `call-iam-web/src/components/audit/AuditDetailDialog.vue`

**Step 1: Write the failing test**

Use the page interaction tests to keep create/edit flows active while the dialog structure changes.

**Step 2: Run test to verify it fails**

Run: `npm test -- --run src/tests/auth-flow.spec.ts src/tests/tenant-page.spec.ts`
Expected: FAIL once dialog affordances diverge from the old markup.

**Step 3: Write minimal implementation**

- Standardize backdrop, card, header, field grouping, footer, and action buttons
- Add shared form hints, stronger focus styles, and better responsive spacing

**Step 4: Run test to verify it passes**

Run: `npm test -- --run src/tests/auth-flow.spec.ts src/tests/tenant-page.spec.ts`
Expected: PASS

**Step 5: Commit**

```bash
git add call-iam-web/src/components/tenant/TenantFormDialog.vue call-iam-web/src/components/user call-iam-web/src/components/role call-iam-web/src/components/department call-iam-web/src/components/audit/AuditDetailDialog.vue
git commit -m "iam-web: unify dialog surfaces"
```

### Task 6: Verify the redesign end to end

**Files:**
- Modify: `call-iam-web/src/tests/auth-flow.spec.ts`
- Modify: `call-iam-web/src/tests/smoke.spec.ts`
- Modify: `call-iam-web/src/tests/tenant-page.spec.ts`

**Step 1: Run focused tests**

Run: `npm test -- --run src/tests/auth-flow.spec.ts src/tests/smoke.spec.ts src/tests/tenant-page.spec.ts`
Expected: PASS

**Step 2: Run the full frontend test suite**

Run: `npm test`
Expected: PASS

**Step 3: Run a production build**

Run: `npm run build`
Expected: PASS

**Step 4: Review the final diff**

Run: `git diff -- call-iam-web docs/plans`
Expected: Shared visual system, refactored views, unified dialogs, and saved design/plan docs only.

**Step 5: Commit**

```bash
git add call-iam-web docs/plans
git commit -m "iam-web: redesign admin console surfaces"
```
