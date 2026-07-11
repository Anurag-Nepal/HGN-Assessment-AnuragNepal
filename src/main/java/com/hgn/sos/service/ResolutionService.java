package com.hgn.sos.service;

import com.hgn.sos.dto.ResolutionResult;
import com.hgn.sos.model.Order;
import com.hgn.sos.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class ResolutionService {

    private final OrderRepository orderRepository;

    @Value("${sos.resolution-grace-days:1}")
    private long graceDays;

    public ResolutionService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public ResolutionResult resolve(UUID deviceId, Instant alertTimestamp) {
        List<Order> exact = orderRepository
                .findActiveOrdersCoveringTimestamp(deviceId, alertTimestamp);

        if (exact.size() == 1) {
            return ResolutionResult.resolved(exact.get(0), false);
        }
        if (exact.size() > 1) {
            return ResolutionResult.ambiguous();
        }

        Instant graceStart = alertTimestamp.minus(graceDays, ChronoUnit.DAYS);
        Instant graceEnd = alertTimestamp.plus(graceDays, ChronoUnit.DAYS);
        List<Order> graceCandidates = orderRepository
                .findActiveOrdersWithinGraceWindow(deviceId, graceStart, graceEnd);

        if (graceCandidates.size() == 1) {
            return ResolutionResult.resolved(graceCandidates.get(0), true);
        }
        return ResolutionResult.ambiguous();
    }
}