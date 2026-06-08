package com.callcenter.ingestion.application.port;

import com.callcenter.ingestion.domain.model.DeadLetterTaskData;

public interface DeadLetterTaskRepository {

    int insertIgnore(DeadLetterTaskData task);
}
