package com.hgn.sos.service;

import com.hgn.sos.dto.AlertDto;
import com.hgn.sos.model.Alert;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class AlertPushService {

    private final SimpMessagingTemplate messagingTemplate;

    public AlertPushService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void pushNewAlert(Alert alert) {
        messagingTemplate.convertAndSend("/topic/alerts", AlertDto.from(alert));
    }

    public void pushEscalation(Alert alert) {
        messagingTemplate.convertAndSend("/topic/alerts/escalated", AlertDto.from(alert));
    }

    public void pushClaimUpdate(Alert alert, String coordinatorId) {
        messagingTemplate.convertAndSend("/topic/alerts/claimed", AlertDto.from(alert));
    }
}