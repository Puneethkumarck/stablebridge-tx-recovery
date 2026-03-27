package com.stablebridge.txrecovery.infrastructure.db.nonce;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.stablebridge.txrecovery.domain.address.model.NonceAccountStatus;

import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "nonce_account_pool")
@Getter
@Setter
class NonceAccountPoolEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "nonce_account", nullable = false)
    private String nonceAccount;

    @Column(name = "authority_address", nullable = false)
    private String authorityAddress;

    @Column(nullable = false)
    private String chain;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private NonceAccountStatus status;

    @Column(name = "current_nonce_value")
    private String currentNonceValue;

    @Column(name = "allocated_to_tx")
    private UUID allocatedToTx;
}
