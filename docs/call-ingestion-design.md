# 📞 通话完成后处理系统技术方案（生产级）+ 工程骨架

---

> 说明：
> 本文档描述的是项目早期基于 Kafka 的初版技术方案，现已不再代表当前实现。
> 当前 `call-ingestion` 已切换为 RocketMQ 链路，落地方案请以
> [2026-05-20-rocketmq-refactor-design.md](/Users/johnny/github/call/docs/plans/2026-05-20-rocketmq-refactor-design.md:1)
> 和
> [2026-05-20-rocketmq-refactor.md](/Users/johnny/github/call/docs/plans/2026-05-20-rocketmq-refactor.md:1)
> 为准。

---

## 一、系统目标

- 支持日均千万级通话记录写入，高峰期 QPS 约 20k+
- 支持实时查询（单次查询 P99 < 1s）
- 多租户严格数据隔离（物理分库 + 路由字段）
- 系统可水平扩展，无单点故障
- 全链路高可用，单节点故障不影响整体服务
- 失败自动恢复（重试 + 死信队列）
- 幂等写入，防止重复消费导致数据冗余
- 可观测性强（指标监控 + 分布式链路追踪 + 告警）
- 服务拆分：
    - `call-write-service`：负责通话记录消费和写入
    - `call-query-service`：查询服务（暂不实现，但预留接口和基础设施）
- 公共模块 `call-common`：持久层、数据对象、工具类等共享 jar

---

## 二、整体架构

```
┌──────────┐    ┌─────────────────────────────────────────────────────────────────────────────┐
│ Dialer    │    │  Kafka Cluster                                                              │
│ (Producer)│───▶│  ┌─────────────────────┐    ┌─────────────────────┐                         │
└──────────┘    │  │ call_record_topic    │    │ call_round_topic     │                         │
                │  │ (partitioned by      │    │ (partitioned by      │                         │
                │  │  tenant_id)          │    │  tenant_id)          │                         │
                │  └─────────┬───────────┘    └──────────┬──────────┘                         │
                └────────────┼───────────────────────────┼───────────────────────────────────┘
                             │                           │
                             ▼                           ▼
                ┌─────────────────────────────────────────────────────────┐
                │  Consumer Cluster (call-write-service)                  │
                │  ┌──────────────────┐   ┌──────────────────┐           │
                │  │ RecordConsumer   │   │ RoundConsumer    │           │
                │  │ (thread pool)    │   │ (thread pool)    │           │
                │  └────────┬─────────┘   └────────┬─────────┘           │
                │           │                      │                     │
                │           ▼                      ▼                     │
                │  ┌───────────────────────────────────────┐             │
                │  │  Processor + Retry + DLQ               │             │
                │  └──┬───────────────────────┬────────────┘             │
                │     │                       │                          │
                └─────┼───────────────────────┼──────────────────────────┘
                      │                       │
                      ▼                       ▼
           ┌──────────────────┐    ┌──────────────────┐
           │   MySQL (分库分表) │    │  Elasticsearch   │
           └──────────────────┘    └──────────────────┘
```
- **写入链路**：Dialer → Kafka → Consumer → MySQL（持久化）→ ES（实时检索）
- **查询链路**（预留）：call-query-service → ES（主读） / MySQL（穿透）
- **公共依赖**：call-common 包含 MyBatis-plus 实体、动态表名插件、ID 生成器、Kafka/ES 客户端配置、数据脱敏工具等

---

## 三、技术栈

| 组件           | 选型                             | 说明                           |
|---------------|----------------------------------|------------------------------|
| JDK           | 21 (LTS)                         | 虚拟线程（Project Loom）        |
| 框架           | Spring Boot 3.2.x                | 响应式 + 虚拟线程集成           |
| 数据库         | MySQL 8.0+                       | InnoDB，分库分表                |
| ORM           | MyBatis-Plus 3.5.x               | 动态表名、批量插入               |
| 消息队列       | Apache Kafka 3.6+                | 高吞吐，分区有序                 |
| 搜索引擎       | Elasticsearch 8.11+              | ILM、索引模板、别名自动管理       |
| 缓存           | Redis 7.x                        | 分布式锁、租户配置缓存           |
| 分库分表中间件  | 自研路由（基于雪花 ID + 一致性哈希） | 轻量、可控，避免 ShardingSphere 过度抽象 |
| 服务注册/配置  | Nacos / Consul（按需）            | 服务发现与动态配置                |
| 可观测性       | Micrometer + Prometheus + Grafana | 指标暴露，自定义业务指标          |
| 日志           | Logback + JSON 格式               | 统一 traceId，ELK 收集          |
| 分布式 ID      | 雪花算法（改进版，可处理时钟回拨）   | 42 位时间戳 + 10 位机器码 + 12 位序列 |

