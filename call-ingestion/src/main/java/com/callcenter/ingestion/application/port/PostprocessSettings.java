package com.callcenter.ingestion.application.port;

public interface PostprocessSettings {

    boolean llmEnabled();

    String recordPersistedTopic();

    String analysisCompletedTopic();
}
