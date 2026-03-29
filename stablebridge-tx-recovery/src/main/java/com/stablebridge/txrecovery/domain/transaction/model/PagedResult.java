package com.stablebridge.txrecovery.domain.transaction.model;

import java.util.List;

import lombok.Builder;

@Builder(toBuilder = true)
public record PagedResult<T>(
        List<T> content,
        long totalElements,
        int totalPages) {
}