---

## 四、数据模型

### 4.1 MySQL 分库分表设计

#### 4.1.1 分片规则

- **分库**：按 `tenant_id` 取哈希后对 `DB_COUNT` 取模，库名 `call_{index}`，如 `call_0`。
- **分表**（通话记录）：按月份 + 手机号哈希，表名 `call_record_YYYYMM_{table_index}`，其中 `table_index = hash(phone) % TABLE_COUNT_PER_DB`。
- **分表**（通话轮次）：为保障与通话记录“同库同表族”，轮次表不按 `call_id` 独立分片，而是 **跟随通话记录的分片**，即 `table_index` 与所属通话记录相同，由 `call_id` 中携带的分片信息推导。

> 同库同表族保证：通话记录 ID 使用定制雪花 ID，其 **12 位序列前 4 位** 用于标记该记录所在的分表后缀（`table_index` 使用 0~15），轮次表分表后缀直接使用该值。这样同一个通话的所有轮次都与通话记录落在同一分表后缀，并可绑定在同一数据库事务内。

**雪花 ID 结构（64 bit）**：
```
┌─┬────────────────────┬───────────┬──────────────┬────────────────┐
│0│ 42 bit timestamp    │ 10 bit    │ 4 bit shard   │ 8 bit sequence │
│ │ (ms, 自定义epoch)   │ machine   │ (分表后缀)    │                │
└─┴────────────────────┴───────────┴──────────────┴────────────────┘
```
- 42 位时间戳：可用约 139 年。
- 10 位机器码：支持 1024 个 worker（可混入 datacenter）。
- 4 位分表后缀：范围 0~15，对应 16 个分表。生成时根据 `phone` 的哈希 % 16 计算，并写入 ID。
- 8 位序列号：每毫秒每 worker 可生成 256 个 ID（对于单租户足够）。

**全局唯一且可路由**：通过 ID 即可解析出分库（从 tenant_id 获取）和分表后缀，无需单独查路由表。

#### 4.1.2 DDL 示例

