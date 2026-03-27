package com.stablebridge.txrecovery.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class NonceManagerPropertiesTest {

    @Test
    void shouldUseDefaultGapDetectionIntervalWhenNull() {
        // when
        var properties = new NonceManagerProperties(null);

        // then
        assertThat(properties.gapDetectionInterval()).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void shouldUseProvidedGapDetectionInterval() {
        // given
        var customInterval = Duration.ofMinutes(1);

        // when
        var properties = new NonceManagerProperties(customInterval);

        // then
        assertThat(properties.gapDetectionInterval()).isEqualTo(customInterval);
    }
}
