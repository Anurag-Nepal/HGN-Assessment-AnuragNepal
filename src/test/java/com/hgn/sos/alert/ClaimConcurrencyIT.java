package com.hgn.sos.alert;

import com.hgn.sos.intake.SosIntakeService;
import com.hgn.sos.intake.SosPayload;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class ClaimConcurrencyIT {

    @Autowired private SosIntakeService intakeService;
    @Autowired private AlertClaimService claimService;

    @Test
    void exactlyOneClaimSucceedsUnderConcurrentAttempts() throws Exception {
        UUID deviceId = UUID.randomUUID(); // seed a matching active order in test setup
        var result = intakeService.handleIncomingSos(
                new SosPayload(deviceId, 27.7, 85.3, Instant.now()));
        UUID alertId = result.alert().getId();

        int coordinatorCount = 10;
        ExecutorService pool = Executors.newFixedThreadPool(coordinatorCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(coordinatorCount);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();

        for (int i = 0; i < coordinatorCount; i++) {
            String coordinatorId = "coordinator-" + i;
            pool.submit(() -> {
                try {
                    startLatch.await();
                    ClaimOutcome outcome = claimService.claim(alertId, coordinatorId);
                    (outcome.success() ? successes : conflicts).incrementAndGet();
                } catch (InterruptedException ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(10, TimeUnit.SECONDS);
        pool.shutdown();

        assertEquals(1, successes.get(), "exactly one coordinator should win the claim");
        assertEquals(coordinatorCount - 1, conflicts.get(), "everyone else should get a conflict");
    }
}