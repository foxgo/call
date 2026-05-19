package com.callcenter.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
        "call.shard.db-count=1",
        "call.shard.table-count=16",
        "call.elasticsearch.auto-init=true"
})
class CallSearchIntegrationTest {

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
    private ElasticsearchClient elasticsearchClient;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() throws Exception {
        indexRecord("10001", 9L, "13800138000", "021-8000", 1, 300, "2026-05-19T10:00:00", "billing complaint followup");
        indexRecord("10002", 9L, "13800138001", "021-8001", 0, 120, "2026-05-19T11:00:00", "routine callback");
        indexRecord("10003", 10L, "13800138000", "021-9000", 1, 180, "2026-05-19T12:00:00", "other tenant complaint");
        indexRound("20001", 9L, "10001", 1, "AGENT", "billing complaint followup", "GREETING", "2026-05-19T10:00:10");
        indexRound("20002", 9L, "10001", 2, "CUSTOMER", "please explain the bill", "QUESTION", "2026-05-19T10:00:40");
        elasticsearchClient.indices().refresh(refresh -> refresh.index("call_record_write"));
        elasticsearchClient.indices().refresh(refresh -> refresh.index("call_round_write"));
    }

    @Test
    void shouldSearchByTenantPhoneAndStatus() throws Exception {
        mockMvc.perform(get("/api/v1/{tenantId}/call-records", 9L)
                        .param("phone", "13800138000")
                        .param("callStatus", "1")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.source").value("elasticsearch"))
                .andExpect(jsonPath("$.content[0].callId").value("10001"))
                .andExpect(jsonPath("$.content[0].tenantId").value(9))
                .andExpect(jsonPath("$.content[0].phone").value("13800138000"));
    }

    @Test
    void shouldSearchByKeywordWithinTenant() throws Exception {
        mockMvc.perform(get("/api/v1/{tenantId}/call-records", 9L)
                        .param("keyword", "complaint")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.content[0].callId").value("10001"));
    }

    @Test
    void shouldSupportSort() throws Exception {
        mockMvc.perform(get("/api/v1/{tenantId}/call-records", 9L)
                        .param("sortBy", "duration")
                        .param("sortOrder", "asc")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].callId").value("10002"))
                .andExpect(jsonPath("$.content[1].callId").value("10001"));
    }

    @Test
    void shouldSupportHighlight() throws Exception {
        mockMvc.perform(get("/api/v1/{tenantId}/call-records", 9L)
                        .param("keyword", "complaint")
                        .param("highlight", "true")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].callId").value("10001"))
                .andExpect(jsonPath("$.content[0].highlights").isArray())
                .andExpect(jsonPath("$.content[0].highlights[0]").exists());
    }

    @Test
    void shouldReturnCallDetailWithRounds() throws Exception {
        mockMvc.perform(get("/api/v1/{tenantId}/call-records/{callId}", 9L, "10001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.callId").value("10001"))
                .andExpect(jsonPath("$.lineNumber").value("021-8000"))
                .andExpect(jsonPath("$.rounds[0].roundIndex").value(1))
                .andExpect(jsonPath("$.rounds[1].intent").value("QUESTION"));
    }

    private void indexRecord(
            String callId,
            long tenantId,
            String phone,
            String lineNumber,
            int callStatus,
            int duration,
            String startTime,
            String fullText
    ) throws Exception {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("tenant_id", tenantId);
        doc.put("call_id", callId);
        doc.put("task_id", 501L);
        doc.put("phone", phone);
        doc.put("line_number", lineNumber);
        doc.put("call_status", callStatus);
        doc.put("duration", duration);
        doc.put("start_time", startTime);
        doc.put("end_time", startTime);
        doc.put("full_text", fullText);
        doc.put("created_at", startTime);

        elasticsearchClient.index(index -> index
                .index("call_record_write")
                .id(callId)
                .routing(Long.toString(tenantId))
                .document(doc)
        );
    }

    private void indexRound(
            String roundId,
            long tenantId,
            String callId,
            int roundIndex,
            String speaker,
            String content,
            String intent,
            String startTime
    ) throws Exception {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("tenant_id", tenantId);
        doc.put("call_id", callId);
        doc.put("round_index", roundIndex);
        doc.put("speaker", speaker);
        doc.put("content", content);
        doc.put("intent", intent);
        doc.put("start_time", startTime);

        elasticsearchClient.index(index -> index
                .index("call_round_write")
                .id(roundId)
                .routing(Long.toString(tenantId))
                .document(doc)
        );
    }
}
