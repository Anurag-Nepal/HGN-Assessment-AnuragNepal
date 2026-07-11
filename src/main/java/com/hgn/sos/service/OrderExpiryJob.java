package com.hgn.sos.service;

import com.hgn.sos.repository.OrderRepository;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
public class OrderExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(OrderExpiryJob.class);
    private final OrderRepository orderRepository;

    public OrderExpiryJob(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Scheduled(fixedRate = 3600000)
    @SchedulerLock(name = "expireCompletedOrders", lockAtMostFor = "5m")
    @Transactional
    public void expireCompletedOrders() {
        int updated = orderRepository.expireOrdersEndedBefore(Instant.now());
        if (updated > 0) {
            log.info("Expired {} completed orders", updated);
        }
    }
}