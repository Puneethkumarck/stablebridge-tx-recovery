package com.stablebridge.txrecovery.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NonRetryableExceptionTest {

    @Test
    void shouldCreateWithMessage() {
        // given
        var message = "Something went wrong";

        // when
        var exception = new NonRetryableException(message);

        // then
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getErrorCode()).isEqualTo("STR-5010");
    }

    @Test
    void shouldCreateWithMessageAndCause() {
        // given
        var message = "Something went wrong";
        var cause = new RuntimeException("root cause");

        // when
        var exception = new NonRetryableException(message, cause);

        // then
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getErrorCode()).isEqualTo("STR-5010");
        assertThat(exception.getCause()).isEqualTo(cause);
    }

    @Test
    void shouldBeRuntimeException() {
        // when
        var exception = new NonRetryableException("test");

        // then
        assertThat(exception).isInstanceOf(RuntimeException.class);
        assertThat(exception).isInstanceOf(StrException.class);
    }
}
