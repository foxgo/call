package com.callcenter.task.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "call.task.rocketmq")
public class CallTaskRocketMqProperties {

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
        private String dispatch = "call_dispatch_topic";

        public String getDispatch() {
            return dispatch;
        }

        public void setDispatch(String dispatch) {
            this.dispatch = dispatch;
        }
    }
}
