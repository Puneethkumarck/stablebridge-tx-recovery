package com.stablebridge.txrecovery.infrastructure.client.evm;

import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bouncycastle.jcajce.provider.digest.Keccak;

import com.stablebridge.txrecovery.domain.transaction.model.BroadcastResult;
import com.stablebridge.txrecovery.domain.transaction.model.EvmSubmissionResource;
import com.stablebridge.txrecovery.domain.transaction.model.SignedTransaction;
import com.stablebridge.txrecovery.domain.transaction.model.SubmissionResource;
import com.stablebridge.txrecovery.domain.transaction.model.TransactionIntent;
import com.stablebridge.txrecovery.domain.transaction.model.TransactionStatus;
import com.stablebridge.txrecovery.domain.transaction.model.UnsignedTransaction;
import com.stablebridge.txrecovery.domain.transaction.port.ChainTransactionManager;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
class EvmChainTransactionManager implements ChainTransactionManager {

    private static final String ALREADY_KNOWN = "already known";
    private static final String NONCE_TOO_LOW = "nonce too low";

    private final EvmRpcClient rpcClient;
    private final EvmTransactionBuilder transactionBuilder;
    private final long finalityBlocks;
    private final long stuckThresholdBlocks;
    private final ConcurrentHashMap<String, Long> pendingFirstSeen = new ConcurrentHashMap<>();

    @Override
    public UnsignedTransaction build(TransactionIntent intent, SubmissionResource resource) {
        if (!(resource instanceof EvmSubmissionResource evmResource)) {
            throw new EvmRpcException(
                    "Expected EvmSubmissionResource but got " + resource.getClass().getSimpleName(), false);
        }
        return transactionBuilder.build(intent, evmResource);
    }

    @Override
    public BroadcastResult broadcast(SignedTransaction signedTransaction, String chain) {
        validateChain(chain);
        var hex = "0x" + HexFormat.of().formatHex(signedTransaction.signedPayload());
        try {
            var txHash = rpcClient.sendRawTransaction(hex);
            return BroadcastResult.builder()
                    .txHash(txHash)
                    .chain(chain)
                    .broadcastedAt(Instant.now())
                    .build();
        } catch (EvmRpcException e) {
            return handleBroadcastError(e, signedTransaction, chain);
        }
    }

    @Override
    public TransactionStatus checkStatus(String txHash, String chain) {
        validateChain(chain);
        var receipt = rpcClient.getTransactionReceipt(txHash);

        if (receipt.isPresent()) {
            pendingFirstSeen.remove(txHash);
            var rcpt = receipt.get();
            if ("0x0".equals(rcpt.status())) {
                return TransactionStatus.FAILED;
            }
            return classifyConfirmedTransaction(rcpt);
        }

        return classifyPendingTransaction(txHash);
    }

    private BroadcastResult handleBroadcastError(
            EvmRpcException e, SignedTransaction signedTransaction, String chain) {
        var message = e.getMessage();
        if (message != null && message.toLowerCase().contains(ALREADY_KNOWN)) {
            log.info("Transaction already known by node for chain={}, treating as success", chain);
            var txHash = computeTxHash(signedTransaction.signedPayload());
            return BroadcastResult.builder()
                    .txHash(txHash)
                    .chain(chain)
                    .broadcastedAt(Instant.now())
                    .details(Map.of("note", "Transaction already known by node"))
                    .build();
        }
        if (message != null && message.toLowerCase().contains(NONCE_TOO_LOW)) {
            throw new EvmRpcException("Nonce too low — transaction cannot be submitted: " + message, false);
        }
        throw e;
    }

    private TransactionStatus classifyConfirmedTransaction(EvmReceipt receipt) {
        var currentBlock = rpcClient.getBlockByNumber("latest", false);
        if (currentBlock == null) {
            log.warn("Unable to fetch latest block, treating confirmed transaction as CONFIRMED");
            return TransactionStatus.CONFIRMED;
        }
        var receiptBlockNum = decodeBlockNumber(receipt.blockNumber());
        var currentBlockNum = decodeBlockNumber(currentBlock.number());
        var depth = currentBlockNum - receiptBlockNum;

        return depth >= finalityBlocks ? TransactionStatus.FINALIZED : TransactionStatus.CONFIRMED;
    }

    private TransactionStatus classifyPendingTransaction(String txHash) {
        var mempoolTx = rpcClient.getTransactionByHash(txHash);
        if (mempoolTx.isEmpty()) {
            pendingFirstSeen.remove(txHash);
            return TransactionStatus.DROPPED;
        }

        var currentBlock = rpcClient.getBlockByNumber("latest", false);
        if (currentBlock == null) {
            log.warn("Unable to fetch latest block for tx={}, treating as PENDING", txHash);
            return TransactionStatus.PENDING;
        }
        var currentBlockNum = decodeBlockNumber(currentBlock.number());
        var firstSeen = pendingFirstSeen.computeIfAbsent(txHash, _ -> currentBlockNum);
        var blocksSinceSeen = currentBlockNum - firstSeen;

        if (blocksSinceSeen > stuckThresholdBlocks) {
            return TransactionStatus.STUCK;
        }

        return TransactionStatus.PENDING;
    }

    private void validateChain(String chain) {
        if (!rpcClient.getChain().equals(chain)) {
            throw new EvmRpcException(
                    "Manager for chain %s cannot serve chain %s".formatted(rpcClient.getChain(), chain), false);
        }
    }

    private static long decodeBlockNumber(String hex) {
        var stripped = hex.startsWith("0x") ? hex.substring(2) : hex;
        return Long.parseLong(stripped, 16);
    }

    private static String computeTxHash(byte[] signedPayload) {
        var digest = new Keccak.Digest256();
        var hash = digest.digest(signedPayload);
        return "0x" + HexFormat.of().formatHex(hash);
    }
}
