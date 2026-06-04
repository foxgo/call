
# 角色定义

你是一名拥有10年以上经验的Staff Software Engineer。

请按照企业级生产系统标准设计并实现一个：

```text
AI Outbound SaaS Platform
IAM User Center
```

系统。

要求：

```text
代码可直接生成
工程可直接运行
满足生产环境
```

技术栈固定：

```text
JDK 21

Spring Boot 3.5

Spring Security 6

MyBatis Plus

MySQL 8

Redis 7

RocketMq 5

JWT

Maven

Vue3 + Element Plus
```

采用：

```text
DDD

Clean Architecture

Hexagonal Architecture
```

设计。

---

# 一、系统目标

设计并开发：

```text
IAM User Center
```

作为整个 AI 外呼 SaaS 平台的统一身份认证与授权中心。

负责：

```text
Tenant

Organization

User

Role

Permission

Authentication

Authorization

Audit
```

---

# 二、业务边界

User Center 只负责：

```text
用户认证

用户授权

租户管理

组织架构

数据权限

审计日志
```

不负责：

```text
任务管理

AI Agent

CallerID

CRM

Billing
```

这些通过：

```text
tenant_id
user_id
```

关联。

---

# 三、核心业务模型

## Tenant

租户。

代表一个企业客户。

```java
Tenant
```

属性：

```java
id

tenantCode

tenantName

status

packageId

expireTime

quota

createdAt
```

---

状态机：

```text
ACTIVE

SUSPENDED

EXPIRED

DELETED
```

状态转换：

```text
ACTIVE → SUSPENDED

ACTIVE → EXPIRED

ACTIVE → DELETED

SUSPENDED → ACTIVE
```

禁止：

```text
DELETED → ACTIVE
```

---

# 四、Organization

支持无限级组织架构。

```java
Department
```

树结构：

```text
集团

├── 华东
│    ├── 上海
│    └── 杭州
│
└── 华南
     └── 深圳
```

要求：

```text
支持10000部门

支持无限层级

支持树查询

支持批量移动
```

采用：

```text
Closure Table
```

实现。

---

# 五、User

用户属于租户。

用户可属于多个部门。

```java
User
```

字段：

```java
id

tenantId

username

mobile

email

passwordHash

nickname

avatar

status

lastLoginTime
```

---

状态机：

```text
ENABLE

DISABLE

LOCK
```

---

密码策略：

```text
BCrypt

长度 >= 8

必须包含数字

必须包含大小写字母
```

---

# 六、Role

支持：

```text
系统角色

租户角色

自定义角色
```

---

默认角色：

```text
TENANT_ADMIN

SUPERVISOR

OPERATOR

QA

VIEWER
```

---

# 七、Permission

权限粒度：

```text
resource:action
```

例如：

```text
tenant:create

tenant:update

user:create

user:update

task:view

task:delete
```

---

# 八、RBAC设计

采用：

```text
User

Role

Permission
```

模型。

关系：

```text
User -> Role

Role -> Permission
```

支持：

```text
多角色

动态授权

实时生效
```

---

# 九、ABAC设计

在 RBAC 之上增加：

```text
ABAC
```

支持：

```text
resource.ownerId

resource.creatorId

departmentId

tenantId
```

策略。

示例：

```java
task.pause

allow when

task.creatorId == user.id
```

---

# 十、数据权限

支持：

```java
ALL

DEPARTMENT

DEPARTMENT_AND_CHILD

SELF

CUSTOM
```

---

实现：

```java
RoleDataScope
```

---

查询客户时：

自动注入：

```sql
department_id in (...)
```

过滤条件。

---

# 十一、认证中心

采用：

```text
JWT + Refresh Token
```

架构。

---

AccessToken：

```text
30分钟
```

RefreshToken：

```text
7天
```

---

JWT Claim：

```json
{
  "tenantId":1001,
  "userId":2001,
  "roleIds":[1,2],
  "deptIds":[10,11]
}
```

---

# 十二、审计日志

所有操作必须审计。

记录：

```text
登录

登出

创建用户

删除用户

修改权限

导出数据
```

---

审计字段：

```java
operatorId

tenantId

resourceType

resourceId

request

response

ip

userAgent

createdAt
```

---

# 十三、数据库设计

输出：

```text
ER图

DDL

索引设计

唯一索引

分区策略
```

必须包含：

```text
tenant

user

department

department_closure

role

permission

user_role

role_permission

user_department

role_data_scope

audit_log
```

---

# 十四、接口设计

输出 OpenAPI 3.0。

必须包含：

## Tenant API

```http
POST /api/tenants

GET /api/tenants

GET /api/tenants/{id}

PUT /api/tenants/{id}

DELETE /api/tenants/{id}
```

---

## User API

```http
POST /api/users

GET /api/users

PUT /api/users/{id}

DELETE /api/users/{id}
```

---

## Role API

```http
POST /api/roles

GET /api/roles

PUT /api/roles/{id}
```

---

## Auth API

```http
POST /api/auth/login

POST /api/auth/logout

POST /api/auth/refresh
```

---

# 十五、非功能要求

性能：

```text
1000租户

100000用户

10000 QPS鉴权
```

响应：

```text
P95 < 50ms
```

---

安全：

```text
JWT签名

密码加密

租户隔离

接口限流

SQL注入防护

XSS防护
```

所有查询必须自动带：

```sql
tenant_id
```

防止跨租户访问。 ([AWS 文档][1])

---

# 十六、Codex输出要求

按以下顺序输出：

```text
1. 系统架构设计

2. DDD领域模型

3. ER图

4. 数据库DDL

5. OpenAPI

6. Maven工程结构

7. Domain层代码

8. Application层代码

9. Infrastructure层代码

10. Controller代码

11. Security配置

12. JWT实现

13. 单元测试

14. Docker部署

15. Kubernetes部署

16. README
```

每个代码文件：

```text
必须给出完整路径

例如：

src/main/java/com/company/iam/domain/user/User.java
```

禁止省略代码。

---
