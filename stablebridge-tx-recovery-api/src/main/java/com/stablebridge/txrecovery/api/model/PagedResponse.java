package com.stablebridge.txrecovery.api.model;

import java.util.List;

import lombok.Builder;

@Builder(toBuilder = true)
public record PagedResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {}
