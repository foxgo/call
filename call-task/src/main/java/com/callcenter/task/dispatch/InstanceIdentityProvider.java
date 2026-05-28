package com.callcenter.task.dispatch;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.springframework.stereotype.Component;

@Component
public class InstanceIdentityProvider {

    public String instanceId() {
        try {
            return "%s-%s".formatted(
                    InetAddress.getLocalHost().getHostName(),
                    ManagementFactory.getRuntimeMXBean().getName()
            );
        } catch (UnknownHostException exception) {
            return ManagementFactory.getRuntimeMXBean().getName();
        }
    }
}
