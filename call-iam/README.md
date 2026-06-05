# call-iam

`call-iam` 是统一身份中心服务，负责：

- 用户登录、JWT 访问令牌与刷新令牌
- 平台级租户管理
- 租户内用户、角色、部门与权限管理
- 审计日志查询

## 本地运行

从仓库根目录执行：

```bash
mvn -pl call-iam -am spring-boot:run
```

默认端口：`8085`

## 依赖环境变量

- `MYSQL_HOST`
- `MYSQL_PORT`
- `MYSQL_USER`
- `MYSQL_PASSWORD`
- `REDIS_HOST`
- `ROCKETMQ_NAME_SERVER`
- `ES_URI`
- `ES_USERNAME`
- `ES_PASSWORD`

## 部署

- Dockerfile: [Dockerfile](/Users/johnny/github/call/call-iam/Dockerfile)
- Compose service: [deploy/docker-compose.yml](/Users/johnny/github/call/deploy/docker-compose.yml)
- Kubernetes deployment: [deploy/k8s/call-iam-deployment.yaml](/Users/johnny/github/call/deploy/k8s/call-iam-deployment.yaml)
