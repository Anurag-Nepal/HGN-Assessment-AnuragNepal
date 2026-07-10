package com.hgn.sos.alert;

import com.hgn.sos.incident.Incident;
import com.hgn.sos.incident.IncidentRepository;
import com.hgn.sos.ws.AlertPushService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class AlertClaimService {

    private final AlertRepository alertRepository;
    private final IncidentRepository incidentRepository;
    private final AlertPushService pushService;

    public AlertClaimService(AlertRepository alertRepository,
                              IncidentRepository incidentRepository,
                              AlertPushService pushService) {
        this.alertRepository = alertRepository;
        this.incidentRepository = incidentRepository;
        this.pushService = pushService;
    }

    @Transactional
    public ClaimOutcome claim(UUID alertId, String coordinatorId) {
        // The WHERE status = 'OPEN' clause in this atomic UPDATE is what
        // actually prevents the double-claim: only one concurrent caller's
        // UPDATE can match the row before the first commits.
        int rows = alertRepository.claimIfOpen(alertId);

        if (rows == 0) {
            Alert current = alertRepository.findById(alertId)
                    .orElseThrow(() -> new AlertNotFoundException(alertId));
            String owner = incidentRepository.findByAlertId(alertId)
                    .map(Incident::getClaimedBy).orElse(null);
            return ClaimOutcome.conflict(current.getStatus(), owner);
        }

        incidentRepository.updateClaim(alertId, coordinatorId, Instant.now());
        Alert updated = alertRepository.findById(alertId).orElseThrow();
        pushService.pushClaimUpdate(updated, coordinatorId);
        return ClaimOutcome.success(coordinatorId);
    }
}