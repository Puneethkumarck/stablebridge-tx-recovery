package com.stablebridge.txrecovery.infrastructure.client.evm;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.StringRedisTemplate;

import com.stablebridge.txrecovery.domain.recovery.model.FeeEstimate;
import com.stablebridge.txrecovery.domain.recovery.model.FeeUrgency;
import com.stablebridge.txrecovery.domain.recovery.port.FeeOracle;
import com.stablebridge.txrecovery.infrastructure.redis.RedisKeyNamespace;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

@Slf4j
class EvmFeeOracle implements FeeOracle {

    private static final BigDecimal BASE_FEE_MULTIPLIER = new BigDecimal("1.125");
    private static final BigDecimal REPLACEMENT_BUMP = new BigDecimal("1.1");
    private static final int FEE_HISTORY_BLOCK_COUNT = 10;
    private static final List<Float> REWARD_PERCENTILES = List.of(25.0f, 50.0f, 75.0f, 95.0f);
    private static final int SCALE = 0;
    private static final MathContext MATH_CONTEXT = MathContext.DECIMAL128;
    private static final String DENOMINATION = "wei";

    private final EvmRpcClient rpcClient;
    private final EvmChainProperties chainProperties;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    EvmFeeOracle(
            EvmRpcClient rpcClient,
            EvmChainProperties chainProperties,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper) {
        this.rpcClient = rpcClient;
        this.chainProperties = chainProperties;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public FeeEstimate estimate(String chain, FeeUrgency urgency) {
        return readFromCache(chain, urgency)
                .orElseGet(() -> fetchAndCache(chain, urgency));
    }

    @Override
    public FeeEstimate estimateReplacement(String chain, String originalTxHash, int attemptNumber) {
        var originalTx = rpcClient.getTransactionByHash(originalTxHash)
                .orElseThrow(() -> new EvmRpcException(
                        "Original transaction not found: " + originalTxHash, false));

        var originalMaxFee = decodeHexToBigDecimal(originalTx.maxFeePerGas(), originalTx.gasPrice());
        var bumpedOriginal = originalMaxFee.multiply(REPLACEMENT_BUMP, MATH_CONTEXT)
                .setScale(SCALE, RoundingMode.CEILING);

        var fastEstimate = estimate(chain, FeeUrgency.FAST);
        var currentFastFee = fastEstimate.maxFeePerGas();

        var escalationMultiplier = escalationMultiplierForAttempt(attemptNumber);
        var replacementFee = bumpedOriginal.max(currentFastFee)
                .multiply(escalationMultiplier, MATH_CONTEXT)
                .setScale(SCALE, RoundingMode.CEILING);

        var cappedFee = capToSafetyLimit(replacementFee);

        var originalPriorityFee = decodeHexToBigDecimal(originalTx.maxPriorityFeePerGas(), null);
        var bumpedPriorityFee = originalPriorityFee
                .multiply(REPLACEMENT_BUMP, MATH_CONTEXT)
                .multiply(escalationMultiplier, MATH_CONTEXT)
                .setScale(SCALE, RoundingMode.CEILING);
        var cappedPriorityFee = bumpedPriorityFee.min(cappedFee);
        var fastPriorityFee = fastEstimate.maxPriorityFeePerGas();
        var finalPriorityFee = cappedPriorityFee.max(fastPriorityFee).min(cappedFee);

        return FeeEstimate.builder()
                .maxFeePerGas(cappedFee)
                .maxPriorityFeePerGas(finalPriorityFee)
                .estimatedCost(cappedFee)
                .denomination(DENOMINATION)
                .urgency(FeeUrgency.URGENT)
                .details(Map.of(
                        "originalMaxFee", originalMaxFee.toPlainString(),
                        "bumpedOriginal", bumpedOriginal.toPlainString(),
                        "currentFastFee", currentFastFee.toPlainString(),
                        "escalationMultiplier", escalationMultiplier.toPlainString(),
                        "maxFeePerGas", cappedFee.toPlainString(),
                        "priorityFee", finalPriorityFee.toPlainString(),
                        "safetyCapWei", chainProperties.maxFeeCapWei().toPlainString(),
                        "attemptNumber", String.valueOf(attemptNumber)))
                .build();
    }

    BigDecimal calculateMaxFeePerGas(BigDecimal baseFee, int exponent) {
        var multiplier = BASE_FEE_MULTIPLIER.pow(exponent, MATH_CONTEXT);
        return baseFee.multiply(multiplier, MATH_CONTEXT).setScale(SCALE, RoundingMode.CEILING);
    }

    BigDecimal capToSafetyLimit(BigDecimal fee) {
        return fee.min(chainProperties.maxFeeCapWei());
    }

    static int urgencyExponent(FeeUrgency urgency) {
        return switch (urgency) {
            case SLOW -> 1;
            case MEDIUM -> 2;
            case FAST -> 3;
            case URGENT -> 5;
        };
    }

    static int percentileIndex(FeeUrgency urgency) {
        return switch (urgency) {
            case SLOW -> 0;
            case MEDIUM -> 1;
            case FAST -> 2;
            case URGENT -> 3;
        };
    }

    BigDecimal latestBaseFee(EvmFeeHistory feeHistory) {
        var baseFees = feeHistory.baseFeePerGas();
        if (baseFees.isEmpty()) {
            throw new EvmRpcException("Fee history returned empty baseFeePerGas");
        }
        return new BigDecimal(decodeQuantity(baseFees.getLast()));
    }

    BigDecimal medianRewardForPercentile(EvmFeeHistory feeHistory, int percentileIndex) {
        var rewards = feeHistory.reward();
        if (rewards.isEmpty()) {
            return BigDecimal.ZERO;
        }

        var values = rewards.stream()
                .filter(r -> r.size() > percentileIndex)
                .map(r -> new BigDecimal(decodeQuantity(r.get(percentileIndex))))
                .sorted()
                .toList();

        if (values.isEmpty()) {
            return BigDecimal.ZERO;
        }

        var mid = values.size() / 2;
        return values.size() % 2 == 0
                ? values.get(mid - 1).add(values.get(mid)).divide(BigDecimal.TWO, SCALE, RoundingMode.CEILING)
                : values.get(mid);
    }

    String getChain() {
        return chainProperties.chain();
    }

    private FeeEstimate fetchAndCache(String chain, FeeUrgency urgency) {
        var feeHistory = rpcClient.feeHistory(FEE_HISTORY_BLOCK_COUNT, "latest", REWARD_PERCENTILES);
        var baseFee = latestBaseFee(feeHistory);
        var priorityFee = medianRewardForPercentile(feeHistory, percentileIndex(urgency));
        var multiplierExponent = urgencyExponent(urgency);
        var maxFee = calculateMaxFeePerGas(baseFee, multiplierExponent);
        var cappedMaxFee = capToSafetyLimit(maxFee);
        var cappedPriorityFee = priorityFee.min(cappedMaxFee);

        var details = Map.of(
                "baseFee", baseFee.toPlainString(),
                "maxFeePerGas", cappedMaxFee.toPlainString(),
                "priorityFee", cappedPriorityFee.toPlainString(),
                "multiplier", BASE_FEE_MULTIPLIER.pow(multiplierExponent, MATH_CONTEXT).toPlainString(),
                "safetyCapWei", chainProperties.maxFeeCapWei().toPlainString());

        var estimate = FeeEstimate.builder()
                .maxFeePerGas(cappedMaxFee)
                .maxPriorityFeePerGas(cappedPriorityFee)
                .estimatedCost(cappedMaxFee)
                .denomination(DENOMINATION)
                .urgency(urgency)
                .details(details)
                .build();

        writeToCache(chain, urgency, estimate);
        return estimate;
    }

    private BigDecimal escalationMultiplierForAttempt(int attemptNumber) {
        return BigDecimal.ONE.add(
                new BigDecimal("0.1").multiply(BigDecimal.valueOf(attemptNumber), MATH_CONTEXT));
    }

    private BigDecimal decodeHexToBigDecimal(String primaryHex, String fallbackHex) {
        var hex = primaryHex != null ? primaryHex : fallbackHex;
        if (hex == null) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(decodeQuantity(hex));
    }

    private static BigInteger decodeQuantity(String hex) {
        if (hex == null) {
            throw new EvmRpcException("Cannot decode null hex quantity");
        }
        var stripped = hex.startsWith("0x") ? hex.substring(2) : hex;
        return new BigInteger(stripped, 16);
    }

    private Optional<FeeEstimate> readFromCache(String chain, FeeUrgency urgency) {
        try {
            var key = RedisKeyNamespace.gasCacheHash(chain);
            var raw = redisTemplate.opsForHash().get(key, urgency.name());
            return Optional.ofNullable(raw)
                    .map(Object::toString)
                    .filter(json -> !"null".equals(json))
                    .map(json -> objectMapper.readValue(json, FeeEstimate.class));
        } catch (Exception e) {
            log.warn("Failed to read fee estimate from cache for chain={} urgency={}", chain, urgency, e);
            return Optional.empty();
        }
    }

    private void writeToCache(String chain, FeeUrgency urgency, FeeEstimate estimate) {
        try {
            var key = RedisKeyNamespace.gasCacheHash(chain);
            var json = objectMapper.writeValueAsString(estimate);
            redisTemplate.opsForHash().put(key, urgency.name(), json);
            redisTemplate.expire(key, chainProperties.blockTime().toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.warn("Failed to write fee estimate to cache for chain={} urgency={}", chain, urgency, e);
        }
    }
}
