package com.callcenter.ingestion.infrastructure.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "call.postprocess")
public class PostprocessProperties {

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
