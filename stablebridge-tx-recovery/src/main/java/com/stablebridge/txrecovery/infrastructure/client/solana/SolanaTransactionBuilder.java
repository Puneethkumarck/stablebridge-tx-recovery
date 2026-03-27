package com.stablebridge.txrecovery.infrastructure.client.solana;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bouncycastle.math.ec.rfc8032.Ed25519;

import com.stablebridge.txrecovery.domain.recovery.model.FeeEstimate;
import com.stablebridge.txrecovery.domain.recovery.model.FeeUrgency;
import com.stablebridge.txrecovery.domain.recovery.port.FeeOracle;
import com.stablebridge.txrecovery.domain.transaction.model.SolanaSubmissionResource;
import com.stablebridge.txrecovery.domain.transaction.model.TransactionIntent;
import com.stablebridge.txrecovery.domain.transaction.model.UnsignedTransaction;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
class SolanaTransactionBuilder {

    private static final int PUBKEY_LENGTH = 32;
    private static final int DEFAULT_COMPUTE_UNIT_LIMIT = 200_000;

    private static final byte[] SYSTEM_PROGRAM_ID = new byte[PUBKEY_LENGTH];
    private static final byte[] TOKEN_PROGRAM_ID = decodeBase58("TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA");
    private static final byte[] ATA_PROGRAM_ID = decodeBase58("ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL");
    private static final byte[] COMPUTE_BUDGET_PROGRAM_ID = decodeBase58("ComputeBudget111111111111111111111111111111");
    private static final byte[] RECENT_BLOCKHASHES_SYSVAR = decodeBase58("SysvarRecentB1ockHashes11111111111111111111");

    private static final int ADVANCE_NONCE_INSTRUCTION_INDEX = 4;
    private static final byte SPL_TRANSFER_INSTRUCTION = 3;
    private static final byte SET_COMPUTE_UNIT_LIMIT_INSTRUCTION = 0x02;
    private static final byte SET_COMPUTE_UNIT_PRICE_INSTRUCTION = 0x03;

    private static final String BASE58_ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";

    private final SolanaRpcClient rpcClient;
    private final FeeOracle feeOracle;
    private final int defaultComputeUnitLimit;

    SolanaTransactionBuilder(SolanaRpcClient rpcClient, FeeOracle feeOracle) {
        this(rpcClient, feeOracle, DEFAULT_COMPUTE_UNIT_LIMIT);
    }

    public UnsignedTransaction build(TransactionIntent intent, SolanaSubmissionResource resource) {
        Objects.requireNonNull(intent, "TransactionIntent must not be null");
        Objects.requireNonNull(resource, "SolanaSubmissionResource must not be null");

        var feeEstimate = feeOracle.estimate(intent.chain(), FeeUrgency.MEDIUM);
        validateFeeEstimate(feeEstimate);

        var computeUnitPrice = feeEstimate.computeUnitPrice().longValueExact();

        var fromKey = decodeBase58(resource.fromAddress());
        var mintKey = decodeBase58(intent.tokenContractAddress());
        var destKey = decodeBase58(intent.toAddress());
        var nonceAccountKey = decodeBase58(resource.nonceAccountAddress());
        var nonceBlockhash = decodeBase58(resource.nonceValue());

        var sourceAta = deriveAta(fromKey, mintKey);
        var destAta = deriveAta(destKey, mintKey);

        var instructions = List.of(
                buildAdvanceNonceInstruction(nonceAccountKey, fromKey),
                buildSetComputeUnitLimitInstruction(defaultComputeUnitLimit),
                buildSetComputeUnitPriceInstruction(computeUnitPrice),
                buildSplTransferInstruction(sourceAta, destAta, fromKey, intent.rawAmount()));

        var payload = serializeMessage(fromKey, nonceBlockhash, instructions);

        var metadata = Map.of(
                "nonceAccountAddress", resource.nonceAccountAddress(),
                "nonceValue", resource.nonceValue(),
                "computeUnitLimit", String.valueOf(defaultComputeUnitLimit),
                "computeUnitPrice", String.valueOf(computeUnitPrice));

        return UnsignedTransaction.builder()
                .intentId(intent.intentId())
                .chain(intent.chain())
                .fromAddress(resource.fromAddress())
                .toAddress(intent.toAddress())
                .payload(payload)
                .feeEstimate(feeEstimate)
                .metadata(metadata)
                .build();
    }

