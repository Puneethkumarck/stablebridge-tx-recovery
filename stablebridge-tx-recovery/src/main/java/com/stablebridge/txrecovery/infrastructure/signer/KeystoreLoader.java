package com.stablebridge.txrecovery.infrastructure.signer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

import org.springframework.core.io.DefaultResourceLoader;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
final class KeystoreLoader {

    private KeystoreLoader() {}

    static Map<String, byte[]> load(String keystorePath) {
        try {
            var content = readContent(keystorePath);
            var mapper = JsonMapper.builder().build();
            var root = mapper.readTree(content);
            var keysNode = root.get("keys");
            if (keysNode == null || !keysNode.isArray()) {
                throw new SignerConfigurationException("Keystore file must contain a 'keys' array");
            }

            var hex = HexFormat.of();
            var entries = new HashMap<String, byte[]>();

            for (var entry : keysNode) {
                var address = entry.get("address").asText();
                var privateKeyHex = entry.get("privateKeyHex").asText();
                if (entries.containsKey(address)) {
                    throw new SignerConfigurationException("Duplicate address in keystore: " + address);
                }
                entries.put(address, hex.parseHex(privateKeyHex));
            }

            log.info("Loaded {} signing key(s) from keystore", entries.size());
            return Map.copyOf(entries);
        } catch (SignerConfigurationException e) {
            throw e;
        } catch (IOException e) {
            throw new SignerConfigurationException("Failed to read keystore file: " + e.getMessage());
        } catch (Exception e) {
            throw new SignerConfigurationException("Failed to parse keystore file: " + e.getMessage());
        }
    }

    private static String readContent(String keystorePath) throws IOException {
        var filePath = Path.of(keystorePath);
        if (Files.exists(filePath)) {
            return Files.readString(filePath);
        }

        var resource = new DefaultResourceLoader().getResource(keystorePath);
        if (!resource.exists()) {
            throw new SignerConfigurationException("Keystore file not found: " + keystorePath);
        }
        try (InputStream is = resource.getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
