package com.callcenter.ingestion.application.port;

import com.callcenter.ingestion.domain.model.CallRecordData;
import com.callcenter.ingestion.domain.record.CallRecordMessage;

public interface RecordRepository {

    CallRecordData save(CallRecordMessage message);
}
