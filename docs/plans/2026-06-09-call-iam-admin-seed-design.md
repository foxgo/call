# Call IAM Admin Seed Design

**日期：** 2026-06-09  
**状态：** 已确认，待实现

## 1. 背景

当前 `call-iam` 已经通过 Flyway `V1__init_call_iam_schema.sql` 建立了 IAM 核心表，并初始化了权限与租户内置角色，但没有任何可直接登录的初始账号。

这导致：

- 新环境初始化后无法直接进入 IAM 管理后台
- 平台级租户管理缺少默认入口
- 租户级用户、角色、部门管理缺少默认管理员入口

## 2. 目标

为 `call-iam` 增加数据库初始化种子数据，环境首次迁移完成后自动具备：

- 1 个平台管理帐号
- 1 个默认租户
- 1 个租户管理员帐号

## 3. 非目标

本次不做以下扩展：

- 不新增启动期 Java 自动补数逻辑
- 不改登录/鉴权流程
- 不新增账号创建 API
- 不改现有 `V1` 历史迁移文件

## 4. 已确认决策

- 使用新的 Flyway 迁移文件完成种子初始化
- 保持 `V1` 只负责建表和基础字典
- 平台管理帐号使用 `tenant_id = null`、`user_type = PLATFORM`
- 租户管理员帐号使用 `tenant_id = 默认租户ID`、`user_type = TENANT`
- 租户管理员复用现有内置角色 `TENANT_ADMIN`
- 平台管理员增加独立平台角色 `PLATFORM_ADMIN`
- 初始密码直接写入 BCrypt 哈希，并在文档中明确要求首次登录后改密

## 5. 数据设计

### 5.1 平台角色

新增平台内置角色：

- `role_code = PLATFORM_ADMIN`
- `role_type = PLATFORM_SYSTEM`
- `tenant_id = null`

该角色主要用于补全平台用户在数据层的角色表达。接口鉴权仍复用当前逻辑：JWT 中 `tenantId == null` 自动授予 `ROLE_PLATFORM_ADMIN`。

### 5.2 平台管理员

初始化 1 个平台管理员：

- `username = platform-admin`
- `tenant_id = null`
- `user_type = PLATFORM`
- `status = ENABLE`

并绑定平台角色 `PLATFORM_ADMIN`。

### 5.3 默认租户

初始化 1 个默认租户，作为租户管理员的归属上下文：

- `tenant_code = default`
- `tenant_name = Default Tenant`
- `status = ACTIVE`

### 5.4 租户管理员

初始化 1 个租户管理员：

- `username = tenant-admin`
- `tenant_id = 默认租户ID`
- `user_type = TENANT`
- `status = ENABLE`

并绑定内置角色 `TENANT_ADMIN`。

## 6. 迁移与幂等

新建 `V2__seed_call_iam_admin_accounts.sql`，使用 `INSERT ... ON DUPLICATE KEY UPDATE` 保证在重复执行场景下可安全重放，避免对已存在的唯一键造成失败。

需要覆盖的数据关系：

- `tenant`
- `role`
- `iam_user`
- `user_role`

## 7. 测试策略

补充迁移测试，验证：

- `V1 + V2` 执行后存在平台管理员
- 存在默认租户与租户管理员
- 平台角色与租户管理员角色绑定正确
- 账号密码哈希已落库

## 8. 文档更新

更新 `call-iam/README.md`，补充：

- 默认平台管理员登录方式
- 默认租户管理员登录方式
- 默认租户编码
- 首次登录后必须改密
