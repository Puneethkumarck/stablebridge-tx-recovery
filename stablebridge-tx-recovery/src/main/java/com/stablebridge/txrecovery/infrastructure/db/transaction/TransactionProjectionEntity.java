package com.stablebridge.txrecovery.infrastructure.db.transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "transaction_projection")
@Getter
@Setter
class TransactionProjectionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "intent_id", nullable = false, unique = true)
    private UUID intentId;

    @Column(nullable = false, length = 50)
    private String chain;

    @Column(name = "chain_family", length = 20)
    private String chainFamily;

    @Column(name = "from_address", length = 66)
    private String fromAddress;

    @Column(name = "to_address", nullable = false, length = 66)
    private String toAddress;

    @Column(nullable = false, precision = 36, scale = 18)
    private BigDecimal amount;

    @Column(nullable = false, length = 100)
    private String asset;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "transaction_hash", length = 128)
    private String transactionHash;

    private Long nonce;

    @Column(name = "gas_price", precision = 36, scale = 18)
    private BigDecimal gasPrice;

    @Column(name = "gas_limit")
    private Long gasLimit;

    @Column(name = "gas_used")
    private Long gasUsed;

    @Column(name = "submission_strategy", length = 30)
    private String submissionStrategy;

    @Column(name = "recovery_count", nullable = false)
    private int recoveryCount;

    @Column(name = "current_recovery_action", length = 30)
    private String currentRecoveryAction;

    @Column(name = "last_recovery_at")
    private Instant lastRecoveryAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, String> metadata;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    @PrePersist
    private void onPrePersist() {
        var now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    private void onPreUpdate() {
        updatedAt = Instant.now();
    }
}