    private static void validateFeeEstimate(FeeEstimate feeEstimate) {
        Objects.requireNonNull(feeEstimate, "FeeEstimate must not be null");
        Objects.requireNonNull(feeEstimate.computeUnitPrice(), "FeeEstimate.computeUnitPrice must not be null");
        if (feeEstimate.computeUnitPrice().signum() < 0) {
            throw new SolanaRpcException(-1, "Compute unit price must be non-negative");
        }
        if (feeEstimate.computeUnitPrice().compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) > 0) {
            throw new SolanaRpcException(-1, "Compute unit price exceeds maximum long value");
        }
    }

    private static SolanaInstruction buildSetComputeUnitLimitInstruction(int units) {
        var data = ByteBuffer.allocate(5).order(ByteOrder.LITTLE_ENDIAN)
                .put(SET_COMPUTE_UNIT_LIMIT_INSTRUCTION)
                .putInt(units)
                .array();
        return new SolanaInstruction(
                COMPUTE_BUDGET_PROGRAM_ID,
                List.of(),
                data);
    }

    private static SolanaInstruction buildSetComputeUnitPriceInstruction(long microLamports) {
        var data = ByteBuffer.allocate(9).order(ByteOrder.LITTLE_ENDIAN)
                .put(SET_COMPUTE_UNIT_PRICE_INSTRUCTION)
                .putLong(microLamports)
                .array();
        return new SolanaInstruction(
                COMPUTE_BUDGET_PROGRAM_ID,
                List.of(),
                data);
    }

    private static SolanaInstruction buildAdvanceNonceInstruction(byte[] nonceAccount, byte[] nonceAuthority) {
        var data = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(ADVANCE_NONCE_INSTRUCTION_INDEX)
                .array();
        var accounts = List.of(
                new AccountMeta(nonceAccount, true, false),
                new AccountMeta(RECENT_BLOCKHASHES_SYSVAR, false, false),
                new AccountMeta(nonceAuthority, false, true));
        return new SolanaInstruction(SYSTEM_PROGRAM_ID, accounts, data);
    }

    private static SolanaInstruction buildSplTransferInstruction(
            byte[] sourceAta, byte[] destAta, byte[] authority, BigInteger amount) {
        if (amount.signum() < 0 || amount.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) {
            throw new SolanaRpcException(-1,
                    "SPL transfer amount must be in range 0..%d, got %s".formatted(Long.MAX_VALUE, amount));
        }
        var data = ByteBuffer.allocate(9).order(ByteOrder.LITTLE_ENDIAN)
                .put(SPL_TRANSFER_INSTRUCTION)
                .putLong(amount.longValueExact())
                .array();
        var accounts = List.of(
                new AccountMeta(sourceAta, true, false),
                new AccountMeta(destAta, true, false),
                new AccountMeta(authority, false, true));
        return new SolanaInstruction(TOKEN_PROGRAM_ID, accounts, data);
    }

    @SneakyThrows
    static byte[] deriveAta(byte[] wallet, byte[] mint) {
        return findProgramAddress(ATA_PROGRAM_ID, wallet, TOKEN_PROGRAM_ID, mint);
    }

    @SneakyThrows
    private static byte[] findProgramAddress(byte[] programId, byte[]... seeds) {
        var markerBytes = "ProgramDerivedAddress".getBytes();
        for (var bump = 255; bump >= 0; bump--) {
            var digest = MessageDigest.getInstance("SHA-256");
            for (var seed : seeds) {
                digest.update(seed);
            }
            digest.update((byte) bump);
            digest.update(programId);
            digest.update(markerBytes);
            var hash = digest.digest();
            if (!Ed25519.validatePublicKeyFull(hash, 0)) {
                return hash;
            }
        }
        throw new SolanaRpcException(-1, "Unable to derive PDA: no valid off-curve address found");
    }

    static byte[] decodeBase58(String input) {
        if (input == null || input.isEmpty()) {
            throw new SolanaRpcException(-1, "Base58 input must not be null or empty");
        }

        var bi = BigInteger.ZERO;
        for (var i = 0; i < input.length(); i++) {
            var charIndex = BASE58_ALPHABET.indexOf(input.charAt(i));
            if (charIndex < 0) {
                throw new SolanaRpcException(-1, "Invalid Base58 character: " + input.charAt(i));
            }
            bi = bi.multiply(BigInteger.valueOf(58)).add(BigInteger.valueOf(charIndex));
        }

        var raw = bi.toByteArray();
        var leadingZeros = 0;
        for (var i = 0; i < input.length() && input.charAt(i) == '1'; i++) {
            leadingZeros++;
        }

        var startIndex = (raw.length > 0 && raw[0] == 0) ? 1 : 0;
        var significantLength = raw.length - startIndex;
        var result = new byte[leadingZeros + significantLength];
        System.arraycopy(raw, startIndex, result, leadingZeros, significantLength);

        if (result.length != PUBKEY_LENGTH) {
            throw new SolanaRpcException(-1,
                    "Decoded Base58 key must be %d bytes, got %d".formatted(PUBKEY_LENGTH, result.length));
        }
        return result;
    }

    static byte[] encodeCompactU16(int value) {
        if (value < 0 || value > 0xFFFF) {
            throw new SolanaRpcException(-1, "Compact-u16 value must be in range 0..65535");
        }
        if (value < 0x80) {
            return new byte[]{(byte) value};
        }
        if (value < 0x4000) {
            return new byte[]{
                    (byte) ((value & 0x7F) | 0x80),
                    (byte) (value >> 7)};
        }
        return new byte[]{
                (byte) ((value & 0x7F) | 0x80),
                (byte) (((value >> 7) & 0x7F) | 0x80),
                (byte) (value >> 14)};
    }

    private static byte[] serializeMessage(
            byte[] feePayer, byte[] recentBlockhash, List<SolanaInstruction> instructions) {
        var accountIndex = new LinkedHashMap<ByteArrayKey, AccountProperties>();

        addAccount(accountIndex, feePayer, true, true);

        for (var ix : instructions) {
            for (var meta : ix.accounts()) {
                addAccount(accountIndex, meta.pubkey(), meta.writable(), meta.signer());
            }
            addAccount(accountIndex, ix.programId(), false, false);
        }

        var signerWritable = new ArrayList<byte[]>();
        var signerReadonly = new ArrayList<byte[]>();
        var nonSignerWritable = new ArrayList<byte[]>();
        var nonSignerReadonly = new ArrayList<byte[]>();

        accountIndex.forEach((key, props) -> {
            if (props.signer && props.writable) {
                signerWritable.add(key.bytes);
            } else if (props.signer) {
                signerReadonly.add(key.bytes);
            } else if (props.writable) {
                nonSignerWritable.add(key.bytes);
            } else {
                nonSignerReadonly.add(key.bytes);
            }
        });

        var orderedAccounts = new ArrayList<byte[]>();
        orderedAccounts.addAll(signerWritable);
        orderedAccounts.addAll(signerReadonly);
        orderedAccounts.addAll(nonSignerWritable);
        orderedAccounts.addAll(nonSignerReadonly);

        var numRequiredSignatures = signerWritable.size() + signerReadonly.size();
        var numReadonlySignedAccounts = signerReadonly.size();
        var numReadonlyUnsignedAccounts = nonSignerReadonly.size();

        var accountLookup = new LinkedHashMap<ByteArrayKey, Integer>();
        for (var i = 0; i < orderedAccounts.size(); i++) {
            accountLookup.put(new ByteArrayKey(orderedAccounts.get(i)), i);
        }

        var output = new ByteArrayOutputStream();
        output.write(numRequiredSignatures);
        output.write(numReadonlySignedAccounts);
        output.write(numReadonlyUnsignedAccounts);

        var accountCountBytes = encodeCompactU16(orderedAccounts.size());
        output.write(accountCountBytes, 0, accountCountBytes.length);
        for (var account : orderedAccounts) {
            output.write(account, 0, PUBKEY_LENGTH);
        }

        output.write(recentBlockhash, 0, PUBKEY_LENGTH);

        var instructionCountBytes = encodeCompactU16(instructions.size());
        output.write(instructionCountBytes, 0, instructionCountBytes.length);

        for (var ix : instructions) {
            var programIndex = accountLookup.get(new ByteArrayKey(ix.programId()));
            output.write(programIndex);

            var accountIdxCountBytes = encodeCompactU16(ix.accounts().size());
            output.write(accountIdxCountBytes, 0, accountIdxCountBytes.length);
            for (var meta : ix.accounts()) {
                var idx = accountLookup.get(new ByteArrayKey(meta.pubkey()));
                output.write(idx);
            }

            var dataLenBytes = encodeCompactU16(ix.data().length);
            output.write(dataLenBytes, 0, dataLenBytes.length);
            output.write(ix.data(), 0, ix.data().length);
        }

        return output.toByteArray();
    }

    private static void addAccount(
            LinkedHashMap<ByteArrayKey, AccountProperties> index, byte[] pubkey,
            boolean writable, boolean signer) {
        var key = new ByteArrayKey(pubkey);
        index.merge(key, new AccountProperties(writable, signer), AccountProperties::merge);
    }

    private record AccountMeta(byte[] pubkey, boolean writable, boolean signer) {}

    private record SolanaInstruction(byte[] programId, List<AccountMeta> accounts, byte[] data) {}

    private static final class AccountProperties {
        private final boolean writable;
        private final boolean signer;

        AccountProperties(boolean writable, boolean signer) {
            this.writable = writable;
            this.signer = signer;
        }

        AccountProperties merge(AccountProperties other) {
            return new AccountProperties(
                    this.writable || other.writable,
                    this.signer || other.signer);
        }
    }

    private record ByteArrayKey(byte[] bytes) {

        @Override
        public boolean equals(Object o) {
            return o instanceof ByteArrayKey other && Arrays.equals(bytes, other.bytes);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(bytes);
        }
    }
}