```sql
-- 通话记录表（库 call_0, 表 call_record_202605_00）
CREATE TABLE call_record_202605_00 (
    call_id       BIGINT PRIMARY KEY,
    tenant_id     BIGINT NOT NULL,
    task_id       BIGINT,
    phone         VARCHAR(20) NOT NULL,
    line_number   VARCHAR(20),
    call_status   TINYINT,
    duration      INT,
    start_time    DATETIME,
    end_time      DATETIME,
    created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_tenant_time (tenant_id, start_time),
    INDEX idx_task (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 通话轮次表（库 call_0, 表 call_round_202605_00）
CREATE TABLE call_round_202605_00 (
    round_id      BIGINT PRIMARY KEY,
    call_id       BIGINT NOT NULL,
    tenant_id     BIGINT NOT NULL,
    round_index   INT,
    speaker       TINYINT,
    content       TEXT,
    intent        VARCHAR(64),
    created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_call (call_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

**动态表名**由 MyBatis-Plus 拦截器根据 `tenant_id` 和 `phone`/`call_id` 自动拼接。

#### 4.1.3 数据库连接池配置

- 每个分库使用独立的 `HikariDataSource`，隔离租户资源。
- 最大连接数：`call-write-service` 中，单个分库连接池 `maximumPoolSize = 20`，总共 DB 数量 × 20。
- 支持动态增减数据源（扩容时新增数据源注册至 Spring 容器）。

### 4.2 Elasticsearch 索引设计

#### 4.2.1 通话记录索引模板（修正原文档错误）

```json
PUT _index_template/call_record_template
{
  "index_patterns": ["call_record_*"],
  "template": {
    "settings": {
      "number_of_shards": 2,
      "number_of_replicas": 1,
      "refresh_interval": "30s",
      "index.lifecycle.name": "call_record_ilm",
      "index.lifecycle.rollover_alias": "call_record_write",
      "routing": {
        "enable": true
      },
      "analysis": {
        "analyzer": {
          "ik_max": {
            "type": "custom",
            "tokenizer": "ik_max_word"
          }
        }
      }
    },
    "mappings": {
      "dynamic": "strict",
      "_routing": {
        "required": true
      },
      "properties": {
        "tenant_id": {"type": "long"},
        "call_id": {"type": "keyword"},
        "task_id": {"type": "long"},
        "phone": {"type": "keyword"},
        "call_status": {"type": "byte"},
        "duration": {"type": "integer"},
        "start_time": {"type": "date"},
        "end_time": {"type": "date"},
        "full_text": {
          "type": "text",
          "analyzer": "ik_max",
          "fields": {
            "keyword": {
              "type": "keyword",
              "ignore_above": 256
            }
          }
        },
        "intents": {"type": "keyword"},
        "tags": {"type": "keyword"},
        "risk_flag": {"type": "boolean"},
        "quality_score": {"type": "float"},
        "round_count": {"type": "integer"},
        "ai_version": {"type": "keyword"},
        "ext": {"type": "object", "enabled": false},
        "created_at": {"type": "date"}
      }
    }
  }
}
```

#### 4.2.2 通话轮次索引模板

```json
PUT _index_template/call_round_template
{
  "index_patterns": ["call_round_*"],
  "template": {
    "settings": {
      "number_of_shards": 2,
      "number_of_replicas": 1,
      "refresh_interval": "30s",
      "index.lifecycle.name": "call_round_ilm",
      "index.lifecycle.rollover_alias": "call_round_write",
      "routing": {
        "enable": true
      },
      "analysis": {
        "analyzer": {
          "ik_max": {
            "type": "custom",
            "tokenizer": "ik_max_word"
          }
        }
      }
    },
    "mappings": {
      "dynamic": "strict",
      "_routing": {
        "required": true
      },
      "properties": {
        "tenant_id": {"type": "long"},
        "call_id": {"type": "keyword"},
        "round_index": {"type": "integer"},
        "speaker": {"type": "keyword"},
        "content": {
          "type": "text",
          "analyzer": "ik_max"
        },
        "intent": {"type": "keyword"},
        "start_time": {"type": "date"}
      }
    }
  }
}
```

#### 4.2.3 ILM 策略

```json
PUT _ilm/policy/call_record_ilm
{
  "policy": {
    "phases": {
      "hot": {
        "actions": {
          "rollover": {
            "max_size": "30gb",
            "max_age": "7d"
          }
        }
      },
      "warm": {
        "min_age": "30d",
        "actions": {
          "shrink": {
            "number_of_shards": 1
          },
          "forcemerge": {
            "max_num_segments": 1
          }
        }
      },
      "cold": {
        "min_age": "90d",
        "actions": {
          "freeze": {}
        }
      },
      "delete": {
        "min_age": "180d",
        "actions": {
          "delete": {}
        }
      }
    }
  }
}
```

通话轮次 ILM 策略 `call_round_ilm` 结构相同。

#### 4.2.4 读写别名与路由

- **写入别名**：`call_record_write`、`call_round_write`（指向最新 hot 索引）
- **读取别名**：`call_record_read`、`call_round_read`（指向全部索引或按需指向 warm/hot）
- **强制路由**：所有读写操作必须携带 `routing=tenant_id`，确保同一租户数据落在同一分片，提升查询性能并隔离资源。
- 自动建索引脚本：在服务启动时调用 ES API 创建索引模板、ILM 策略、初始索引和别名，使用组件 `ElasticsearchInitializer` 实现。

---

## 五、Kafka 设计

| 配置项             | 值                               | 说明                           |
|--------------------|----------------------------------|-------------------------------|
| Topic 名称         | `call_record_topic` / `call_round_topic` | 分开传递通话记录与轮次消息       |
| 分区数             | 初始 32，可按租户数量动态增加      | 保证分区内有序，租户可跨分区     |
| 消息 key           | `tenant_id` 字符串                | 同一租户消息进入同一分区（顺序消费） |
| 消息体             | Protobuf / JSON                   | 建议用 Protobuf 提升性能        |
| 消费者组           | `call-write-group`                | 一个服务实例一个消费者          |
| 消费模式           | 虚拟线程批量消费，手动提交 offset  | 保证处理完成后再提交，防丢失     |
| 死信队列（DLQ）    | `call_record_dlq` / `call_round_dlq` | 重试耗尽消息转入，人工干预       |
| 消息保留时间       | 7 天                              | 便于数据回溯                    |

**消息体设计（示例 Protobuf）：**
```protobuf
message CallRecordMessage {
  int64 tenant_id = 1;
  int64 task_id = 2;
  string phone = 3;
  string line_number = 4;
  int32 call_status = 5;
  int64 start_time = 6;
  int64 end_time = 7;
  int32 duration = 8;
  string ext_json = 9; // 扩展字段
}

