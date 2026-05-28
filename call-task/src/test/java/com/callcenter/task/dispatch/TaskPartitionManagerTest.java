package com.callcenter.task.dispatch;

import com.callcenter.task.config.CallTaskDispatchProperties;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TaskPartitionManagerTest {

    @Test
    void shouldAcquireOnlyUnownedPartition() {
        @SuppressWarnings("unchecked")
        CallTaskDispatchProperties properties = new CallTaskDispatchProperties();
        ValueOperations<String, String> valueOperations = (ValueOperations<String, String>) Proxy.newProxyInstance(
                ValueOperations.class.getClassLoader(),
                new Class<?>[]{ValueOperations.class},
                (proxy, method, args) -> {
                    if ("setIfAbsent".equals(method.getName())
                            && "call:scheduler:partition:7:owner".equals(args[0])
                            && "instance-a".equals(args[1])
                            && properties.getPartitionLeaseTtl().equals(args[2])) {
                        return true;
                    }
                    throw new UnsupportedOperationException(method.getName());
                }
        );
        StringRedisTemplate redisTemplate = new StringRedisTemplate() {
            @Override
            public ValueOperations<String, String> opsForValue() {
                return valueOperations;
            }
        };
        InstanceIdentityProvider instanceIdentityProvider = new InstanceIdentityProvider() {
            @Override
            public String instanceId() {
                return "instance-a";
            }
        };

        TaskPartitionManager manager = new TaskPartitionManager(
                redisTemplate,
                instanceIdentityProvider,
                properties
        );

        assertTrue(manager.tryAcquire(7));
    }

    @Test
    void shouldTrackOwnedPartitionsAfterSuccessfulAcquire() {
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = (ValueOperations<String, String>) Proxy.newProxyInstance(
                ValueOperations.class.getClassLoader(),
                new Class<?>[]{ValueOperations.class},
                (proxy, method, args) -> {
                    if ("setIfAbsent".equals(method.getName())) {
                        return true;
                    }
                    throw new UnsupportedOperationException(method.getName());
                }
        );
        StringRedisTemplate redisTemplate = new StringRedisTemplate() {
            @Override
            public ValueOperations<String, String> opsForValue() {
                return valueOperations;
            }
        };

        TaskPartitionManager manager = new TaskPartitionManager(
                redisTemplate,
                new InstanceIdentityProvider() {
                    @Override
                    public String instanceId() {
                        return "instance-a";
                    }
                },
                new CallTaskDispatchProperties()
        );

        manager.tryAcquire(7);

        assertEquals(List.of(7), manager.ownedPartitions());
    }

    @Test
    void shouldRefreshOwnershipAcrossConfiguredPartitions() {
        CallTaskDispatchProperties properties = new CallTaskDispatchProperties();
        properties.setPartitionCount(3);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = (ValueOperations<String, String>) Proxy.newProxyInstance(
                ValueOperations.class.getClassLoader(),
                new Class<?>[]{ValueOperations.class},
                (proxy, method, args) -> {
                    if ("setIfAbsent".equals(method.getName())) {
                        return "call:scheduler:partition:1:owner".equals(args[0]);
                    }
                    throw new UnsupportedOperationException(method.getName());
                }
        );
        StringRedisTemplate redisTemplate = new StringRedisTemplate() {
            @Override
            public ValueOperations<String, String> opsForValue() {
                return valueOperations;
            }
        };

        TaskPartitionManager manager = new TaskPartitionManager(
                redisTemplate,
                new InstanceIdentityProvider() {
                    @Override
                    public String instanceId() {
                        return "instance-a";
                    }
                },
                properties
        );

        manager.refreshOwnership();

        assertEquals(List.of(1), manager.ownedPartitions());
    }

    @Test
    void shouldKeepOwnedPartitionWhenRenewSucceeds() {
        CallTaskDispatchProperties properties = new CallTaskDispatchProperties();
        properties.setPartitionCount(2);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(
                "call:scheduler:partition:1:owner",
                "instance-a",
                properties.getPartitionLeaseTtl()
        )).thenReturn(true);
        when(valueOperations.setIfAbsent(
                "call:scheduler:partition:0:owner",
                "instance-a",
                properties.getPartitionLeaseTtl()
        )).thenReturn(false);
        when(redisTemplate.execute(
                org.mockito.ArgumentMatchers.<RedisScript<Long>>any(),
                eq(Collections.singletonList("call:scheduler:partition:1:owner")),
                eq("instance-a"),
                eq(String.valueOf(properties.getPartitionLeaseTtl().toMillis()))
        )).thenReturn(1L);

        TaskPartitionManager manager = new TaskPartitionManager(
                redisTemplate,
                new InstanceIdentityProvider() {
                    @Override
                    public String instanceId() {
                        return "instance-a";
                    }
                },
                properties
        );

        manager.tryAcquire(1);
        manager.refreshOwnership();

        assertEquals(List.of(1), manager.ownedPartitions());
    }

    @Test
    void shouldDropOwnedPartitionWhenRenewFails() {
        CallTaskDispatchProperties properties = new CallTaskDispatchProperties();
        properties.setPartitionCount(2);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(
                "call:scheduler:partition:1:owner",
                "instance-a",
                properties.getPartitionLeaseTtl()
        )).thenReturn(true, false);
        when(valueOperations.setIfAbsent(
                "call:scheduler:partition:0:owner",
                "instance-a",
                properties.getPartitionLeaseTtl()
        )).thenReturn(false);
        when(redisTemplate.execute(
                org.mockito.ArgumentMatchers.<RedisScript<Long>>any(),
                eq(Collections.singletonList("call:scheduler:partition:1:owner")),
                eq("instance-a"),
                eq(String.valueOf(properties.getPartitionLeaseTtl().toMillis()))
        )).thenReturn(0L);

        TaskPartitionManager manager = new TaskPartitionManager(
                redisTemplate,
                new InstanceIdentityProvider() {
                    @Override
                    public String instanceId() {
                        return "instance-a";
                    }
                },
                properties
        );

        manager.tryAcquire(1);
        manager.refreshOwnership();

        assertEquals(List.of(), manager.ownedPartitions());
    }
}
