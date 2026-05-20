package com.callcenter.ingestion.service;

import com.callcenter.common.dto.DomainEventMessage;
import com.callcenter.common.entity.CallRoundEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CallRoundIndexService {

    private final ObjectMapper objectMapper;
    private final ElasticsearchBulkService elasticsearchBulkService;

    public CallRoundIndexService(
            ObjectMapper objectMapper,
            ElasticsearchBulkService elasticsearchBulkService
    ) {
        this.objectMapper = objectMapper;
        this.elasticsearchBulkService = elasticsearchBulkService;
    }

    public void indexBatch(List<CallRoundEntity> entities) {
        elasticsearchBulkService.bulkIndexRounds(entities);
    }

    public void indexPersistedEvent(DomainEventMessage event) {
        try {
            elasticsearchBulkService.bulkIndexRounds(List.of(objectMapper.treeToValue(event.payload(), CallRoundEntity.class)));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to deserialize round persisted event payload", exception);
        }
    }
}
