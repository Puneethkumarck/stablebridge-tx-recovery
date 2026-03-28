package com.stablebridge.txrecovery.infrastructure.client.solana;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

import com.stablebridge.txrecovery.domain.recovery.model.FeeEstimate;
import com.stablebridge.txrecovery.domain.recovery.model.FeeUrgency;
import com.stablebridge.txrecovery.domain.recovery.port.FeeOracle;
import com.stablebridge.txrecovery.infrastructure.redis.RedisFeeCache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
class SolanaFeeOracle implements FeeOracle {

    private static final long DEFAULT_COMPUTE_UNITS = 200_000L;
    private static final BigDecimal MICRO_LAMPORTS_PER_LAMPORT = BigDecimal.valueOf(1_000_000L);
    private static final BigDecimal LAMPORTS_PER_SOL = BigDecimal.valueOf(1_000_000_000L);
    private static final MathContext MATH_CONTEXT = MathContext.DECIMAL128;
    private static final int SCALE = 18;
    private static final String DENOMINATION = "SOL";

    private final SolanaRpcClient rpcClient;
    private final SolanaChainProperties chainProperties;
    private final RedisFeeCache feeCache;

    @Override
    public FeeEstimate estimate(String chain, FeeUrgency urgency) {
        validateChain(chain);
        return feeCache.read(chain, urgency)
                .orElseGet(() -> fetchAndCache(chain, urgency));
    }

    @Override
    public FeeEstimate estimateReplacement(String chain, String originalTxHash, int attemptNumber) {
        validateChain(chain);
        if (attemptNumber < 1) {
            throw new IllegalArgumentException("attemptNumber must be >= 1, got: " + attemptNumber);
        }

        log.debug("Solana uses durable nonce resubmission — originalTxHash={} not used for fee calculation",
                originalTxHash);

        var fastEstimate = estimate(chain, FeeUrgency.FAST);
        var basePrice = fastEstimate.computeUnitPrice();

        var escalationMultiplier = escalationMultiplierForAttempt(attemptNumber);
        var escalatedPrice = basePrice.multiply(escalationMultiplier, MATH_CONTEXT)
                .setScale(0, RoundingMode.CEILING);

        var cappedPrice = capToSafetyLimit(escalatedPrice);
        var estimatedCost = computeEstimatedCost(cappedPrice);

        return FeeEstimate.builder()
                .computeUnitPrice(cappedPrice)
                .estimatedCost(estimatedCost)
                .denomination(DENOMINATION)
                .urgency(FeeUrgency.URGENT)
                .details(Map.of(
                        "baseComputeUnitPrice", basePrice.toPlainString(),
                        "escalationMultiplier", escalationMultiplier.toPlainString(),
                        "computeUnitPrice", cappedPrice.toPlainString(),
                        "safetyCapMicroLamports", String.valueOf(chainProperties.maxPriorityFeeMicroLamports()),
                        "attemptNumber", String.valueOf(attemptNumber)))
                .build();
    }

    long computePercentile(List<Long> sortedFees, double percentile) {
        if (sortedFees.isEmpty()) {
            return 0L;
        }
        var index = (int) Math.ceil(percentile / 100.0 * sortedFees.size()) - 1;
        return sortedFees.get(Math.max(0, index));
    }

    BigDecimal capToSafetyLimit(BigDecimal fee) {
        return fee.min(BigDecimal.valueOf(chainProperties.maxPriorityFeeMicroLamports()));
    }

    String getChain() {
        return chainProperties.chain();
    }

    private FeeEstimate fetchAndCache(String chain, FeeUrgency urgency) {
        var recentFees = rpcClient.getRecentPrioritizationFees(chainProperties.programAddresses());

        var sortedFees = recentFees.stream()
                .map(SolanaPrioritizationFee::prioritizationFee)
                .sorted()
                .toList();

        var computeUnitPrice = BigDecimal.valueOf(feeForUrgency(sortedFees, urgency));
        var cappedPrice = capToSafetyLimit(computeUnitPrice);
        var estimatedCost = computeEstimatedCost(cappedPrice);

        var details = Map.of(
                "computeUnitPrice", cappedPrice.toPlainString(),
                "sampleSize", String.valueOf(sortedFees.size()),
                "safetyCapMicroLamports", String.valueOf(chainProperties.maxPriorityFeeMicroLamports()));

        var estimate = FeeEstimate.builder()
                .computeUnitPrice(cappedPrice)
                .estimatedCost(estimatedCost)
                .denomination(DENOMINATION)
                .urgency(urgency)
                .details(details)
                .build();

        feeCache.write(chain, urgency, estimate, chainProperties.blockTime().toMillis());
        return estimate;
    }

    private static final BigDecimal URGENT_MULTIPLIER = new BigDecimal("1.5");

    private long feeForUrgency(List<Long> sortedFees, FeeUrgency urgency) {
        if (sortedFees.isEmpty()) {
            return 0L;
        }
        return switch (urgency) {
            case SLOW -> computePercentile(sortedFees, 50);
            case MEDIUM -> computePercentile(sortedFees, 75);
            case FAST -> computePercentile(sortedFees, 90);
            case URGENT -> BigDecimal.valueOf(sortedFees.getLast())
                    .multiply(URGENT_MULTIPLIER, MATH_CONTEXT)
                    .setScale(0, RoundingMode.CEILING)
                    .longValueExact();
        };
    }

    private BigDecimal computeEstimatedCost(BigDecimal computeUnitPrice) {
        return computeUnitPrice
                .multiply(BigDecimal.valueOf(DEFAULT_COMPUTE_UNITS), MATH_CONTEXT)
                .divide(MICRO_LAMPORTS_PER_LAMPORT, SCALE, RoundingMode.HALF_UP)
                .divide(LAMPORTS_PER_SOL, SCALE, RoundingMode.HALF_UP);
    }

    private void validateChain(String chain) {
        if (!chainProperties.chain().equals(chain)) {
            throw new IllegalArgumentException(
                    "Oracle for chain %s cannot serve chain %s".formatted(chainProperties.chain(), chain));
        }
    }

    private BigDecimal escalationMultiplierForAttempt(int attemptNumber) {
        return BigDecimal.ONE.add(
                new BigDecimal("0.1").multiply(BigDecimal.valueOf(attemptNumber), MATH_CONTEXT));
    }
}