message CallRoundMessage {
  int64 tenant_id = 1;
  int64 call_id = 2;       // 已由 Dialer 生成
  int32 round_index = 3;
  string speaker = 4;
  string content = 5;
  string intent = 6;
  int64 start_time = 7;
}
```

---

## 六、写入流程（幂等、事务、重试、死信）

### 6.1 幂等设计

- 通话记录和轮次使用雪花 ID 作为主键，天然幂等（`call_id`、`round_id` 由生产者生成）。
- MySQL 写入使用 `INSERT ... ON DUPLICATE KEY UPDATE`（但实际不需要更新，仅忽略重复）或依赖主键冲突捕获异常。
- ES 写入时使用 `doc_as_upsert` 或直接指定 `_id` 为 `call_id`/`round_id`，重复写入自动覆盖（可接受覆盖，或结合版本号处理）。

### 6.2 Consumer 处理主流程

```
┌───── 从 Kafka 拉取批量记录 ──────────────────────────┐
│  1. 按 (tenant_id, 分表后缀) 分组                      │
│  2. 同组记录批量插入 MySQL（事务包裹）                  │
│      - 同一组内 call_record 和 call_round 在一个事务中 │
│  3. 构建 BulkRequest，按 routing=tenant_id 分组批量写入 ES│
│  4. 全部成功后手动提交 offset                          │
│  5. 失败则按记录级重试，达到最大重试后发送到 DLQ        │
└──────────────────────────────────────────────────┘
```

**关键代码骨架（简化）：**
```java
@KafkaListener(topics = "call_record_topic", groupId = "call-write-group")
public void consume(List<ConsumerRecord<String, byte[]>> records) {
    // 反序列化、分组
    Map<ShardKey, List<CallRecordMessage>> grouped = groupByShard(records);
    
    for (Map.Entry<ShardKey, List<CallRecordMessage>> entry : grouped.entrySet()) {
        ShardKey shard = entry.getKey();
        List<CallRecordMessage> batch = entry.getValue();
        
        try {
            // MySQL 批量插入（同一分库分表事务）
            recordMapper.batchInsertOrIgnore(batch);
            
            // ES 批量索引
            bulkToEs(batch);
            
        } catch (Exception e) {
            // 逐条重试逻辑
            for (CallRecordMessage msg : batch) {
                tryWithRetry(msg);
            }
        }
    }
    // 手动提交 offset（由框架管理或手动 ack）
}
```

### 6.3 重试与死信队列

- **重试策略**：每条消息最多重试 3 次，指数退避（200ms, 1s, 5s）。
- **死信队列**：达到上限仍失败的消息，序列化为原始 Kafka 消息写入 `call_record_dlq` 或 `call_round_dlq`，并记录详细错误日志和 traceId。
- 监控 DLQ 堆积量，超出阈值告警，人工排查补偿。

### 6.4 最终一致性保障

- MySQL 与 ES 间允许短暂的最终一致性（< 2s）。
- 若 ES 批量写入失败，可将失败的文档缓存至 Redis 或本地重试队列，后台异步重放；同时直接提交 MySQL 事务 offset，保证 MySQL 成功。
- 查询时优先查 ES，如未命中可降级查 MySQL（预留）。

---

## 七、分库分表详细路由与扩容

### 7.1 动态数据源与表名

`call-common` 中提供 `ShardingRouter`：
- 根据 `tenantId` 计算库名：`"call_" + (tenantId.hashCode() & Integer.MAX_VALUE) % DB_COUNT`。
- 根据 `phone` 或 `call_id` 提取分表后缀：对于通话记录，从 `phone` 的哈希 % 16 得到后缀 `xx`；对于轮次，直接从 `call_id` 的 4 bit 分片位提取。
- 表名模板：`call_record_{yyyyMM}_{tableIndex}`、`call_round_{yyyyMM}_{tableIndex}`。

MyBatis-Plus 通过自定义 `TableNameHandler` 实现动态表名。

### 7.2 平滑扩容方案

初始配置 `DB_COUNT=4`，每库 `TABLE_COUNT=16`。扩容时：

1. 新增一倍数据库实例（4→8），`DB_COUNT` 改为 8。
2. 采用一致性哈希算法减少数据迁移：使用虚拟节点映射，调整少量租户的路由。
3. 迁移工具按租户逐库迁移旧数据，双写过渡期由配置中心控制。
4. 扩容完成后切换路由规则。

分表后缀始终 0~15，可通过迁移工具在库内将部分表迁移至新库，或者采用“按租户”调整分库，表结构不变。具体细节在运维手册中定义。

### 7.3 连接池管理

- 使用 Spring `AbstractRoutingDataSource` 实现动态数据源，每个分库一个数据源 Bean。
- 启动时根据 `DB_COUNT` 动态注册数据源，配置 HikariCP 连接池参数。

---

## 八、ES 索引自动管理与查询预留

### 8.1 索引自动创建脚本

`call-common` 提供 `ElasticsearchIndexInitializer`：
- 检查并创建 ILM 策略
- 检查并创建索引模板
- 创建初始索引（如 `call_record_000001`）并绑定写入别名
- 启用滚动（rollover）条件
  该组件在服务启动时运行，幂等，不会重复创建。

### 8.2 查询服务预留设计

`call-query-service` 未来实现，接口设计草案：

```java
// 通话记录查询接口
GET /api/v1/{tenantId}/call-records
  ?phone=138xxxx
  &startTime=2026-05-01T00:00:00
  &endTime=2026-05-19T23:59:59
  &keyword=投诉
  &callStatus=1
  &page=0&size=20

