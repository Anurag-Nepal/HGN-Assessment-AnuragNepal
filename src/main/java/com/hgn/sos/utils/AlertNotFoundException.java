package com.hgn.sos.utils;

import java.util.UUID;

public class AlertNotFoundException extends RuntimeException {
    public AlertNotFoundException(UUID alertId) {
        super("Alert not found: " + alertId);
    }
}