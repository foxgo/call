package com.callcenter.ingestion.service;

import com.callcenter.common.entity.CallRecordEntity;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CallRecordIndexService {

    private final ElasticsearchBulkService elasticsearchBulkService;

    public CallRecordIndexService(ElasticsearchBulkService elasticsearchBulkService) {
        this.elasticsearchBulkService = elasticsearchBulkService;
    }

    public void indexBatch(List<CallRecordEntity> entities) {
        elasticsearchBulkService.bulkIndexRecords(entities);
    }
}

