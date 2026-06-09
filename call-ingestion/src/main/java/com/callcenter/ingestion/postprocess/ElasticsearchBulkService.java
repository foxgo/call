package com.callcenter.ingestion.postprocess;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import com.callcenter.ingestion.service.SearchIndexGateway;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.callcenter.ingestion.model.AnalysisResultData;
import com.callcenter.ingestion.model.CallRecordData;
import com.callcenter.ingestion.model.CallRoundData;
import com.callcenter.ingestion.model.MessageKeys;
import com.callcenter.ingestion.repository.CallAnalysisResultEntity;
import com.callcenter.ingestion.repository.CallRecordEntity;
import com.callcenter.ingestion.repository.CallRoundEntity;
import com.callcenter.ingestion.support.metrics.WriteMetrics;
import io.micrometer.core.instrument.Timer;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ElasticsearchBulkService implements SearchIndexGateway {

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

    @Override
    public void bulkIndexRecordData(List<CallRecordData> entities) {
        bulkIndexRecordData(entities, List.of());
    }

    @Override
    public void bulkIndexRecordData(List<CallRecordData> entities, List<AnalysisResultData> analysisResults) {
        Map<Long, AnalysisResultData> resultsByCallId = new LinkedHashMap<>();
        analysisResults.forEach(result -> resultsByCallId.put(result.callId(), result));
        BulkRequest.Builder builder = new BulkRequest.Builder();
        entities.forEach(entity -> builder.operations(operation -> operation.index(index -> index
                .index("call_record_write")
                .id(MessageKeys.recordDocumentId(entity))
                .routing(String.valueOf(entity.tenantId()))
                .document(buildRecordDocument(entity, resultsByCallId.get(entity.callId()))))));
        execute(builder.build());
    }

    public void bulkIndexRecords(List<CallRecordEntity> entities, Map<Long, CallAnalysisResultEntity> analysisResults) {
        BulkRequest.Builder builder = new BulkRequest.Builder();
        entities.forEach(entity -> builder.operations(operation -> operation.index(index -> index
                .index("call_record_write")
                .id(MessageKeys.recordDocumentId(entity.getCallId()))
                .routing(String.valueOf(entity.getTenantId()))
                .document(buildRecordDocument(entity, analysisResults.get(entity.getCallId()))))));
        execute(builder.build());
    }

    @Override
    public void bulkIndexRoundData(List<CallRoundData> entities) {
        BulkRequest.Builder builder = new BulkRequest.Builder();
        entities.forEach(entity -> builder.operations(operation -> operation.index(index -> index
                .index("call_round_write")
                .id(MessageKeys.roundDocumentId(entity))
                .routing(String.valueOf(entity.tenantId()))
                .document(buildRoundDocument(entity)))));
        execute(builder.build());
    }

    public void bulkIndexRounds(List<CallRoundEntity> entities) {
        BulkRequest.Builder builder = new BulkRequest.Builder();
        entities.forEach(entity -> builder.operations(operation -> operation.index(index -> index
                .index("call_round_write")
                .id(MessageKeys.roundDocumentId(entity.getCallId(), entity.getRoundId()))
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

    private Map<String, Object> buildRecordDocument(CallRecordData entity, AnalysisResultData analysisResult) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("tenant_id", entity.tenantId());
        document.put("call_id", String.valueOf(entity.callId()));
        putIfNotNull(document, "task_id", entity.taskId());
        putIfNotNull(document, "phone", entity.phone());
        putIfNotNull(document, "line_number", entity.lineNumber());
        putIfNotNull(document, "full_text", buildFullText(entity.phone(), entity.lineNumber()));
        putIfNotNull(document, "call_status", entity.callStatus());
        putIfNotNull(document, "duration", entity.duration());
        putIfNotNull(document, "recording_url", entity.recordingUrl());
        putIfNotNull(document, "error_code", entity.errorCode());
        putIfNotNull(document, "error_description", entity.errorDescription());
        putIfNotNull(document, "hangup_by", entity.hangupBy());
        putIfNotNull(document, "connected", entity.connected());
        putIfNotNull(document, "ring_duration", entity.ringDuration());
        putIfNotNull(document, "ring_start_time", entity.ringStartTime());
        putIfNotNull(document, "hangup_time", entity.hangupTime());
        putIfNotNull(document, "start_time", entity.startTime());
        putIfNotNull(document, "end_time", entity.endTime());
        putIfNotNull(document, "round_count", entity.roundTotal());
        putIfNotNull(document, "tags", readTags(analysisResult));
        putIfNotNull(document, "risk_flag", analysisResult == null ? null : analysisResult.riskFlag());
        putIfNotNull(document, "quality_score", analysisResult == null ? null : analysisResult.qualityScore());
        putIfNotNull(document, "ai_version", analysisResult == null ? null : analysisResult.aiVersion());
        putIfNotNull(document, "created_at", entity.createdAt());
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

    private Map<String, Object> buildRoundDocument(CallRoundData entity) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("tenant_id", entity.tenantId());
        document.put("call_id", String.valueOf(entity.callId()));
        putIfNotNull(document, "round_index", entity.roundIndex());
        putIfNotNull(document, "speaker", entity.speaker());
        putIfNotNull(document, "content", entity.content());
        putIfNotNull(document, "intent", entity.intent());
        putIfNotNull(document, "start_time", entity.startTime());
        return document;
    }

    private void putIfNotNull(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private String buildFullText(CallRecordEntity entity) {
        return buildFullText(entity.getPhone(), entity.getLineNumber());
    }

    private String buildFullText(String phoneValue, String lineNumberValue) {
        String phone = phoneValue == null ? "" : phoneValue;
        String lineNumber = lineNumberValue == null ? "" : lineNumberValue;
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

    private List<String> readTags(AnalysisResultData analysisResult) {
        if (analysisResult == null || analysisResult.tags() == null) {
            return null;
        }
        try {
            return objectMapper.readValue(analysisResult.tags(), STRING_LIST);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to deserialize analysis tags", exception);
        }
    }
}
