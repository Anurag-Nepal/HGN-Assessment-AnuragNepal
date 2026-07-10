package com.hgn.sos.intake;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/sos")
public class SosIntakeController {

    private final SosIntakeService intakeService;

    public SosIntakeController(SosIntakeService intakeService) {
        this.intakeService = intakeService;
    }

    public record SosRequest(
            @NotNull UUID deviceId, double latitude, double longitude,
            @NotNull Instant deviceTimestamp) {}

    public record SosResponse(UUID alertId, boolean duplicate) {}

    @PostMapping
    public ResponseEntity<SosResponse> submit(@Valid @RequestBody SosRequest req) {
        IntakeResult result = intakeService.handleIncomingSos(
                new SosPayload(req.deviceId(), req.latitude(), req.longitude(), req.deviceTimestamp()));
        return ResponseEntity.ok(new SosResponse(result.alert().getId(), result.duplicate()));
    }
}