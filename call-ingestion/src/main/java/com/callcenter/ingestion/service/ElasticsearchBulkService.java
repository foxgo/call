package com.callcenter.ingestion.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import com.callcenter.common.entity.CallRecordEntity;
import com.callcenter.common.entity.CallRoundEntity;
import com.callcenter.ingestion.config.WriteMetrics;
import io.micrometer.core.instrument.Timer;
import java.io.IOException;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ElasticsearchBulkService {

    private final ElasticsearchClient elasticsearchClient;
    private final WriteMetrics writeMetrics;

    public ElasticsearchBulkService(ElasticsearchClient elasticsearchClient, WriteMetrics writeMetrics) {
        this.elasticsearchClient = elasticsearchClient;
        this.writeMetrics = writeMetrics;
    }

    public void bulkIndexRecords(List<CallRecordEntity> entities) {
        BulkRequest.Builder builder = new BulkRequest.Builder();
        entities.forEach(entity -> builder.operations(operation -> operation.index(index -> index
                .index("call_record_write")
                .id(String.valueOf(entity.getCallId()))
                .routing(String.valueOf(entity.getTenantId()))
                .document(buildRecordDocument(entity)))));
        execute(builder.build());
    }

    public void bulkIndexRounds(List<CallRoundEntity> entities) {
        BulkRequest.Builder builder = new BulkRequest.Builder();
        entities.forEach(entity -> builder.operations(operation -> operation.index(index -> index
                .index("call_round_write")
                .id(String.valueOf(entity.getRoundId()))
                .routing(String.valueOf(entity.getTenantId()))
                .document(buildRoundDocument(entity)))));
        execute(builder.build());
    }

    private void execute(BulkRequest request) {
        Timer.Sample sample = Timer.start();
        try {
            BulkResponse response = elasticsearchClient.bulk(request);
            if (response.errors()) {
                throw new IllegalStateException("Elasticsearch bulk request completed with item errors");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write documents to Elasticsearch", exception);
        } finally {
            sample.stop(writeMetrics.esBulkLatency());
        }
    }

    private Map<String, Object> buildRecordDocument(CallRecordEntity entity) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("tenant_id", entity.getTenantId());
        document.put("call_id", String.valueOf(entity.getCallId()));
        putIfNotNull(document, "task_id", entity.getTaskId());
        putIfNotNull(document, "phone", entity.getPhone());
        putIfNotNull(document, "line_number", entity.getLineNumber());
        putIfNotNull(document, "full_text", buildFullText(entity));
        putIfNotNull(document, "call_status", entity.getCallStatus());
        putIfNotNull(document, "duration", entity.getDuration());
        putIfNotNull(document, "start_time", entity.getStartTime());
        putIfNotNull(document, "end_time", entity.getEndTime());
        putIfNotNull(document, "created_at", entity.getCreatedAt());
        return document;
    }

    private Map<String, Object> buildRoundDocument(CallRoundEntity entity) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("tenant_id", entity.getTenantId());
        document.put("call_id", String.valueOf(entity.getCallId()));
        putIfNotNull(document, "round_index", entity.getRoundIndex());
        putIfNotNull(document, "speaker", entity.getSpeaker());
        putIfNotNull(document, "content", entity.getContent());
        putIfNotNull(document, "intent", entity.getIntent());
        putIfNotNull(document, "start_time", entity.getStartTime());
        return document;
    }

    private void putIfNotNull(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private String buildFullText(CallRecordEntity entity) {
        String phone = entity.getPhone() == null ? "" : entity.getPhone();
        String lineNumber = entity.getLineNumber() == null ? "" : entity.getLineNumber();
        String value = (phone + " " + lineNumber).trim();
        return value.isEmpty() ? null : value;
    }
}
