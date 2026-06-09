package com.callcenter.task.config;

import org.apache.rocketmq.spring.autoconfigure.RocketMQAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
// RocketMQ starter 2.2.0 only registers this auto-configuration via spring.factories.
// Spring Boot 3 no longer discovers it automatically, so import it explicitly.
@Import(RocketMQAutoConfiguration.class)
public class RocketMqCompatibilityConfig {
}
