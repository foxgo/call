package com.callcenter.ingestion.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "call.postprocess")
public class PostprocessProperties {

    @Valid
    private Topics topics = new Topics();

    public Topics getTopics() {
        return topics;
    }

    public void setTopics(Topics topics) {
        this.topics = topics;
    }

    public static class Topics {

        @NotBlank
        private String recordPersisted = "call_record_persisted";

        public String getRecordPersisted() {
            return recordPersisted;
        }

        public void setRecordPersisted(String recordPersisted) {
            this.recordPersisted = recordPersisted;
        }
    }
}
