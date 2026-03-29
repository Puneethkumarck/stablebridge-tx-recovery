package com.stablebridge.txrecovery.infrastructure.db.transaction;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "transaction_intent")
@Getter
@Setter
class TransactionIntentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "intent_id", nullable = false, unique = true, length = 36)
    private String intentId;

    @Column(nullable = false, length = 50)
    private String chain;

    @Column(name = "to_address", nullable = false, length = 66)
    private String toAddress;

    @Column(nullable = false, precision = 36, scale = 18)
    private BigDecimal amount;

    @Column(nullable = false, length = 100)
    private String token;

    @Column(name = "token_decimals", nullable = false)
    private int tokenDecimals;

    @Column(name = "raw_amount")
    private BigInteger rawAmount;

    @Column(name = "token_contract_address", length = 66)
    private String tokenContractAddress;

    @Column(length = 30)
    private String strategy;

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, String> metadata;

    @Column(name = "batch_id", length = 36)
    private String batchId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
