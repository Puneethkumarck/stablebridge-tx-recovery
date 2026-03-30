package com.stablebridge.txrecovery.application.controller.gas;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.stablebridge.txrecovery.api.model.GasEstimateResponse;
import com.stablebridge.txrecovery.api.model.GasHistoryResponse;
import com.stablebridge.txrecovery.application.controller.gas.mapper.GasOracleControllerMapper;
import com.stablebridge.txrecovery.domain.recovery.GasOracleQueryService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/gas")
@RequiredArgsConstructor
public class GasOracleController {

    private final GasOracleQueryService gasOracleQueryService;
    private final GasOracleControllerMapper mapper;

    @GetMapping("/{chain}")
    public ResponseEntity<GasEstimateResponse> getCurrentEstimates(@PathVariable String chain) {
        var snapshot = gasOracleQueryService.getCurrentEstimates(chain);
        var response = mapper.toResponse(snapshot);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{chain}/history")
    public ResponseEntity<GasHistoryResponse> getHistory(
            @PathVariable String chain,
            @RequestParam(defaultValue = "24") @Min(1) @Max(168) int hours) {
        var estimates = gasOracleQueryService.getHistoricalEstimates(chain, hours);
        var response = mapper.toHistoryResponse(chain, hours, estimates);
        return ResponseEntity.ok(response);
    }
}
