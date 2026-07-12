package com.hgn.sos.dto;

import com.hgn.sos.model.Alert;

import java.time.Instant;
import java.util.UUID;

public record AlertDto(UUID id,
                       UUID deviceId,
                       UUID orderId,
                       UUID groupId,
                       double latitude,
                       double longitude,
                       String status,
                       boolean urgent,
                       Instant receivedAt,
                       String claimedBy,
                       Instant claimedAt,
                       Instant resolvedAt,
                       String notes) {
    
    public static AlertDto from(Alert a) {
        return new AlertDto(a.getId(),
                a.getDeviceId(),
                a.getOrderId(),
                a.getGroupId(),
                a.getLatitude(),
                a.getLongitude(),
                a.getStatus().name(),
                a.isUrgent(),
                a.getReceivedAt(),
                a.getClaimedBy(),
                a.getClaimedAt(),
                a.getResolvedAt(),
                a.getNotes());
    }
}