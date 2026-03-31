package com.stablebridge.txrecovery.infrastructure.signer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.util.HexFormat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class KeystoreLoaderTest {

    @TempDir
    java.nio.file.Path tempDir;

    @Nested
    class ValidKeystore {

        @Test
        void shouldLoadSingleKey() throws IOException {
            // given
            var privateKeyHex = "a1b2c3d4e5f6".repeat(5) + "a1b2c3d4";
            var keystoreFile = tempDir.resolve("keys.json");
            Files.writeString(keystoreFile, """
                    {
                      "keys": [
                        {"address": "SolanaTestAddr123", "privateKeyHex": "%s"}
                      ]
                    }
                    """.formatted(privateKeyHex));

            // when
            var result = KeystoreLoader.load(keystoreFile.toString());

            // then
            assertThat(result).hasSize(1);
            assertThat(result).containsKey("SolanaTestAddr123");
            assertThat(result.get("SolanaTestAddr123"))
                    .isEqualTo(HexFormat.of().parseHex(privateKeyHex));
        }

        @Test
        void shouldLoadMultipleKeys() throws IOException {
            // given
            var key1Hex = "aa".repeat(32);
            var key2Hex = "bb".repeat(32);
            var keystoreFile = tempDir.resolve("keys.json");
            Files.writeString(keystoreFile, """
                    {
                      "keys": [
                        {"address": "0xEvmAddr", "privateKeyHex": "%s"},
                        {"address": "SolAddr", "privateKeyHex": "%s"}
                      ]
                    }
                    """.formatted(key1Hex, key2Hex));

            // when
            var result = KeystoreLoader.load(keystoreFile.toString());

            // then
            assertThat(result).hasSize(2);
            assertThat(result).containsKeys("0xEvmAddr", "SolAddr");
        }
    }

    @Nested
    class InvalidKeystore {

        @Test
        void shouldThrowWhenFileNotFound() {
            // when/then
            assertThatThrownBy(() -> KeystoreLoader.load("/nonexistent/path.json"))
                    .isInstanceOf(SignerConfigurationException.class)
                    .hasMessageContaining("not found");
        }

        @Test
        void shouldThrowWhenMissingKeysArray() throws IOException {
            // given
            var keystoreFile = tempDir.resolve("keys.json");
            Files.writeString(keystoreFile, "{}");

            // when/then
            assertThatThrownBy(() -> KeystoreLoader.load(keystoreFile.toString()))
                    .isInstanceOf(SignerConfigurationException.class)
                    .hasMessageContaining("keys");
        }

        @Test
        void shouldThrowWhenInvalidJson() throws IOException {
            // given
            var keystoreFile = tempDir.resolve("keys.json");
            Files.writeString(keystoreFile, "not json");

            // when/then
            assertThatThrownBy(() -> KeystoreLoader.load(keystoreFile.toString()))
                    .isInstanceOf(SignerConfigurationException.class)
                    .hasMessageContaining("parse");
        }
    }
}
