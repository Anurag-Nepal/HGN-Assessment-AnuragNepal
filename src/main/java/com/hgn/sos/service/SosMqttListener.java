package com.hgn.sos.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hgn.sos.dto.SosPayload;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

@Component
public class SosMqttListener {

    private final SosIntakeService intakeService;
    private final ObjectMapper objectMapper;

    public SosMqttListener(SosIntakeService intakeService, ObjectMapper objectMapper) {
        this.intakeService = intakeService;
        this.objectMapper = objectMapper;
    }

    @ServiceActivator(inputChannel = "mqttInputChannel")
    public void handle(Message<String> message) throws Exception {
        SosPayload payload = objectMapper.readValue(message.getPayload(), SosPayload.class);
        intakeService.handleIncomingSos(payload);
    }
}