package com.hgn.sos.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

public record SosPayload(UUID deviceId, double latitude, double longitude,
                         @JsonProperty("timestamp") Instant deviceTimestamp) {}