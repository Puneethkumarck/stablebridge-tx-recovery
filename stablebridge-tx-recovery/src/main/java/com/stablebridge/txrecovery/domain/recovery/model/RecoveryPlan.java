package com.stablebridge.txrecovery.domain.recovery.model;

import java.time.Duration;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import lombok.Builder;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "@type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = RecoveryPlan.SpeedUp.class, name = "SpeedUp"),
        @JsonSubTypes.Type(value = RecoveryPlan.Cancel.class, name = "Cancel"),
        @JsonSubTypes.Type(value = RecoveryPlan.Resubmit.class, name = "Resubmit"),
        @JsonSubTypes.Type(value = RecoveryPlan.Wait.class, name = "Wait")
})
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
