package com.hgn.sos.intake;

import com.hgn.sos.alert.Alert;

public record IntakeResult(Alert alert, boolean duplicate) {
    public static IntakeResult created(Alert alert) { return new IntakeResult(alert, false); }
    public static IntakeResult duplicate(Alert alert) { return new IntakeResult(alert, true); }
}