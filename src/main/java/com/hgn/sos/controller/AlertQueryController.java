package com.hgn.sos.controller;

import com.hgn.sos.dto.AlertDto;
import com.hgn.sos.model.AlertStatus;
import com.hgn.sos.repository.AlertRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/alerts")
public class AlertQueryController {

    private static final Sort FIXED_SORT = Sort.by(Sort.Direction.DESC, "deviceTimestamp");

    private final AlertRepository alertRepository;

    public AlertQueryController(AlertRepository alertRepository) {
        this.alertRepository = alertRepository;
    }

    @GetMapping
    public List<AlertDto> list(@RequestParam(required = false) AlertStatus status,
                               @RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "50") int size) {
        var pageable = PageRequest.of(page, size, FIXED_SORT);
        return (status == null
                ? alertRepository.findAll(pageable)
                : alertRepository.findByStatus(status, pageable))
                .stream()
                .map(AlertDto::from)
                .toList();
    }

    @GetMapping("/unresolved-ownership")
    public List<AlertDto> unresolvedOwnership() {
        return alertRepository.findUnresolvedOwnership().stream()
                .map(AlertDto::from)
                .toList();
    }
}