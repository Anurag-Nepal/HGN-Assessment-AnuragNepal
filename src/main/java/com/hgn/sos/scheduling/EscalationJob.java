package com.hgn.sos.scheduling;

import com.hgn.sos.alert.Alert;
import com.hgn.sos.alert.AlertRepository;
import com.hgn.sos.ws.AlertPushService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
public class EscalationJob {

    private final AlertRepository alertRepository;
    private final AlertPushService pushService;

    @Value("${sos.escalation-window-seconds:300}")
    private long escalationWindowSeconds;

    public EscalationJob(AlertRepository alertRepository, AlertPushService pushService) {
        this.alertRepository = alertRepository;
        this.pushService = pushService;
    }

    // Runs independently of intake/claim — scans the alert table directly,
    // not triggered by any pipeline step.
    @Scheduled(fixedRateString = "${sos.escalation-poll-ms:30000}")
    @Transactional
    public void escalateStaleAlerts() {
        Instant cutoff = Instant.now().minusSeconds(escalationWindowSeconds);
        List<Alert> escalated = alertRepository.escalateOlderThan(cutoff);
        escalated.forEach(pushService::pushEscalation);
    }
}