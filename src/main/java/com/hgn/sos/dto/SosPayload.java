package com.hgn.sos.dto;

import java.time.Instant;
import java.util.UUID;

public record SosPayload(UUID deviceId, double latitude, double longitude, Instant deviceTimestamp) {}