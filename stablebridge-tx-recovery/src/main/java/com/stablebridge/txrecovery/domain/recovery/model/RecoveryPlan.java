package com.stablebridge.txrecovery.domain.recovery.model;

import java.time.Duration;
import java.util.Objects;

import lombok.Builder;

public sealed interface RecoveryPlan {

    @Builder(toBuilder = true)
    record SpeedUp(String originalTxHash, FeeEstimate newFee) implements RecoveryPlan {

        public SpeedUp {
            Objects.requireNonNull(originalTxHash);
            Objects.requireNonNull(newFee);
        }
    }

    @Builder(toBuilder = true)
    record Cancel(String originalTxHash) implements RecoveryPlan {

        public Cancel {
            Objects.requireNonNull(originalTxHash);
        }
    }

    @Builder(toBuilder = true)
    record Resubmit(String originalTxHash) implements RecoveryPlan {

        public Resubmit {
            Objects.requireNonNull(originalTxHash);
        }
    }

    @Builder(toBuilder = true)
    record Wait(Duration estimatedClearance, String reason) implements RecoveryPlan {

        public Wait {
            Objects.requireNonNull(reason);
        }
    }
}
