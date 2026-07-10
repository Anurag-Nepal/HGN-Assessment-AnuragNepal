package com.hgn.sos.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hgn.sos.intake.SosIntakeService;
import com.hgn.sos.intake.SosPayload;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class SosMqttListener {

    private final SosIntakeService intakeService;
    private final ObjectMapper objectMapper;

    public SosMqttListener(SosIntakeService intakeService, ObjectMapper objectMapper) {
        this.intakeService = intakeService;
        this.objectMapper = objectMapper;
    }

    private record MqttSosDto(UUID deviceId, double latitude, double longitude, Instant timestamp) {}

    @ServiceActivator(inputChannel = "mqttInputChannel")
    public void handle(Message<String> message) throws Exception {
        MqttSosDto dto = objectMapper.readValue(message.getPayload(), MqttSosDto.class);
        intakeService.handleIncomingSos(new SosPayload(
                dto.deviceId(), dto.latitude(), dto.longitude(), dto.timestamp()));
    }
}