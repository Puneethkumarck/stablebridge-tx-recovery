package com.stablebridge.txrecovery.domain.model;

import java.time.Duration;
import java.util.Objects;

import lombok.Builder;

public sealed interface RecoveryPlan {

    @Builder
    record SpeedUp(String originalTxHash, FeeEstimate newFee) implements RecoveryPlan {

        public SpeedUp {
            Objects.requireNonNull(originalTxHash);
            Objects.requireNonNull(newFee);
        }
    }

    @Builder
    record Cancel(String originalTxHash) implements RecoveryPlan {

        public Cancel {
            Objects.requireNonNull(originalTxHash);
        }
    }

    @Builder
    record Resubmit(String originalTxHash) implements RecoveryPlan {

        public Resubmit {
            Objects.requireNonNull(originalTxHash);
        }
    }

    @Builder
    record Wait(Duration estimatedClearance, String reason) implements RecoveryPlan {

        public Wait {
            Objects.requireNonNull(reason);
        }
    }
}
