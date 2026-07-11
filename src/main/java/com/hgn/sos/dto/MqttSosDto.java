package com.hgn.sos.dto;

import java.time.Instant;
import java.util.UUID;

public record MqttSosDto(UUID deviceId, double latitude, double longitude, Instant timestamp) {}