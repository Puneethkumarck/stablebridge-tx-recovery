package com.stablebridge.txrecovery.application.controller.address;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.stablebridge.txrecovery.api.model.AddressResponse;
import com.stablebridge.txrecovery.api.model.DrainResponse;
import com.stablebridge.txrecovery.api.model.NonceSyncResponse;
import com.stablebridge.txrecovery.api.model.RegisterAddressRequest;
import com.stablebridge.txrecovery.application.controller.address.mapper.AddressPoolControllerMapper;
import com.stablebridge.txrecovery.domain.address.AddressPoolService;
import com.stablebridge.txrecovery.domain.address.model.AddressStatus;
import com.stablebridge.txrecovery.domain.address.model.AddressTier;
import com.stablebridge.txrecovery.domain.exception.InvalidParameterException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/addresses")
@RequiredArgsConstructor
public class AddressPoolController {

    private final AddressPoolService addressPoolService;
    private final AddressPoolControllerMapper mapper;

    @PostMapping
    public ResponseEntity<AddressResponse> register(@Valid @RequestBody RegisterAddressRequest request) {
        var tier = parseTier(request.tier());
        var pooledAddress = addressPoolService.register(
                request.address(), request.chain(), tier, request.signerEndpoint());
        var response = mapper.toResponse(pooledAddress);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<AddressResponse>> list(
            @RequestParam(required = false) String chain,
            @RequestParam(required = false) String tier,
            @RequestParam(required = false) String status) {
        var parsedTier = tier != null ? parseTier(tier) : null;
        var parsedStatus = status != null ? parseStatus(status) : null;
        var addresses = addressPoolService.list(chain, parsedTier, parsedStatus);
        var responses = mapper.toResponseList(addresses);
        return ResponseEntity.ok(responses);
    }

    @DeleteMapping("/{address}")
    public ResponseEntity<DrainResponse> drain(
            @PathVariable String address,
            @RequestParam String chain) {
        var result = addressPoolService.drain(address, chain);
        var response = mapper.toDrainResponse(result);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{address}/nonces/sync")
    public ResponseEntity<NonceSyncResponse> syncNonce(
            @PathVariable String address,
            @RequestParam String chain) {
        var result = addressPoolService.syncNonce(address, chain);
        var response = NonceSyncResponse.builder()
                .address(result.address().address())
                .chain(result.address().chain())
                .previousNonce(result.previousNonce())
                .currentNonce(result.currentNonce())
                .build();
        return ResponseEntity.ok(response);
    }

    private AddressTier parseTier(String tier) {
        try {
            return AddressTier.valueOf(tier);
        } catch (IllegalArgumentException _) {
            throw new InvalidParameterException("tier", tier);
        }
    }

    private AddressStatus parseStatus(String status) {
        try {
            return AddressStatus.valueOf(status);
        } catch (IllegalArgumentException _) {
            throw new InvalidParameterException("status", status);
        }
    }
}
