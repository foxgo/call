package com.callcenter.ingestion.service;

public interface PostprocessSettings {

    boolean llmEnabled();

    String recordPersistedTopic();

    String analysisCompletedTopic();
}
