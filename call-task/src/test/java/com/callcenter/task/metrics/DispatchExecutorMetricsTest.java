package com.callcenter.task.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DispatchExecutorMetricsTest {

    @Test
    void shouldRegisterSendExecutorGaugesAndReflectRuntimeLoad() throws InterruptedException {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(1);
        executor.setThreadNamePrefix("dispatch-send-test-");
        executor.initialize();

        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            new DispatchExecutorMetrics(executor, meterRegistry);

            executor.execute(() -> {
                started.countDown();
                awaitLatch(release);
            });
            assertTrue(started.await(1, TimeUnit.SECONDS));

            executor.execute(() -> {
            });
            assertTrue(waitUntilQueueSize(executor, 1));

            assertNotNull(meterRegistry.find("call.task.dispatch.send.executor.active").gauge());
            assertNotNull(meterRegistry.find("call.task.dispatch.send.executor.queue.size").gauge());
            assertEquals(1.0d, meterRegistry.find("call.task.dispatch.send.executor.active").gauge().value());
            assertEquals(1.0d, meterRegistry.find("call.task.dispatch.send.executor.queue.size").gauge().value());
        } finally {
            release.countDown();
            executor.shutdown();
        }
    }

    private static boolean waitUntilQueueSize(ThreadPoolTaskExecutor executor, int expectedSize) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (System.nanoTime() < deadline) {
            if (executor.getThreadPoolExecutor().getQueue().size() == expectedSize) {
                return true;
            }
            TimeUnit.MILLISECONDS.sleep(10);
        }
        return executor.getThreadPoolExecutor().getQueue().size() == expectedSize;
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            latch.await(1, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
