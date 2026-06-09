package com.callcenter.observability.logging;

import java.util.Map;
import org.springframework.core.task.TaskDecorator;

public class MdcTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        Map<String, String> callerContext = MdcUtils.copy();
        return () -> {
            Map<String, String> previous = MdcUtils.copy();
            try {
                MdcUtils.restore(callerContext);
                runnable.run();
            } finally {
                MdcUtils.restore(previous);
            }
        };
    }
}