// 查询时指定 routing=tenantId，ES查询伪代码：
SearchRequest searchRequest = new SearchRequest("call_record_read");
searchRequest.routing(tenantId.toString());
// 构建 BoolQuery，过滤 tenant_id，时间范围，匹配 keyword 等
```

查询结果将从 `call_record_read` 别名获取，涵盖 warm/hot 索引。对多租户场景，接口层面强制传入 `tenantId`，所有查询操作自动注入 `routing`。

---

## 九、可观测性

### 9.1 指标监控

通过 Micrometer 暴露如下指标：

| 指标名                                | 类型      | 描述                         |
|--------------------------------------|-----------|-----------------------------|
| `call.kafka.lag`                     | Gauge     | 各分区消费者 lag             |
| `call.mysql.insert.latency`          | Timer     | MySQL 批量插入耗时            |
| `call.es.bulk.latency`               | Timer     | ES Bulk 请求耗时             |
| `call.process.error.rate`            | Counter   | 处理错误速率                  |
| `call.dlq.produce`                   | Counter   | 死信队列生产消息数             |
| `call.shard.route.hit`               | Histogram | 分片路由命中分布              |

接入 Prometheus + Grafana 仪表盘。

### 9.2 分布式追踪

- 使用 Spring Cloud Sleuth (或 Micrometer Tracing) 结合 Kafka 传递 traceId。
- 日志配置为 JSON 格式，包含 `traceId`, `spanId`, `tenantId`, `callId`。
- 日志收集至 ELK，便于故障排查。

### 9.3 告警规则

- Kafka Lag > 5000 持续 5 分钟
- ES Bulk 错误率 > 1%
- DLQ 队列堆积 > 100
- 处理耗时 P99 > 2s
- 数据库连接池等待超时

通过 Alertmanager 发送通知（邮件、短信、钉钉）。

---

## 十、高可用与扩展性

- **Kafka**：集群至少 3 节点，副本因子 3，min.insync.replicas=2，生产者 ack=all。
- **Consumer**：以容器化部署，按需水平扩展，消费者数量不超过分区数；使用 K8s HPA 基于 CPU/Lag 自动扩缩。
- **MySQL**：主从复制 + 每个分库一主一从，写主读从（查询服务）或读写分离中间件。
- **ES**：节点角色分离（master、data hot、data warm/cold），3 节点起步；使用 ILM 自动管理数据生命周期。
- **无单点**：所有组件均有冗余，依赖服务注册与发现做故障转移。

---

## 十一、工程骨架

### 11.1 模块结构

```
call-center/
├── call-common/                 # 公共模块
│   ├── entity/                  # 数据对象 (CallRecord, CallRound)
│   ├── dto/                     # Kafka 消息 DTO
│   ├── mapper/                  # MyBatis-Plus Mapper 接口
│   ├── config/                  # 动态表名、ID 生成器、ES 客户端配置
│   ├── util/                    # 雪花 ID、路由工具
│   └── initializer/             # ES 索引初始化、数据源注册
├── call-write-service/          # 写入服务
│   ├── consumer/                # Kafka 消费者监听器
│   ├── processor/               # 消息处理编排（分组、事务）
│   ├── retry/                   # 重试与 DLQ 逻辑
│   ├── service/                 # MySQL/ES 批量写服务
│   └── config/                  # 消费者配置、线程池
├── call-query-service/          # 查询服务（预留）
│   ├── controller/
│   ├── service/
│   └── config/
└── call-ops/                    # 运维工具（数据迁移、索引重建等）
```

### 11.2 关键配置示例（application.yml 片段）

```yaml
spring:
  kafka:
    bootstrap-servers: kafka1:9092,kafka2:9092,kafka3:9092
    consumer:
      group-id: call-write-group
      enable-auto-commit: false
      max-poll-records: 500
      properties:
        allow.auto.create.topics: false

