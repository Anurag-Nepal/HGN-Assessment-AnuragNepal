package com.hgn.sos.service;

import com.hgn.sos.dto.ClaimOutcome;
import com.hgn.sos.model.Alert;
import com.hgn.sos.repository.AlertRepository;
import com.hgn.sos.utils.AlertNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AlertClaimService {

    private static final Logger log = LoggerFactory.getLogger(AlertClaimService.class);
    private final AlertRepository alertRepository;
    private final AlertPushService pushService;

    public AlertClaimService(AlertRepository alertRepository,
                             AlertPushService pushService) {
        this.alertRepository = alertRepository;
        this.pushService = pushService;
    }

    @Transactional
    public ClaimOutcome claim(UUID alertId, String coordinatorId) {
        int rows = alertRepository.claimIfOpen(alertId, coordinatorId);

        if (rows == 0) {
            Alert current = alertRepository.findById(alertId)
                    .orElseThrow(() -> new AlertNotFoundException(alertId));
            String owner = current.getClaimedBy();
            return ClaimOutcome.conflict(current.getStatus(), owner);
        }

        Alert updated = alertRepository.findById(alertId).orElseThrow();
        try {
            pushService.pushClaimUpdate(updated);
        } catch (Exception e) {
            log.warn("Failed to push claim notification", e);
        }
        return ClaimOutcome.success(coordinatorId);
    }
}