package com.hgn.sos.intake;

import com.hgn.sos.alert.Alert;
import com.hgn.sos.alert.AlertRepository;
import com.hgn.sos.alert.AlertStatus;
import com.hgn.sos.dedup.DedupService;
import com.hgn.sos.incident.Incident;
import com.hgn.sos.incident.IncidentRepository;
import com.hgn.sos.resolution.ResolutionResult;
import com.hgn.sos.resolution.ResolutionService;
import com.hgn.sos.ws.AlertPushService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class SosIntakeService {

    private final DedupService dedupService;
    private final ResolutionService resolutionService;
    private final AlertRepository alertRepository;
    private final IncidentRepository incidentRepository;
    private final AlertPushService pushService;

    public SosIntakeService(DedupService dedupService,
                             ResolutionService resolutionService,
                             AlertRepository alertRepository,
                             IncidentRepository incidentRepository,
                             AlertPushService pushService) {
        this.dedupService = dedupService;
        this.resolutionService = resolutionService;
        this.alertRepository = alertRepository;
        this.incidentRepository = incidentRepository;
        this.pushService = pushService;
    }

    /**
     * Single entry point for MQTT listener and REST controller.
     * Order matters: dedup (cheap, Redis-only) BEFORE resolution (DB lookup)
     * so a duplicate retry never pays for a resolution query it'll discard.
     */
    public IntakeResult handleIncomingSos(SosPayload payload) {
        boolean isNew = dedupService.registerIfNew(
                payload.deviceId(), payload.deviceTimestamp(),
                payload.latitude(), payload.longitude());

        if (!isNew) {
            // Duplicate within the 60s window — no-op, don't create a
            // second alert. Devices retry every 20-60s on no-ack; broker
            // QoS 1 redelivery is covered by the same check.
            Alert existing = alertRepository
                    .findMostRecentByDeviceId(payload.deviceId())
                    .orElseThrow(); // shouldn't happen: dedup implies a prior alert exists
            return IntakeResult.duplicate(existing);
        }

        return createAlert(payload);
    }

    @Transactional
    protected IntakeResult createAlert(SosPayload payload) {
        ResolutionResult resolution = resolutionService.resolve(
                payload.deviceId(), payload.deviceTimestamp());

        Alert alert = new Alert();
        alert.setDeviceId(payload.deviceId());
        alert.setLatitude(payload.latitude());
        alert.setLongitude(payload.longitude());
        alert.setDeviceTimestamp(payload.deviceTimestamp());
        alert.setReceivedAt(Instant.now());
        alert.setStatus(AlertStatus.OPEN);

        if (resolution.isAmbiguous()) {
            // Never drop or guess — flag urgent immediately, leave
            // order/group null, and let a coordinator sort it out via the
            // manual-review queue (GET /api/alerts/unresolved-ownership).
            alert.setUrgent(true);
        } else {
            alert.setOrderId(resolution.getOrder().getId());
            alert.setGroupId(resolution.getOrder().getGroupId());
            alert.setResolvedViaGraceWindow(resolution.isResolvedViaGraceWindow());
        }

        Alert saved = alertRepository.save(alert);

        Incident incident = new Incident();
        incident.setAlertId(saved.getId());
        incidentRepository.save(incident);

        pushService.pushNewAlert(saved); // WebSocket push, real-time to operator dashboard
        return IntakeResult.created(saved);
    }
}