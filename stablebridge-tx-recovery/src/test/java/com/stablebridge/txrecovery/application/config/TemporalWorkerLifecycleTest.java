package com.stablebridge.txrecovery.application.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.temporal.worker.WorkerFactory;

@ExtendWith(MockitoExtension.class)
class TemporalWorkerLifecycleTest {

    @Mock
    private WorkerFactory workerFactory;

    @InjectMocks
    private TemporalWorkerLifecycle lifecycle;

    @Test
    void shouldStartWorkerFactory() {
        // when
        lifecycle.start();

        // then
        then(workerFactory).should().start();
        assertThat(lifecycle.isRunning()).isTrue();
    }

    @Test
    void shouldShutdownWorkerFactoryAndAwaitTermination() {
        // given
        lifecycle.start();

        // when
        lifecycle.stop();

        // then
        then(workerFactory).should().shutdown();
        then(workerFactory).should().awaitTermination(10L, TimeUnit.SECONDS);
        assertThat(lifecycle.isRunning()).isFalse();
    }

    @Test
    void shouldNotBeRunningInitially() {
        // when
        var running = lifecycle.isRunning();

        // then
        assertThat(running).isFalse();
    }
}
