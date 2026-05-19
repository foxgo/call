package com.callcenter.ingestion.service;

import com.callcenter.common.entity.CallRoundEntity;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CallRoundIndexService {

    private final ElasticsearchBulkService elasticsearchBulkService;

    public CallRoundIndexService(ElasticsearchBulkService elasticsearchBulkService) {
        this.elasticsearchBulkService = elasticsearchBulkService;
    }

    public void indexBatch(List<CallRoundEntity> entities) {
        elasticsearchBulkService.bulkIndexRounds(entities);
    }
}

