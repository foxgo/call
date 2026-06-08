# Call IAM Web Full Implementation Design

**日期：** 2026-06-05  
**状态：** 已确认，待实现

## 1. 目标

按现有 `call-iam` 接口能力，把 `call-iam-web` 从静态骨架补成可联调、可操作的 IAM 管理台，覆盖：

- 登录与会话管理
- 仪表盘
- 租户管理
- 用户管理
- 角色权限管理
- 部门树管理
- 审计日志

除页面列表外，还要补齐页面内二级管理动作：

- 用户：编辑、删除、启停、重置密码、分配角色、分配部门
- 角色：编辑、删除、分配权限、设置数据范围
- 部门：新建、编辑、移动、删除
- 租户：新建、编辑、删除

## 2. 当前事实

### 2.1 前端现状

`call-iam-web` 已有：

- `Vue 3 + Vite + TypeScript + Pinia + Vue Router + Element Plus`
- 控制台布局、导航和页面骨架
- 极少量 Vitest 测试

但现状问题很明确：

- 页面主体几乎全部是静态假数据
- 表单弹窗没有真实字段和提交流程
- 没有统一 API 类型层
- 没有统一错误提示和加载状态
- 登录页没有真实表单

### 2.2 后端现状

`call-iam` 已提供：

- `/api/iam/tenants`
- `/api/iam/users`
- `/api/iam/roles`
- `/api/iam/permissions`
- `/api/iam/departments`
- `/api/iam/audit-logs`

并且已经具备用户、角色、部门的二级管理接口。

当前关键缺口：

- 前端既有登录流依赖 `/api/iam/auth/login`
- 前端既有登录流依赖 `/api/iam/users/me`
- 仓库内尚未发现对应 REST Controller

因此要实现“页面全部可用”，必须同时补上认证登录接口和当前用户信息接口。

## 3. 实现范围

### 3.1 登录与会话

- 登录页提供账号、密码输入
- 调用真实登录接口获取 `accessToken` 与 `refreshToken`
- 登录后加载当前用户摘要
- 请求头统一注入 `Authorization: Bearer <token>`
- 401 自动清空会话并跳回登录页

### 3.2 仪表盘

不新增聚合后端接口，直接前端聚合现有接口数据，展示：

- 租户总数
- 用户总数
- 角色总数
- 最近审计记录数

同时显示最近审计动作和快捷入口。

### 3.3 租户管理

- 列表查询
- 新建租户
- 编辑租户名称、到期时间
- 删除租户

### 3.4 用户管理

- 列表查询
- 按部门筛选
- 新建用户
- 编辑用户资料
- 删除用户
- 修改状态
- 重置密码
- 分配角色
- 分配部门

### 3.5 角色权限管理

- 列表查询
- 新建角色
- 编辑角色基础信息
- 删除角色
- 加载权限列表
- 分配权限
- 设置数据范围

### 3.6 部门树管理

- 树形加载
- 新建部门
- 编辑部门
- 移动部门
- 删除部门

### 3.7 审计日志

- 列表查询
- 按 `operatorId / resourceType / resourceId` 筛选
- 查看单条详情

## 4. 页面与组件设计

### 4.1 页面层

保留现有路由结构，重做以下页面实现：

- `src/views/LoginView.vue`
- `src/views/DashboardView.vue`
- `src/views/tenant/TenantListView.vue`
- `src/views/user/UserListView.vue`
- `src/views/role/RoleListView.vue`
- `src/views/department/DepartmentTreeView.vue`
- `src/views/audit/AuditLogView.vue`

### 4.2 组件层

保留现有对话框组件路径，但改成真实表单组件：

- `src/components/tenant/TenantFormDialog.vue`
- `src/components/user/UserFormDialog.vue`
- `src/components/role/RoleFormDialog.vue`

并按需要新增：

- 用户分配角色弹窗
- 用户分配部门弹窗
- 用户重置密码弹窗
- 角色数据范围弹窗
- 部门表单弹窗
- 部门移动弹窗
- 审计详情抽屉或弹窗

### 4.3 API 层

新增或重构 `src/api` 下的模块化服务：

- `auth.ts`
- `tenant.ts`
- `user.ts`
- `role.ts`
- `department.ts`
- `audit.ts`
- `types.ts`

统一负责：

- 请求方法封装
- 响应解包
- 类型定义
- 日期字段映射

## 5. 后端补充设计

### 5.1 认证接口

新增：

- `POST /api/iam/auth/login`
- `POST /api/iam/auth/refresh`

`login` 返回：

- `accessToken`
- `refreshToken`

直接复用已有 `LoginUseCase`。

### 5.2 当前用户接口

新增：

- `GET /api/iam/users/me`

返回最小当前用户信息：

- `userId`
- `displayName`
- `tenantId`
- `roleIds`
- `departmentIds`

其中 `displayName` 优先取 `nickname`，为空时回退 `username`。

## 6. 交互与状态约束

- 所有异步操作都要有加载态
- 成功操作统一弹成功提示并刷新列表
- 后端业务异常统一展示错误信息
- 删除操作必须二次确认
- 编辑弹窗进入时加载当前数据或预填已有行数据
- 权限和部门候选项需在弹窗打开前或打开时加载

## 7. 测试策略

### 7.1 后端

先补接口级 `WebMvcTest`：

- 认证登录成功
- 刷新令牌成功
- 当前用户信息查询成功

### 7.2 前端

先补 Vitest：

- `auth` store 登录、401 清会话、路由守卫
- 页面加载接口数据
- 对话框提交流程调用正确 API
- 关键筛选和二级动作交互

### 7.3 验证

最少执行：

- `npm test`
- `npm run build`
- 必要后端测试子集

## 8. 非目标

本次不做：

- 细粒度前端按钮级权限控制
- 登录页验证码、记住我、多因素认证
- 仪表盘专用后端聚合查询
- 审计导出

## 9. 结果标准

满足以下条件视为完成：

- `call-iam-web` 所有菜单页面都不再依赖静态假数据
- 页面中的核心二级管理动作全部可通过真实接口完成
- 前端登录流程可跑通
- 后端为前端补齐最小认证缺口
- 相关测试和构建命令通过
