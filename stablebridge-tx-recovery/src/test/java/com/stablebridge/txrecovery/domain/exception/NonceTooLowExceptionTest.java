package com.stablebridge.txrecovery.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NonceTooLowExceptionTest {

    @Test
    void shouldFormatMessageWithExpectedAndActual() {
        // given
        var expected = 42L;
        var actual = 10L;

        // when
        var exception = new NonceTooLowException(expected, actual);

        // then
        assertThat(exception.getMessage())
                .isEqualTo("Nonce too low: expected at least 42 but got 10");
        assertThat(exception.getErrorCode()).isEqualTo("STR-5010");
    }

    @Test
    void shouldBeNonRetryableException() {
        // when
        var exception = new NonceTooLowException(5, 3);

        // then
        assertThat(exception).isInstanceOf(NonRetryableException.class);
        assertThat(exception).isInstanceOf(StrException.class);
    }
}
