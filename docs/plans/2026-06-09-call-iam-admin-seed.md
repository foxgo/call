# Call IAM Admin Seed Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add Flyway seed data so a fresh `call-iam` environment contains both a platform admin account and a tenant admin account.

**Architecture:** Keep schema creation in `V1` and add a dedicated `V2` Flyway migration for seed data. Verify the behavior through migration-focused tests before updating the SQL and README.

**Tech Stack:** Java 21, Maven, Flyway, Spring JDBC, JUnit 5, Testcontainers, MySQL 8

---

### Task 1: Extend Migration Tests For Seed Data

**Files:**
- Modify: `call-iam/src/test/java/com/callcenter/iam/db/IamSchemaMigrationTest.java`

**Step 1: Write the failing tests**

Add tests that verify:

- migration creates `platform-admin`
- migration creates `default` tenant and `tenant-admin`
- seed role bindings exist for both users

**Step 2: Run test to verify it fails**

Run: `mvn -pl call-iam -Dtest=IamSchemaMigrationTest test`
Expected: FAIL because only `V1` exists and no admin accounts are seeded.

**Step 3: Write minimal implementation**

Update the migration test to execute both `V1__init_call_iam_schema.sql` and `V2__seed_call_iam_admin_accounts.sql`, then assert the seeded rows and bindings.

**Step 4: Run test to verify it passes**

Run: `mvn -pl call-iam -Dtest=IamSchemaMigrationTest test`
Expected: PASS

**Step 5: Commit**

```bash
git add call-iam/src/test/java/com/callcenter/iam/db/IamSchemaMigrationTest.java
git commit -m "iam: add migration coverage for admin seed data"
```

### Task 2: Add Flyway Seed Migration

**Files:**
- Create: `call-iam/src/main/resources/db/migration/V2__seed_call_iam_admin_accounts.sql`

**Step 1: Write the failing test**

Reuse the failing `IamSchemaMigrationTest` expectations from Task 1.

**Step 2: Run test to verify it fails**

Run: `mvn -pl call-iam -Dtest=IamSchemaMigrationTest test`
Expected: FAIL because `V2` does not exist yet.

**Step 3: Write minimal implementation**

Create `V2` to seed:

- platform role `PLATFORM_ADMIN`
- platform user `platform-admin`
- default tenant `default`
- tenant user `tenant-admin`
- role bindings for both users

Use BCrypt password hashes and idempotent inserts.

**Step 4: Run test to verify it passes**

Run: `mvn -pl call-iam -Dtest=IamSchemaMigrationTest test`
Expected: PASS

**Step 5: Commit**

```bash
git add call-iam/src/main/resources/db/migration/V2__seed_call_iam_admin_accounts.sql
git commit -m "iam: seed default admin accounts"
```

### Task 3: Update Module Documentation

**Files:**
- Modify: `call-iam/README.md`

**Step 1: Write the failing check**

Identify that the README does not describe the default accounts or tenant code created by migration.

**Step 2: Run check to verify the gap**

Run: `rg -n "platform-admin|tenant-admin|default tenant|default" call-iam/README.md`
Expected: no matches or incomplete usage guidance.

**Step 3: Write minimal implementation**

Document:

- platform admin username
- tenant admin username
- default tenant code
- mandatory password rotation after first login

**Step 4: Run check to verify it passes**

Run: `rg -n "platform-admin|tenant-admin|default" call-iam/README.md`
Expected: matching documentation lines exist.

**Step 5: Commit**

```bash
git add call-iam/README.md
git commit -m "iam: document seeded admin accounts"
```

### Task 4: Run Targeted Verification

**Files:**
- No file changes

**Step 1: Run migration tests**

Run: `mvn -pl call-iam -Dtest=IamSchemaMigrationTest,IamFlywayMigrationStrategyTest test`
Expected: PASS

**Step 2: Run a broader module safety check**

Run: `mvn -pl call-iam test`
Expected: PASS, or clearly identify unrelated pre-existing failures.

**Step 3: Review docs and migration inventory**

Run: `rg --files call-iam/src/main/resources/db/migration call-iam/README.md`
Expected: includes `V1__init_call_iam_schema.sql`, `V2__seed_call_iam_admin_accounts.sql`, and updated README.

**Step 4: Commit**

```bash
git add docs/plans/2026-06-09-call-iam-admin-seed-design.md docs/plans/2026-06-09-call-iam-admin-seed.md
git commit -m "docs: add iam admin seed design and plan"
```
