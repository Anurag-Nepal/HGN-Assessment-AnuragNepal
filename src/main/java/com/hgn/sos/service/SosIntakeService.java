package com.hgn.sos.service;

import com.hgn.sos.dto.IntakeResult;
import com.hgn.sos.dto.ResolutionResult;
import com.hgn.sos.dto.SosPayload;
import com.hgn.sos.model.Alert;
import com.hgn.sos.model.AlertStatus;
import com.hgn.sos.repository.AlertRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class SosIntakeService {

    private static final Logger log = LoggerFactory.getLogger(SosIntakeService.class);
    private final DedupService dedupService;
    private final ResolutionService resolutionService;
    private final AlertRepository alertRepository;
    private final AlertPushService pushService;

    public SosIntakeService(DedupService dedupService,
                            ResolutionService resolutionService,
                            AlertRepository alertRepository,
                            AlertPushService pushService) {
        this.dedupService = dedupService;
        this.resolutionService = resolutionService;
        this.alertRepository = alertRepository;
        this.pushService = pushService;
    }

    @Transactional
    public IntakeResult handleIncomingSos(SosPayload payload) {
        boolean isNew = dedupService.registerIfNew(
                payload.deviceId(), payload.deviceTimestamp(),
                payload.latitude(), payload.longitude());

        if (!isNew) {
            Alert existing = alertRepository
                    .findMostRecentByDeviceId(payload.deviceId())
                    .orElseThrow();
            return IntakeResult.duplicate(existing);
        }

        return createAlert(payload);
    }

    private IntakeResult createAlert(SosPayload payload) {
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
            alert.setUrgent(true);
        } else {
            alert.setOrderId(resolution.getOrder().getId());
            alert.setGroupId(resolution.getOrder().getGroupId());
            alert.setResolvedViaGraceWindow(resolution.isResolvedViaGraceWindow());
        }

        Alert saved = alertRepository.save(alert);

        try {
            pushService.pushNewAlert(saved);
        } catch (Exception e) {
            log.warn("Failed to push alert notification", e);
        }

        return IntakeResult.created(saved);
    }
}