package com.stablebridge.txrecovery.infrastructure.db.transaction;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.criteria.Predicate;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import com.stablebridge.txrecovery.domain.transaction.model.TransactionFilters;
import com.stablebridge.txrecovery.domain.transaction.model.TransactionProjection;
import com.stablebridge.txrecovery.domain.transaction.port.TransactionProjectionStore;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
class TransactionProjectionStoreAdapter implements TransactionProjectionStore {

    private final TransactionProjectionJpaRepository jpaRepository;
    private final TransactionProjectionEntityMapper mapper;

    @Override
    public void save(TransactionProjection projection) {
        var entity = mapper.toEntity(projection);
        jpaRepository.save(entity);
    }

    @Override
    public Optional<TransactionProjection> findById(String id) {
        return jpaRepository.findById(UUID.fromString(id))
                .map(mapper::toDomain);
    }

    @Override
    public Optional<TransactionProjection> findByIntentId(String intentId) {
        return jpaRepository.findByIntentId(UUID.fromString(intentId))
                .map(mapper::toDomain);
    }

    @Override
    public List<TransactionProjection> findByFilters(TransactionFilters filters) {
        Specification<TransactionProjectionEntity> spec = buildSpecification(filters);
        return jpaRepository.findAll(spec).stream()
                .map(mapper::toDomain)
                .toList();
    }

    private Specification<TransactionProjectionEntity> buildSpecification(TransactionFilters filters) {
        return (root, _, cb) -> {
            var predicates = new ArrayList<Predicate>();

            if (filters.chain() != null) {
                predicates.add(cb.equal(root.get("chain"), filters.chain()));
            }
            if (filters.status() != null) {
                predicates.add(cb.equal(root.get("status"), filters.status().name()));
            }
            if (filters.fromAddress() != null) {
                predicates.add(cb.equal(root.get("fromAddress"), filters.fromAddress()));
            }
            if (filters.toAddress() != null) {
                predicates.add(cb.equal(root.get("toAddress"), filters.toAddress()));
            }
            if (filters.fromDate() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), filters.fromDate()));
            }
            if (filters.toDate() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), filters.toDate()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
