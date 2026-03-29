package com.stablebridge.txrecovery.infrastructure.db.transaction;

import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.criteria.Predicate;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import com.stablebridge.txrecovery.domain.transaction.model.PagedResult;
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
        return parseUuid(id)
                .flatMap(jpaRepository::findById)
                .map(mapper::toDomain);
    }

    @Override
    public Optional<TransactionProjection> findByIntentId(String intentId) {
        return parseUuid(intentId)
                .flatMap(jpaRepository::findByIntentId)
                .map(mapper::toDomain);
    }

    @Override
    public PagedResult<TransactionProjection> findByFilters(TransactionFilters filters, int page, int size) {
        var spec = buildSpecification(filters);
        var springPage = jpaRepository.findAll(spec, PageRequest.of(page, size));
        var content = springPage.getContent().stream()
                .map(mapper::toDomain)
                .toList();
        return PagedResult.<TransactionProjection>builder()
                .content(content)
                .totalElements(springPage.getTotalElements())
                .totalPages(springPage.getTotalPages())
                .build();
    }

    private Optional<UUID> parseUuid(String value) {
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException _) {
            return Optional.empty();
        }
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
            if (filters.token() != null) {
                predicates.add(cb.equal(root.get("asset"), filters.token()));
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
