package com.stablebridge.txrecovery.infrastructure.db.address;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "address_pool")
@Getter
@Setter
class AddressPoolEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String address;

    @Column(nullable = false)
    private String chain;

    @Column(name = "chain_family", nullable = false)
    private String chainFamily;

    @Column(nullable = false)
    private String tier;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private com.stablebridge.txrecovery.domain.address.model.AddressStatus status;

    @Column(name = "signer_endpoint")
    private String signerEndpoint;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    @Column(name = "retired_at")
    private Instant retiredAt;

    @Column(name = "current_nonce", nullable = false)
    private long currentNonce;

    @Column(name = "in_flight_count", nullable = false)
    private int inFlightCount;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;
}
