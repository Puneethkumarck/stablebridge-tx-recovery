package com.stablebridge.txrecovery.application.controller.approval;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.stablebridge.txrecovery.api.model.ApproveTransactionRequest;
import com.stablebridge.txrecovery.api.model.ApproveTransactionResponse;
import com.stablebridge.txrecovery.api.model.CancelTransactionRequest;
import com.stablebridge.txrecovery.api.model.CancelTransactionResponse;
import com.stablebridge.txrecovery.application.controller.approval.mapper.ApprovalControllerMapper;
import com.stablebridge.txrecovery.domain.transaction.TransactionApprovalService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/transactions/{transactionId}")
@RequiredArgsConstructor
public class ApprovalController {

    private static final String SYSTEM_USER = "system";

    private final TransactionApprovalService transactionApprovalService;
    private final ApprovalControllerMapper approvalControllerMapper;

    @PostMapping("/approve")
    public ResponseEntity<ApproveTransactionResponse> approveTransaction(
            @PathVariable String transactionId,
            @Valid @RequestBody ApproveTransactionRequest request) {
        var action = approvalControllerMapper.toDomain(request.action());
        var result = transactionApprovalService.approveTransaction(
                transactionId, action, request.reason(), SYSTEM_USER);
        var response = approvalControllerMapper.toApproveResponse(result);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/cancel")
    public ResponseEntity<CancelTransactionResponse> cancelTransaction(
            @PathVariable String transactionId,
            @Valid @RequestBody CancelTransactionRequest request) {
        var result = transactionApprovalService.cancelTransaction(
                transactionId, request.reason(), SYSTEM_USER);
        var response = approvalControllerMapper.toCancelResponse(result);
        return ResponseEntity.ok(response);
    }
}
