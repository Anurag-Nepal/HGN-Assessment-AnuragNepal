package com.hgn.sos.ws;

import com.hgn.sos.alert.Alert;
import org.springframework.stereotype.Service;

@Service
public class AlertPushService {
    public void pushNewAlert(Alert alert) {}
    public void pushEscalation(Alert alert) {}
    public void pushClaimUpdate(Alert alert, String coordinatorId) {}
}