package com.callcenter.observability.logging.mq;

import com.callcenter.observability.logging.MdcUtils;
import com.callcenter.observability.logging.StructuredLogFields;
import java.util.Map;
import org.apache.rocketmq.common.message.MessageExt;

public class MqLoggingContext implements AutoCloseable {

    private final MessageExt messageExt;
    private Map<String, String> previousContext;

    public MqLoggingContext(MessageExt messageExt) {
        this.messageExt = messageExt;
    }

    public void open() {
        previousContext = MdcUtils.copy();
        MdcUtils.put(StructuredLogFields.TOPIC, messageExt.getTopic());
        MdcUtils.put(StructuredLogFields.MESSAGE_ID, messageExt.getMsgId());
        MdcUtils.put(StructuredLogFields.MESSAGE_KEYS, messageExt.getKeys());
        MdcUtils.put(StructuredLogFields.QUEUE_ID, messageExt.getQueueId());
        MdcUtils.put(StructuredLogFields.QUEUE_OFFSET, messageExt.getQueueOffset());
        MdcUtils.put(StructuredLogFields.RECONSUME_TIMES, messageExt.getReconsumeTimes());
    }

    @Override
    public void close() {
        MdcUtils.restore(previousContext);
    }
}
