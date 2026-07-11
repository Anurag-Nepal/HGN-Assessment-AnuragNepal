package com.hgn.sos.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hgn.sos.config.AlertWebSocketHandler;
import com.hgn.sos.dto.AlertDto;
import com.hgn.sos.model.Alert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AlertPushService {

    private static final Logger log = LoggerFactory.getLogger(AlertPushService.class);
    private final AlertWebSocketHandler handler;
    private final ObjectMapper objectMapper;

    public AlertPushService(AlertWebSocketHandler handler, ObjectMapper objectMapper) {
        this.handler = handler;
        this.objectMapper = objectMapper;
    }

    public void pushNewAlert(Alert alert) {
        broadcast(AlertDto.from(alert));
    }

    public void pushEscalation(Alert alert) {
        broadcast(AlertDto.from(alert));
    }

    public void pushClaimUpdate(Alert alert) {
        broadcast(AlertDto.from(alert));
    }

    private void broadcast(AlertDto dto) {
        try {
            String json = objectMapper.writeValueAsString(dto);
            handler.broadcast(json);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize AlertDto", e);
        }
    }
}