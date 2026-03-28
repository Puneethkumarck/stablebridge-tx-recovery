package com.stablebridge.txrecovery.infrastructure.signer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

class SignerAutoConfigurationTest {

    private final SignerAutoConfiguration configuration = new SignerAutoConfiguration();

    @Nested
    class LocalKeystoreBackend {

        @Test
        void shouldCreateLocalKeystoreSignerWhenProperlyConfigured() {
            // given
            var properties = LocalSignerProperties.builder()
                    .keystorePath("/path/to/keystore.p12")
                    .password("secret")
                    .build();

            // when
            var signer = configuration.localKeystoreSigner(properties);

            // then
            assertThat(signer).isInstanceOf(LocalKeystoreSigner.class);
        }

        @Test
        void shouldThrowWhenKeystorePathMissing() {
            // given
            var properties = LocalSignerProperties.builder()
                    .password("secret")
                    .build();

            // when/then
            assertThatThrownBy(() -> configuration.localKeystoreSigner(properties))
                    .isInstanceOf(SignerConfigurationException.class)
                    .hasMessageContaining("str.signer.keystore-path");
        }

        @Test
        void shouldThrowWhenKeystorePathBlank() {
            // given
            var properties = LocalSignerProperties.builder()
                    .keystorePath("  ")
                    .password("secret")
                    .build();

            // when/then
            assertThatThrownBy(() -> configuration.localKeystoreSigner(properties))
                    .isInstanceOf(SignerConfigurationException.class)
                    .hasMessageContaining("str.signer.keystore-path");
        }

        @Test
        void shouldThrowWhenPasswordMissing() {
            // given
            var properties = LocalSignerProperties.builder()
                    .keystorePath("/path/to/keystore.p12")
                    .build();

            // when/then
            assertThatThrownBy(() -> configuration.localKeystoreSigner(properties))
                    .isInstanceOf(SignerConfigurationException.class)
                    .hasMessageContaining("str.signer.password");
        }

        @Test
        void shouldThrowWhenPasswordBlank() {
            // given
            var properties = LocalSignerProperties.builder()
                    .keystorePath("/path/to/keystore.p12")
                    .password("")
                    .build();

            // when/then
            assertThatThrownBy(() -> configuration.localKeystoreSigner(properties))
                    .isInstanceOf(SignerConfigurationException.class)
                    .hasMessageContaining("str.signer.password");
        }
    }

    @Nested
    class CallbackBackend {

        @Test
        void shouldCreateCallbackSignerAdapterWhenProperlyConfigured() {
            // given
            var properties = CallbackSignerProperties.builder()
                    .hmacSecret("test-secret")
                    .timeout(Duration.ofSeconds(5))
                    .tls(CallbackSignerProperties.TlsProperties.builder().verify(false).build())
                    .build();
            var objectMapper = JsonMapper.builder().build();

            // when
            var signer = configuration.callbackSignerAdapter(properties, objectMapper);

            // then
            assertThat(signer).isInstanceOf(CallbackSignerAdapter.class);
            ((CallbackSignerAdapter) signer).close();
        }

        @Test
        void shouldThrowWhenHmacSecretMissing() {
            // given
            var properties = CallbackSignerProperties.builder()
                    .timeout(Duration.ofSeconds(5))
                    .build();
            var objectMapper = JsonMapper.builder().build();

            // when/then
            assertThatThrownBy(() -> configuration.callbackSignerAdapter(properties, objectMapper))
                    .isInstanceOf(SignerConfigurationException.class)
                    .hasMessageContaining("str.signer.callback.hmac-secret");
        }

        @Test
        void shouldThrowWhenHmacSecretBlank() {
            // given
            var properties = CallbackSignerProperties.builder()
                    .hmacSecret("  ")
                    .timeout(Duration.ofSeconds(5))
                    .build();
            var objectMapper = JsonMapper.builder().build();

            // when/then
            assertThatThrownBy(() -> configuration.callbackSignerAdapter(properties, objectMapper))
                    .isInstanceOf(SignerConfigurationException.class)
                    .hasMessageContaining("str.signer.callback.hmac-secret");
        }
    }
}
