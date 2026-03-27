package com.stablebridge.txrecovery.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NoAvailableAddressExceptionTest {

    @Test
    void shouldFormatMessageWithChainAndTier() {
        // given
        var chain = "ethereum";
        var tier = "HOT";

        // when
        var exception = new NoAvailableAddressException(chain, tier);

        // then
        assertThat(exception.getMessage()).isEqualTo("No available address for chain=ethereum tier=HOT");
        assertThat(exception.getErrorCode()).isEqualTo("STR-5020");
    }
}
