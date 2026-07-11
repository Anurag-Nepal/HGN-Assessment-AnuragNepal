package com.hgn.sos.service;

import com.hgn.sos.model.Alert;
import com.hgn.sos.repository.AlertRepository;
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

    @Scheduled(fixedRateString = "${sos.escalation-poll-ms:30000}")
    @Transactional
    public void escalateStaleAlerts() {
        Instant cutoff = Instant.now().minusSeconds(escalationWindowSeconds);
        List<Alert> escalated = alertRepository.escalateOlderThan(cutoff);
        escalated.forEach(pushService::pushEscalation);
    }
}