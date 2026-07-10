package com.hgn.sos.ws;

import com.hgn.sos.alert.Alert;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class AlertPushService {

    private final SimpMessagingTemplate messagingTemplate;

    public AlertPushService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void pushNewAlert(Alert alert) {
        // Operators subscribe to /topic/alerts on the dashboard.
        messagingTemplate.convertAndSend("/topic/alerts", AlertDto.from(alert));
    }

    public void pushEscalation(Alert alert) {
        messagingTemplate.convertAndSend("/topic/alerts/escalated", AlertDto.from(alert));
    }

    public void pushClaimUpdate(Alert alert, String coordinatorId) {
        messagingTemplate.convertAndSend("/topic/alerts/claimed", AlertDto.from(alert));
    }
}