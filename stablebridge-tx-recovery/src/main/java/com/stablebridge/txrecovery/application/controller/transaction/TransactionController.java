package com.stablebridge.txrecovery.application.controller.transaction;

import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
import com.stablebridge.txrecovery.domain.exception.BatchValidationException;
import com.stablebridge.txrecovery.domain.exception.DuplicateIntentException;
import com.stablebridge.txrecovery.domain.transaction.TransactionSubmissionService;
import com.stablebridge.txrecovery.domain.transaction.model.TransactionFilters;
import com.stablebridge.txrecovery.domain.transaction.model.TransactionStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
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
        validateBatchConsistency(request.transactions());

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
            @RequestParam(required = false) Instant fromDate,
            @RequestParam(required = false) Instant toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        var filters = TransactionFilters.builder()
                .chain(chain)
                .status(status != null ? TransactionStatus.valueOf(status) : null)
                .fromAddress(fromAddress)
                .toAddress(toAddress)
                .fromDate(fromDate)
                .toDate(toDate)
                .build();

        var projections = transactionSubmissionService.findByFilters(filters);
        var responses = transactionControllerMapper.toResponseList(projections);

        var pagedResponse = PagedResponse.<TransactionResponse>builder()
                .content(responses)
                .page(page)
                .size(size)
                .totalElements(responses.size())
                .totalPages(1)
                .build();

        return ResponseEntity.ok(pagedResponse);
    }

    private void validateBatchConsistency(List<SubmitTransactionRequest> transactions) {
        var firstChain = transactions.getFirst().chain();
        var firstToken = transactions.getFirst().token();

        var allConsistent = transactions.stream()
                .allMatch(tx -> firstChain.equals(tx.chain()) && firstToken.equals(tx.token()));

        if (!allConsistent) {
            throw new BatchValidationException("all transactions in a batch must have the same chain and token");
        }
    }
}
