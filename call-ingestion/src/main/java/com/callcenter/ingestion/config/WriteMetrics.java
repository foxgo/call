package com.callcenter.ingestion.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

public class WriteMetrics {

    private final Timer mysqlInsertLatency;
    private final Timer esBulkLatency;
    private final Counter processErrorRate;
    private final Counter dlqProduce;

    public WriteMetrics(MeterRegistry registry) {
        this.mysqlInsertLatency = registry.timer("call.mysql.insert.latency");
        this.esBulkLatency = registry.timer("call.es.bulk.latency");
        this.processErrorRate = registry.counter("call.process.error.rate");
        this.dlqProduce = registry.counter("call.dlq.produce");
    }

    public Timer mysqlInsertLatency() {
        return mysqlInsertLatency;
    }

    public Timer esBulkLatency() {
        return esBulkLatency;
    }

    public Counter processErrorRate() {
        return processErrorRate;
    }

    public Counter dlqProduce() {
        return dlqProduce;
    }
}
