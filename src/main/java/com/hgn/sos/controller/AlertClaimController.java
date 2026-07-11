package com.hgn.sos.controller;

import com.hgn.sos.dto.ClaimOutcome;
import com.hgn.sos.dto.ClaimRequest;
import com.hgn.sos.service.AlertClaimService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/alerts")
public class AlertClaimController {

    private final AlertClaimService claimService;

    public AlertClaimController(AlertClaimService claimService) {
        this.claimService = claimService;
    }

    @PostMapping("/{id}/claim")
    public ResponseEntity<ClaimOutcome> claim(@PathVariable UUID id, @RequestBody ClaimRequest req) {
        ClaimOutcome outcome = claimService.claim(id, req.coordinatorId());
        return outcome.success()
                ? ResponseEntity.ok(outcome)
                : ResponseEntity.status(HttpStatus.CONFLICT).body(outcome);
    }
}