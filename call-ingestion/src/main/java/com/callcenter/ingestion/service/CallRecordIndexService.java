package com.callcenter.ingestion.service;

import com.callcenter.common.dto.DomainEventMessage;
import com.callcenter.common.entity.CallRecordEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CallRecordIndexService {

    private final ObjectMapper objectMapper;
    private final ElasticsearchBulkService elasticsearchBulkService;

    public CallRecordIndexService(
            ObjectMapper objectMapper,
            ElasticsearchBulkService elasticsearchBulkService
    ) {
        this.objectMapper = objectMapper;
        this.elasticsearchBulkService = elasticsearchBulkService;
    }

    public void indexBatch(List<CallRecordEntity> entities) {
        elasticsearchBulkService.bulkIndexRecords(entities);
    }

    public void indexPersistedEvent(DomainEventMessage event) {
        try {
            elasticsearchBulkService.bulkIndexRecords(List.of(objectMapper.treeToValue(event.payload(), CallRecordEntity.class)));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to deserialize record persisted event payload", exception);
        }
    }
}
