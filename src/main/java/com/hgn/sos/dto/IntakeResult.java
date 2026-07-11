package com.hgn.sos.dto;

import com.hgn.sos.model.Alert;

public record IntakeResult(Alert alert, boolean duplicate) {
    public static IntakeResult created(Alert alert) { return new IntakeResult(alert, false); }
    public static IntakeResult duplicate(Alert alert) { return new IntakeResult(alert, true); }
}