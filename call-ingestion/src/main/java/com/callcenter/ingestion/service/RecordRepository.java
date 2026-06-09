package com.callcenter.ingestion.service;

import com.callcenter.ingestion.model.CallRecordData;
import com.callcenter.ingestion.model.CallRecordMessage;

public interface RecordRepository {

    CallRecordData save(CallRecordMessage message);
}
