package com.callcenter.ingestion.infrastructure.config;

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
    }

    public static class Consumers {

        @Valid
        private Consumer record = new Consumer("call-record-consumer-group", 6, 3);

        @Valid
        private Consumer round = new Consumer("call-round-consumer-group", 6, 3);

        @Valid
        private Consumer index = new Consumer("call-index-group", 2, 3);

        @Valid
        private Consumer recordDlq = new Consumer("call-record-dlq-group", 1, 3);

        @Valid
        private Consumer roundDlq = new Consumer("call-round-dlq-group", 1, 3);

        @Valid
        private Consumer indexDlq = new Consumer("call-index-dlq-group", 1, 3);

        @Valid
        private Consumer ai = new Consumer("call-ai-group", 2, 3);

        @Valid
        private Consumer aiDlq = new Consumer("call-ai-dlq-group", 1, 3);

        @Valid
        private Consumer thirdParty = new Consumer("call-third-party-group", 2, 3);

        @Valid
        private Consumer thirdPartyDlq = new Consumer("call-third-party-dlq-group", 1, 3);

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

        public Consumer getRecordDlq() {
            return recordDlq;
        }

        public void setRecordDlq(Consumer recordDlq) {
            this.recordDlq = recordDlq;
        }

        public Consumer getRoundDlq() {
            return roundDlq;
        }

        public void setRoundDlq(Consumer roundDlq) {
            this.roundDlq = roundDlq;
        }

        public Consumer getIndexDlq() {
            return indexDlq;
        }

        public void setIndexDlq(Consumer indexDlq) {
            this.indexDlq = indexDlq;
        }

        public Consumer getAi() {
            return ai;
        }

        public void setAi(Consumer ai) {
            this.ai = ai;
        }

        public Consumer getAiDlq() {
            return aiDlq;
        }

        public void setAiDlq(Consumer aiDlq) {
            this.aiDlq = aiDlq;
        }

        public Consumer getThirdParty() {
            return thirdParty;
        }

        public void setThirdParty(Consumer thirdParty) {
            this.thirdParty = thirdParty;
        }

        public Consumer getThirdPartyDlq() {
            return thirdPartyDlq;
        }

        public void setThirdPartyDlq(Consumer thirdPartyDlq) {
            this.thirdPartyDlq = thirdPartyDlq;
        }
    }

    public static class Consumer {

        @NotBlank
        private String group;

        @Min(1)
        private int consumeThreadMax;

        private int maxReconsumeTimes = 3;

        public Consumer() {
        }

        public Consumer(String group, int consumeThreadMax, int maxReconsumeTimes) {
            this.group = group;
            this.consumeThreadMax = consumeThreadMax;
            this.maxReconsumeTimes = maxReconsumeTimes;
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

        public int getMaxReconsumeTimes() {
            return maxReconsumeTimes;
        }

        public void setMaxReconsumeTimes(int maxReconsumeTimes) {
            this.maxReconsumeTimes = maxReconsumeTimes;
        }
    }
}
