package com.stablebridge.txrecovery.infrastructure.signer.keygen;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.HexFormat;

import org.bouncycastle.crypto.ec.CustomNamedCurves;
import org.bouncycastle.jcajce.provider.digest.Keccak;
import org.bouncycastle.math.ec.rfc8032.Ed25519;

public final class KeyGenerator {

    private static final String BASE58_ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
    private static final int KEY_SIZE = 32;
    private static final int ADDRESS_HEX_LENGTH = 40;

    private KeyGenerator() {}

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("Usage: KeyGenerator <chain-type> <output-file>");
            System.err.println("  chain-type: solana | evm");
            System.err.println("  output-file: path to write the keystore JSON");
            System.exit(1);
        }

        var chainType = args[0].toLowerCase();
        var outputPath = Path.of(args[1]);

        var random = new SecureRandom();
        var privateKey = new byte[KEY_SIZE];
        random.nextBytes(privateKey);

        var hex = HexFormat.of();
        String address;
        String json;
        String finalKeyHex;

        switch (chainType) {
            case "solana" -> {
                var publicKey = new byte[Ed25519.PUBLIC_KEY_SIZE];
                Ed25519.generatePublicKey(privateKey, 0, publicKey, 0);
                address = encodeBase58(publicKey);
                finalKeyHex = hex.formatHex(privateKey);
                json = formatKeystoreJson(address, finalKeyHex);
                System.out.println("Chain:       Solana");
                System.out.println("Address:     " + address);
                System.out.println("Private key: " + finalKeyHex);
                System.out.println("Keystore:    " + outputPath.toAbsolutePath());
            }
            case "evm" -> {
                var ecParams = CustomNamedCurves.getByName("secp256k1");
                var d = new BigInteger(1, privateKey).mod(
                        ecParams.getN().subtract(BigInteger.ONE)).add(BigInteger.ONE);
                var pubPoint = ecParams.getG().multiply(d).normalize();
                var pubBytes = pubPoint.getEncoded(false);
                var pubUncompressed = new byte[pubBytes.length - 1];
                System.arraycopy(pubBytes, 1, pubUncompressed, 0, pubUncompressed.length);
                var addressHash = new Keccak.Digest256().digest(pubUncompressed);
                var addressBytes = new byte[20];
                System.arraycopy(addressHash, 12, addressBytes, 0, 20);
                address = "0x" + hex.formatHex(addressBytes);

                var adjustedKey = d.toByteArray();
                var keyBytes = new byte[KEY_SIZE];
                if (adjustedKey.length > KEY_SIZE) {
                    System.arraycopy(adjustedKey, adjustedKey.length - KEY_SIZE, keyBytes, 0, KEY_SIZE);
                } else {
                    System.arraycopy(adjustedKey, 0, keyBytes, KEY_SIZE - adjustedKey.length, adjustedKey.length);
                }

                finalKeyHex = hex.formatHex(keyBytes);
                json = formatKeystoreJson(address, finalKeyHex);
                System.out.println("Chain:       EVM");
                System.out.println("Address:     " + address);
                System.out.println("Private key: " + finalKeyHex);
                System.out.println("Keystore:    " + outputPath.toAbsolutePath());
            }
            default -> {
                System.err.println("Unknown chain type: " + chainType + ". Use 'solana' or 'evm'.");
                System.exit(1);
                return;
            }
        }

        if (Files.exists(outputPath)) {
            var existing = Files.readString(outputPath);
            json = mergeKeystoreJson(existing, address, finalKeyHex);
        }

        var parent = outputPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(outputPath, json);
        System.out.println("\nFund this address before running testnet tests.");
    }

    private static String formatKeystoreJson(String address, String privateKeyHex) {
        return """
                {
                  "keys": [
                    {"address": "%s", "privateKeyHex": "%s"}
                  ]
                }
                """.formatted(address, privateKeyHex);
    }

    private static String mergeKeystoreJson(String existing, String address, String privateKeyHex) {
        var insertPoint = existing.lastIndexOf(']');
        if (insertPoint < 0) {
            return formatKeystoreJson(address, privateKeyHex);
        }
        var needsComma = existing.substring(0, insertPoint).trim().endsWith("}");
        var entry = "%s\n    {\"address\": \"%s\", \"privateKeyHex\": \"%s\"}".formatted(
                needsComma ? "," : "", address, privateKeyHex);
        return existing.substring(0, insertPoint) + entry + "\n" + existing.substring(insertPoint);
    }

    static String encodeBase58(byte[] input) {
        var bi = BigInteger.ZERO;
        for (var b : input) {
            bi = bi.multiply(BigInteger.valueOf(256)).add(BigInteger.valueOf(b & 0xFF));
        }

        var sb = new StringBuilder();
        while (bi.compareTo(BigInteger.ZERO) > 0) {
            var divmod = bi.divideAndRemainder(BigInteger.valueOf(58));
            sb.insert(0, BASE58_ALPHABET.charAt(divmod[1].intValue()));
            bi = divmod[0];
        }

        for (var b : input) {
            if (b == 0) {
                sb.insert(0, '1');
            } else {
                break;
            }
        }

        return sb.toString();
    }
}
