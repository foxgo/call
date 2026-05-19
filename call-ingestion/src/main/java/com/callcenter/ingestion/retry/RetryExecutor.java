package com.callcenter.ingestion.retry;

import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class RetryExecutor {

    private static final List<Duration> BACKOFFS = List.of(
            Duration.ofMillis(200),
            Duration.ofSeconds(1),
            Duration.ofSeconds(5)
    );

    public void run(String operation, ThrowingRunnable runnable) {
        call(operation, () -> {
            runnable.run();
            return null;
        });
    }

    public <T> T call(String operation, ThrowingSupplier<T> supplier) {
        RuntimeException lastException = null;
        for (int attempt = 0; attempt < BACKOFFS.size() + 1; attempt++) {
            try {
                return supplier.get();
            } catch (Exception exception) {
                lastException = new RuntimeException(
                        "Operation failed after attempt %d: %s".formatted(attempt + 1, operation),
                        exception
                );
                if (attempt >= BACKOFFS.size()) {
                    break;
                }
                Duration backoff = BACKOFFS.get(attempt);
                log.warn("Retrying operation {} in {} ms", operation, backoff.toMillis(), exception);
                sleep(backoff);
            }
        }
        throw lastException;
    }

    private void sleep(Duration backoff) {
        try {
            Thread.sleep(backoff);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Retry sleep interrupted", exception);
        }
    }

    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Exception;
    }

    @FunctionalInterface
    public interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
