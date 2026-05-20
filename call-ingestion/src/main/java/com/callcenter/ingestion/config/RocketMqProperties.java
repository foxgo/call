package com.callcenter.ingestion.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "call.rocketmq")
public class RocketMqProperties {

    @NotBlank
    private String nameServer = "localhost:9876";

    @NotBlank
    private String producerGroup = "call-producer-group";

    @Valid
    private Topics topics = new Topics();

    @Valid
    private Consumers consumers = new Consumers();

    public String getNameServer() {
        return nameServer;
    }

    public void setNameServer(String nameServer) {
        this.nameServer = nameServer;
    }

    public String getProducerGroup() {
        return producerGroup;
    }

    public void setProducerGroup(String producerGroup) {
        this.producerGroup = producerGroup;
    }

    public Topics getTopics() {
        return topics;
    }

    public void setTopics(Topics topics) {
        this.topics = topics;
    }

    public Consumers getConsumers() {
        return consumers;
    }

    public void setConsumers(Consumers consumers) {
        this.consumers = consumers;
    }

    public static class Topics {

        @NotBlank
        private String recordIngest = "call_record_ingest";

        @NotBlank
        private String roundIngest = "call_round_ingest";

        @NotBlank
        private String recordDlq = "call_record_dlq";

        @NotBlank
        private String roundDlq = "call_round_dlq";

        public String getRecordIngest() {
            return recordIngest;
        }

        public void setRecordIngest(String recordIngest) {
            this.recordIngest = recordIngest;
        }

        public String getRoundIngest() {
            return roundIngest;
        }

        public void setRoundIngest(String roundIngest) {
            this.roundIngest = roundIngest;
        }

        public String getRecordDlq() {
            return recordDlq;
        }

        public void setRecordDlq(String recordDlq) {
            this.recordDlq = recordDlq;
        }

        public String getRoundDlq() {
            return roundDlq;
        }

        public void setRoundDlq(String roundDlq) {
            this.roundDlq = roundDlq;
        }
    }

    public static class Consumers {

        @Valid
        private Consumer record = new Consumer("call-record-consumer-group", 6);

        @Valid
        private Consumer round = new Consumer("call-round-consumer-group", 6);

        @Valid
        private Consumer index = new Consumer("call-index-group", 2);

        @Valid
        private Consumer ai = new Consumer("call-ai-group", 2);

        @Valid
        private Consumer thirdParty = new Consumer("call-third-party-group", 2);

        public Consumer getRecord() {
            return record;
        }

        public void setRecord(Consumer record) {
            this.record = record;
        }

        public Consumer getRound() {
            return round;
        }

        public void setRound(Consumer round) {
            this.round = round;
        }

        public Consumer getIndex() {
            return index;
        }

        public void setIndex(Consumer index) {
            this.index = index;
        }

        public Consumer getAi() {
            return ai;
        }

        public void setAi(Consumer ai) {
            this.ai = ai;
        }

        public Consumer getThirdParty() {
            return thirdParty;
        }

        public void setThirdParty(Consumer thirdParty) {
            this.thirdParty = thirdParty;
        }
    }

    public static class Consumer {

        @NotBlank
        private String group;

        @Min(1)
        private int consumeThreadMax;

        public Consumer() {
        }

        public Consumer(String group, int consumeThreadMax) {
            this.group = group;
            this.consumeThreadMax = consumeThreadMax;
        }

        public String getGroup() {
            return group;
        }

        public void setGroup(String group) {
            this.group = group;
        }

        public int getConsumeThreadMax() {
            return consumeThreadMax;
        }

        public void setConsumeThreadMax(int consumeThreadMax) {
            this.consumeThreadMax = consumeThreadMax;
        }
    }
}
