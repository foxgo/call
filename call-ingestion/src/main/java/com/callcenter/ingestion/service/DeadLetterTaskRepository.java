package com.callcenter.ingestion.service;

import com.callcenter.ingestion.model.DeadLetterTaskData;

public interface DeadLetterTaskRepository {

    int insertIgnore(DeadLetterTaskData task);
}
