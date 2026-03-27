package com.stablebridge.txrecovery.application.config;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import io.temporal.worker.WorkerFactory;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(WorkerFactory.class)
public class TemporalWorkerLifecycle implements SmartLifecycle {

    private static final long SHUTDOWN_TIMEOUT_SECONDS = 10;

    private final WorkerFactory workerFactory;

    private final AtomicBoolean running = new AtomicBoolean(false);

    @Override
    public void start() {
        try {
            log.info("Starting Temporal WorkerFactory");
            workerFactory.start();
            running.set(true);
        } catch (Exception e) {
            log.error("Failed to start Temporal WorkerFactory", e);
        }
    }

    @Override
    @SneakyThrows
    public void stop() {
        log.info("Shutting down Temporal WorkerFactory");
        workerFactory.shutdown();
        workerFactory.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        running.set(false);
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }
}
