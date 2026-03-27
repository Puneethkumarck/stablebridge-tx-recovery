package com.stablebridge.txrecovery.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NonceConcurrencyExceptionTest {

    @Test
    void shouldContainAddressAndChainInMessage() {
        // given
        var address = "0x742d35Cc6634C0532925a3b844Bc9e7595f2bD28";
        var chain = "ethereum_mainnet";

        // when
        var exception = new NonceConcurrencyException(address, chain);

        // then
        assertThat(exception.getMessage()).contains(address).contains(chain);
        assertThat(exception.getErrorCode()).isEqualTo("STR-5012");
    }

    @Test
    void shouldExtendStrException() {
        // when
        var exception = new NonceConcurrencyException("0xabc", "polygon");

        // then
        assertThat(exception).isInstanceOf(StrException.class);
    }
}
