package com.hgn.sos.resolution;

import com.hgn.sos.order.Order;
import com.hgn.sos.order.OrderRepository;
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
        // Rule: ACTIVE status AND alertTimestamp within [start_date, end_date]
        List<Order> exact = orderRepository
                .findActiveOrdersCoveringTimestamp(deviceId, alertTimestamp);

        if (exact.size() == 1) {
            return ResolutionResult.resolved(exact.get(0), false);
        }
        if (exact.size() > 1) {
            // Double-booked device with overlapping ranges — a real scenario
            // per the architecture notes. Never guess: flag for manual review.
            return ResolutionResult.ambiguous();
        }

        // Zero exact matches: widen with the grace buffer for early starts/late returns.
        Instant graceStart = alertTimestamp.minus(graceDays, ChronoUnit.DAYS);
        Instant graceEnd = alertTimestamp.plus(graceDays, ChronoUnit.DAYS);
        List<Order> graceCandidates = orderRepository
                .findActiveOrdersWithinGraceWindow(deviceId, graceStart, graceEnd);

        if (graceCandidates.size() == 1) {
            return ResolutionResult.resolved(graceCandidates.get(0), true);
        }
        // 0 or 2+ candidates in the grace window: still ambiguous either way.
        return ResolutionResult.ambiguous();
    }
}