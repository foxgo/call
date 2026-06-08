package com.callcenter.ingestion.infrastructure.config;

import com.callcenter.ingestion.application.port.PostprocessSettings;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "call.postprocess")
public class PostprocessProperties implements PostprocessSettings {

    private boolean llmEnabled = false;

    @Valid
    private Topics topics = new Topics();

    public boolean isLlmEnabled() {
        return llmEnabled;
    }

    public void setLlmEnabled(boolean llmEnabled) {
        this.llmEnabled = llmEnabled;
    }

    public Topics getTopics() {
        return topics;
    }

    public void setTopics(Topics topics) {
        this.topics = topics;
    }

    @Override
    public boolean llmEnabled() {
        return llmEnabled;
    }

    @Override
    public String recordPersistedTopic() {
        return topics.getRecordPersisted();
    }

    @Override
    public String analysisCompletedTopic() {
        return topics.getAnalysisCompleted();
    }

    public static class Topics {

        @NotBlank
        private String recordPersisted = "call_record_persisted";

        @NotBlank
        private String analysisCompleted = "call_record_analysis_completed";

        public String getRecordPersisted() {
            return recordPersisted;
        }

        public void setRecordPersisted(String recordPersisted) {
            this.recordPersisted = recordPersisted;
        }

        public String getAnalysisCompleted() {
            return analysisCompleted;
        }

        public void setAnalysisCompleted(String analysisCompleted) {
            this.analysisCompleted = analysisCompleted;
        }
    }
}
