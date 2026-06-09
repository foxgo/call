package com.callcenter.observability.logging;

import java.util.Map;
import org.slf4j.MDC;
import org.springframework.util.StringUtils;

public final class MdcUtils {

    private MdcUtils() {
    }

    public static void put(String key, Object value) {
        if (value == null) {
            MDC.remove(key);
            return;
        }
        String text = String.valueOf(value);
        if (StringUtils.hasText(text)) {
            MDC.put(key, text);
        } else {
            MDC.remove(key);
        }
    }

    public static Map<String, String> copy() {
        return MDC.getCopyOfContextMap();
    }

    public static void restore(Map<String, String> contextMap) {
        if (contextMap == null || contextMap.isEmpty()) {
            MDC.clear();
            return;
        }
        MDC.setContextMap(contextMap);
    }
}
