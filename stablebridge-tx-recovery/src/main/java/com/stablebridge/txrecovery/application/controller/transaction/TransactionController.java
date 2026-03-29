package com.stablebridge.txrecovery.application.controller.transaction;

import java.time.Instant;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.github.f4b6a3.uuid.UuidCreator;
import com.stablebridge.txrecovery.api.model.BatchTransactionResponse;
import com.stablebridge.txrecovery.api.model.PagedResponse;
import com.stablebridge.txrecovery.api.model.SubmitBatchRequest;
import com.stablebridge.txrecovery.api.model.SubmitTransactionRequest;
import com.stablebridge.txrecovery.api.model.TransactionResponse;
import com.stablebridge.txrecovery.application.controller.transaction.mapper.TransactionControllerMapper;
import com.stablebridge.txrecovery.domain.exception.DuplicateIntentException;
import com.stablebridge.txrecovery.domain.transaction.TransactionSubmissionService;
import com.stablebridge.txrecovery.domain.transaction.model.TransactionFilters;
import com.stablebridge.txrecovery.domain.transaction.model.TransactionStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionSubmissionService transactionSubmissionService;
    private final TransactionControllerMapper transactionControllerMapper;

    @PostMapping
    public ResponseEntity<TransactionResponse> submitTransaction(
            @Valid @RequestBody SubmitTransactionRequest request) {
        try {
            var intent = transactionControllerMapper.toDomain(request);
            var projection = transactionSubmissionService.submitTransaction(intent);
            transactionSubmissionService.startWorkflowAfterCommit(intent);
            var response = transactionControllerMapper.toResponse(projection);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (DuplicateIntentException ex) {
            var existing = transactionSubmissionService.findById(ex.getExistingTransactionId());
            var response = transactionControllerMapper.toResponse(existing);
            return ResponseEntity.ok(response);
        }
    }

    @PostMapping("/batch")
    public ResponseEntity<BatchTransactionResponse> submitBatch(
            @Valid @RequestBody SubmitBatchRequest request) {
        var batchId = UuidCreator.getTimeOrderedEpoch().toString();
        var intents = request.transactions().stream()
                .map(transactionControllerMapper::toDomain)
                .toList();

        var projections = transactionSubmissionService.submitBatch(intents, batchId);
        var responses = transactionControllerMapper.toResponseList(projections);

        var batchResponse = BatchTransactionResponse.builder()
                .batchId(batchId)
                .transactions(responses)
                .createdAt(Instant.now())
                .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(batchResponse);
    }

    @GetMapping("/{transactionId}")
    public ResponseEntity<TransactionResponse> getTransaction(@PathVariable String transactionId) {
        var projection = transactionSubmissionService.findById(transactionId);
        var response = transactionControllerMapper.toResponse(projection);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<PagedResponse<TransactionResponse>> listTransactions(
            @RequestParam(required = false) String chain,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String fromAddress,
            @RequestParam(required = false) String toAddress,
            @RequestParam(required = false) String token,
            @RequestParam(required = false) Instant fromDate,
            @RequestParam(required = false) Instant toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        var filters = TransactionFilters.builder()
                .chain(chain)
                .status(status != null ? TransactionStatus.valueOf(status) : null)
                .fromAddress(fromAddress)
                .toAddress(toAddress)
                .token(token)
                .fromDate(fromDate)
                .toDate(toDate)
                .build();

        var pagedProjections = transactionSubmissionService.findByFilters(filters, page, size);
        var responses = transactionControllerMapper.toResponseList(pagedProjections.content());

        var pagedResponse = PagedResponse.<TransactionResponse>builder()
                .content(responses)
                .page(page)
                .size(size)
                .totalElements(pagedProjections.totalElements())
                .totalPages(pagedProjections.totalPages())
                .build();

        return ResponseEntity.ok(pagedResponse);
    }
}