call:
  shard:
    db-count: 4
    table-count: 16
  id:
    machine-id: 1
    epoch: 1700000000000
  elasticsearch:
    uris: es1:9200,es2:9200
    username: elastic
    password: ${ES_PASSWORD}
```

### 11.3 核心代码片段（补充）

**雪花 ID 生成器（含分片位）**：
```java
public synchronized long nextId(long tenantId, String phone) {
    long time = System.currentTimeMillis() - epoch;
    int shard = Math.abs(phone.hashCode()) % TABLE_COUNT; // 0~15
    // 组合：42时间 | 10机器 | 4分片 | 8序列
    return (time << 22) | (workerId << 12) | (shard << 8) | (sequence & 0xFF);
}
```

**动态表名拦截器**：
```java
public class CallTableNameHandler implements TableNameHandler {
    @Override
    public String dynamicTableName(String sql, String tableName) {
        // 从上下文获取分库表后缀
        ShardContext ctx = ShardContextHolder.get();
        if ("call_record".equals(tableName)) {
            return String.format("call_record_%s_%02d", ctx.getYearMonth(), ctx.getTableIndex());
        }
        // 类似处理 call_round
        return tableName;
    }
}
```

**ES Bulk 写入示例（routing 强制）**：
```java
BulkRequest bulkRequest = new BulkRequest();
for (CallRecordDoc doc : docs) {
    IndexRequest indexRequest = new IndexRequest("call_record_write")
        .id(doc.getCallId().toString())
        .routing(doc.getTenantId().toString())
        .source(json, XContentType.JSON);
    bulkRequest.add(indexRequest);
}
BulkResponse response = esClient.bulk(bulkRequest, RequestOptions.DEFAULT);
```

---

## 十二、优化（补充）

- **ES 写入**：Bulk 请求大小控制在 5~15MB，批量条数 1000~2000；定期刷新索引但延迟 30s 降低写压力。
- **MySQL**：使用 INSERT IGNORE 或 ON DUPLICATE KEY 忽略冲突，批量条数 200~500，使用 rewriteBatchedStatements=true 优化批处理。
- **Kafka**：适当增加分区数提高并行度；消费者每次拉取记录数需平衡延迟与吞吐，配置 `max.poll.records=500`。
- **虚拟线程**：使用虚拟线程处理单个分区的批量插入，避免阻塞物理线程，提高 CPU 利用率。
- **序列化**：使用 Protobuf 替代 JSON 减少消息体积和序列化开销。

---

## 附录：未压缩原始设计（确保内容完整）

本文档已整合并修正前版中编号重复、JSON 语法错误等问题，并补充了幂等、重试、死信、路由、查询预留、工程骨架等章节，可作为开发基准。
