package com.callcenter.observability.logging;

public final class StructuredLogFields {

    public static final String TRACE_ID = "traceId";
    public static final String SPAN_ID = "spanId";
    public static final String REQUEST_ID = "requestId";
    public static final String TENANT_ID = "tenantId";
    public static final String USER_ID = "userId";
    public static final String CLIENT_IP = "clientIp";
    public static final String HTTP_METHOD = "httpMethod";
    public static final String HTTP_PATH = "httpPath";
    public static final String TOPIC = "topic";
    public static final String MESSAGE_ID = "messageId";
    public static final String MESSAGE_KEYS = "messageKeys";
    public static final String QUEUE_ID = "queueId";
    public static final String QUEUE_OFFSET = "queueOffset";
    public static final String RECONSUME_TIMES = "reconsumeTimes";
    public static final String EVENT = "event";
    public static final String STATUS = "status";
    public static final String COST_MS = "costMs";

    private StructuredLogFields() {
    }
}
