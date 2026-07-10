package com.hgn.sos.alert;

import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/alerts")
public class AlertQueryController {

    private final AlertRepository alertRepository;

    public AlertQueryController(AlertRepository alertRepository) {
        this.alertRepository = alertRepository;
    }

    @GetMapping
    public List<Alert> list(@RequestParam(required = false) AlertStatus status) {
        return status == null ? alertRepository.findAll() : alertRepository.findByStatus(status);
    }

    // The queue your diagram's ambiguous-resolution branch feeds into.
    @GetMapping("/unresolved-ownership")
    public List<Alert> unresolvedOwnership() {
        return alertRepository.findByOrderIdIsNull();
    }
}