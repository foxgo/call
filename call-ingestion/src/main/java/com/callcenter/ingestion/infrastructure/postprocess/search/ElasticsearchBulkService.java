package com.callcenter.ingestion.infrastructure.postprocess.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.callcenter.ingestion.domain.shared.MessageKeys;
import com.callcenter.ingestion.infrastructure.analysis.persistence.CallAnalysisResultEntity;
import com.callcenter.ingestion.infrastructure.record.persistence.CallRecordEntity;
import com.callcenter.ingestion.infrastructure.round.persistence.CallRoundEntity;
import com.callcenter.ingestion.support.metrics.WriteMetrics;
import io.micrometer.core.instrument.Timer;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ElasticsearchBulkService {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final ElasticsearchClient elasticsearchClient;
    private final WriteMetrics writeMetrics;
    private final ObjectMapper objectMapper;

    public ElasticsearchBulkService(
            ElasticsearchClient elasticsearchClient,
            WriteMetrics writeMetrics,
            ObjectMapper objectMapper
    ) {
        this.elasticsearchClient = elasticsearchClient;
        this.writeMetrics = writeMetrics;
        this.objectMapper = objectMapper;
    }

    public void bulkIndexRecords(List<CallRecordEntity> entities) {
        bulkIndexRecords(entities, Map.of());
    }

    public void bulkIndexRecords(List<CallRecordEntity> entities, Map<Long, CallAnalysisResultEntity> analysisResults) {
        BulkRequest.Builder builder = new BulkRequest.Builder();
        entities.forEach(entity -> builder.operations(operation -> operation.index(index -> index
                .index("call_record_write")
                .id(MessageKeys.recordDocumentId(entity))
                .routing(String.valueOf(entity.getTenantId()))
                .document(buildRecordDocument(entity, analysisResults.get(entity.getCallId()))))));
        execute(builder.build());
    }

    public void bulkIndexRounds(List<CallRoundEntity> entities) {
        BulkRequest.Builder builder = new BulkRequest.Builder();
        entities.forEach(entity -> builder.operations(operation -> operation.index(index -> index
                .index("call_round_write")
                .id(MessageKeys.roundDocumentId(entity))
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

    private Map<String, Object> buildRecordDocument(CallRecordEntity entity, CallAnalysisResultEntity analysisResult) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("tenant_id", entity.getTenantId());
        document.put("call_id", String.valueOf(entity.getCallId()));
        putIfNotNull(document, "task_id", entity.getTaskId());
        putIfNotNull(document, "phone", entity.getPhone());
        putIfNotNull(document, "line_number", entity.getLineNumber());
        putIfNotNull(document, "full_text", buildFullText(entity));
        putIfNotNull(document, "call_status", entity.getCallStatus());
        putIfNotNull(document, "duration", entity.getDuration());
        putIfNotNull(document, "recording_url", entity.getRecordingUrl());
        putIfNotNull(document, "error_code", entity.getErrorCode());
        putIfNotNull(document, "error_description", entity.getErrorDescription());
        putIfNotNull(document, "hangup_by", entity.getHangupBy());
        putIfNotNull(document, "connected", entity.getConnected());
        putIfNotNull(document, "ring_duration", entity.getRingDuration());
        putIfNotNull(document, "ring_start_time", entity.getRingStartTime());
        putIfNotNull(document, "hangup_time", entity.getHangupTime());
        putIfNotNull(document, "start_time", entity.getStartTime());
        putIfNotNull(document, "end_time", entity.getEndTime());
        putIfNotNull(document, "round_count", entity.getRoundTotal());
        putIfNotNull(document, "tags", readTags(analysisResult));
        putIfNotNull(document, "risk_flag", analysisResult == null ? null : analysisResult.getRiskFlag());
        putIfNotNull(document, "quality_score", analysisResult == null ? null : analysisResult.getQualityScore());
        putIfNotNull(document, "ai_version", analysisResult == null ? null : analysisResult.getAiVersion());
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

    private List<String> readTags(CallAnalysisResultEntity analysisResult) {
        if (analysisResult == null || analysisResult.getTags() == null) {
            return null;
        }
        try {
            return objectMapper.readValue(analysisResult.getTags(), STRING_LIST);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to deserialize analysis tags", exception);
        }
    }
}
