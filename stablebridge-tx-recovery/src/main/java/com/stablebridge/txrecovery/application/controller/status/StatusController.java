package com.stablebridge.txrecovery.application.controller.status;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.stablebridge.txrecovery.api.model.ChainStatusDetailResponse;
import com.stablebridge.txrecovery.api.model.ChainStatusSummaryResponse;
import com.stablebridge.txrecovery.application.controller.status.mapper.StatusControllerMapper;
import com.stablebridge.txrecovery.domain.status.StatusService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/status")
@RequiredArgsConstructor
public class StatusController {

    private final StatusService statusService;
    private final StatusControllerMapper mapper;

    @GetMapping
    public ResponseEntity<List<ChainStatusSummaryResponse>> getAllStatuses() {
        var statuses = statusService.getAllChainStatuses();
        var responses = mapper.toSummaryResponseList(statuses);
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{chain}")
    public ResponseEntity<ChainStatusDetailResponse> getChainStatus(@PathVariable String chain) {
        var status = statusService.getChainStatus(chain);
        var response = mapper.toDetailResponse(status);
        return ResponseEntity.ok(response);
    }
}
