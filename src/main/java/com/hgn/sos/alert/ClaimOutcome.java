package com.hgn.sos.alert;

public record ClaimOutcome(boolean success, AlertStatus status, String claimedBy) {
    public static ClaimOutcome success(String coordinatorId) {
        return new ClaimOutcome(true, AlertStatus.CLAIMED, coordinatorId);
    }
    public static ClaimOutcome conflict(AlertStatus status, String claimedBy) {
        return new ClaimOutcome(false, status, claimedBy);
    }
}