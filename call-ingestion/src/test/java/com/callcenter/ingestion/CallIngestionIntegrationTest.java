package com.callcenter.ingestion;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.callcenter.common.dto.CallRecordMessage;
import com.callcenter.common.dto.CallRoundMessage;
import com.callcenter.common.route.ShardKey;
import com.callcenter.common.route.ShardingRouter;
import com.callcenter.common.util.ShardedSnowflakeIdGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext
@EmbeddedKafka(partitions = 1, topics = {"call_record_topic", "call_round_topic"})
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "call.shard.db-count=1",
        "call.shard.table-count=16",
        "call.elasticsearch.auto-init=true",
        "call.kafka.topics.record=call_record_topic",
        "call.kafka.topics.round=call_round_topic",
        "call.kafka.topics.record-dlq=call_record_dlq",
        "call.kafka.topics.round-dlq=call_round_dlq"
})
class CallIngestionIntegrationTest {

    private static final DateTimeFormatter YEAR_MONTH = DateTimeFormatter.ofPattern("yyyyMM");

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("call_0")
            .withUsername("call")
            .withPassword("call123");

    @Container
    static final ElasticsearchContainer ELASTICSEARCH =
            new ElasticsearchContainer(DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:8.13.4"))
                    .withEnv("xpack.security.enabled", "false");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("call.datasource.nodes[0].index", () -> 0);
        registry.add("call.datasource.nodes[0].host", MYSQL::getHost);
        registry.add("call.datasource.nodes[0].port", MYSQL::getFirstMappedPort);
        registry.add("call.datasource.nodes[0].database", () -> "call_0");
        registry.add("call.datasource.nodes[0].username", MYSQL::getUsername);
        registry.add("call.datasource.nodes[0].password", MYSQL::getPassword);
        registry.add("call.elasticsearch.uris", ELASTICSEARCH::getHttpHostAddress);
        registry.add("call.elasticsearch.username", () -> "");
        registry.add("call.elasticsearch.password", () -> "");
    }

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ShardingRouter shardingRouter;

    @Autowired
    private ShardedSnowflakeIdGenerator idGenerator;

    @Autowired
    private ElasticsearchClient elasticsearchClient;

    @BeforeEach
    void setUp() {
        createRecordTableIfNeeded(4L, "13800138000", LocalDateTime.of(2026, 5, 19, 10, 30));
    }

    @Test
    void shouldConsumeRecordAndPersistToMysqlAndElasticsearch() throws Exception {
        long tenantId = 4L;
        long callId = 9001001L;
        LocalDateTime startTime = LocalDateTime.of(2026, 5, 19, 10, 30);
        LocalDateTime endTime = startTime.plusMinutes(3);
        String phone = "13800138000";

        CallRecordMessage message = new CallRecordMessage(
                callId,
                tenantId,
                7001L,
                phone,
                "021-8000",
                1,
                toEpochMillis(startTime),
                toEpochMillis(endTime),
                180,
                "{\"tag\":\"vip\"}"
        );

        kafkaTemplate.send("call_record_topic", Long.toString(tenantId), objectMapper.writeValueAsString(message));

        String tableName = recordTableName(tenantId, phone, startTime);
        Awaitility.await().untilAsserted(() -> {
            Long count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + tableName + " WHERE call_id = ?",
                    Long.class,
                    callId
            );
            assertThat(count).isEqualTo(1L);
        });

        Awaitility.await().untilAsserted(() -> {
            elasticsearchClient.indices().refresh(refresh -> refresh.index("call_record_write"));
            Map<String, Object> source = elasticsearchClient.get(
                    get -> get.index("call_record_write")
                            .id(Long.toString(callId))
                            .routing(Long.toString(tenantId)),
                    Map.class
            ).source();
            assertThat(source).isNotNull();
            assertThat(source.get("phone")).isEqualTo(phone);
            assertThat(source.get("call_status")).isEqualTo(1);
        });
    }

    @Test
    void shouldConsumeRoundAndPersistToMysqlAndElasticsearch() throws Exception {
        long tenantId = 4L;
        String phone = "13800138001";
        long callId = idGenerator.nextId(phone);
        LocalDateTime startTime = LocalDateTime.of(2026, 5, 19, 11, 0);
        createRoundTableIfNeeded(tenantId, callId, startTime);

        CallRoundMessage message = new CallRoundMessage(
                9102001L,
                tenantId,
                callId,
                1,
                "AGENT",
                "您好，这里是回访电话",
                "GREETING",
                toEpochMillis(startTime)
        );

        kafkaTemplate.send("call_round_topic", Long.toString(tenantId), objectMapper.writeValueAsString(message));

        String tableName = roundTableName(tenantId, callId, startTime);
        Awaitility.await().untilAsserted(() -> {
            Long count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + tableName + " WHERE round_id = ?",
                    Long.class,
                    message.roundId()
            );
            assertThat(count).isEqualTo(1L);
        });

        Awaitility.await().untilAsserted(() -> {
            elasticsearchClient.indices().refresh(refresh -> refresh.index("call_round_write"));
            Map<String, Object> source = elasticsearchClient.get(
                    get -> get.index("call_round_write")
                            .id(Long.toString(message.roundId()))
                            .routing(Long.toString(tenantId)),
                    Map.class
            ).source();
            assertThat(source).isNotNull();
            assertThat(source.get("intent")).isEqualTo("GREETING");
        });
    }

    private void createRecordTableIfNeeded(long tenantId, String phone, LocalDateTime startTime) {
        ShardKey shardKey = shardingRouter.routeRecord(tenantId, phone, startTime);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS %s (
                    call_id BIGINT PRIMARY KEY,
                    tenant_id BIGINT NOT NULL,
                    task_id BIGINT,
                    phone VARCHAR(20) NOT NULL,
                    line_number VARCHAR(20),
                    call_status TINYINT,
                    duration INT,
                    start_time DATETIME,
                    end_time DATETIME,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    INDEX idx_tenant_time (tenant_id, start_time),
                    INDEX idx_task (task_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """.formatted(tableName("call_record", shardKey, startTime)));
    }

    private void createRoundTableIfNeeded(long tenantId, long callId, LocalDateTime startTime) {
        ShardKey shardKey = shardingRouter.routeRound(tenantId, callId, startTime);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS %s (
                    round_id BIGINT PRIMARY KEY,
                    call_id BIGINT NOT NULL,
                    tenant_id BIGINT NOT NULL,
                    round_index INT,
                    speaker VARCHAR(16),
                    content TEXT,
                    intent VARCHAR(64),
                    start_time DATETIME,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    INDEX idx_call (call_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """.formatted(tableName("call_round", shardKey, startTime)));
    }

    private String recordTableName(long tenantId, String phone, LocalDateTime startTime) {
        return tableName("call_record", shardingRouter.routeRecord(tenantId, phone, startTime), startTime);
    }

    private String roundTableName(long tenantId, long callId, LocalDateTime startTime) {
        return tableName("call_round", shardingRouter.routeRound(tenantId, callId, startTime), startTime);
    }

    private String tableName(String prefix, ShardKey shardKey, LocalDateTime startTime) {
        return "%s_%s_%02d".formatted(prefix, YEAR_MONTH.format(startTime), shardKey.tableIndex());
    }

    private long toEpochMillis(LocalDateTime time) {
        return time.toInstant(java.time.ZoneOffset.UTC).toEpochMilli();
    }
}
