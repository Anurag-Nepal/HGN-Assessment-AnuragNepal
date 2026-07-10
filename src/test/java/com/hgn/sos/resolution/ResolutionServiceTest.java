package com.hgn.sos.resolution;

import com.hgn.sos.order.Order;
import com.hgn.sos.order.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ResolutionServiceTest {

    private OrderRepository orderRepository;
    private ResolutionService resolutionService;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        resolutionService = new ResolutionService(orderRepository);
        ReflectionTestUtils.setField(resolutionService, "graceDays", 1L);
    }

    @Test
    void singleExactMatch_isResolved() {
        UUID deviceId = UUID.randomUUID();
        Instant now = Instant.now();
        Order order = new Order();
        
        when(orderRepository.findActiveOrdersCoveringTimestamp(deviceId, now))
                .thenReturn(List.of(order));

        ResolutionResult result = resolutionService.resolve(deviceId, now);
        
        assertFalse(result.isAmbiguous());
        assertFalse(result.isResolvedViaGraceWindow());
        assertEquals(order, result.getOrder());
    }

    @Test
    void doubleBookedExactMatch_isAmbiguous() {
        UUID deviceId = UUID.randomUUID();
        Instant now = Instant.now();
        
        when(orderRepository.findActiveOrdersCoveringTimestamp(deviceId, now))
                .thenReturn(List.of(new Order(), new Order()));

        ResolutionResult result = resolutionService.resolve(deviceId, now);
        
        assertTrue(result.isAmbiguous());
        assertNull(result.getOrder());
    }

    @Test
    void zeroExact_singleGraceWindow_isResolvedViaGrace() {
        UUID deviceId = UUID.randomUUID();
        Instant now = Instant.now();
        Order order = new Order();
        
        when(orderRepository.findActiveOrdersCoveringTimestamp(deviceId, now))
                .thenReturn(Collections.emptyList());
        when(orderRepository.findActiveOrdersWithinGraceWindow(eq(deviceId), any(), any()))
                .thenReturn(List.of(order));

        ResolutionResult result = resolutionService.resolve(deviceId, now);
        
        assertFalse(result.isAmbiguous());
        assertTrue(result.isResolvedViaGraceWindow());
        assertEquals(order, result.getOrder());
    }

    @Test
    void zeroExact_zeroGrace_isAmbiguous() {
        UUID deviceId = UUID.randomUUID();
        Instant now = Instant.now();
        
        when(orderRepository.findActiveOrdersCoveringTimestamp(deviceId, now))
                .thenReturn(Collections.emptyList());
        when(orderRepository.findActiveOrdersWithinGraceWindow(eq(deviceId), any(), any()))
                .thenReturn(Collections.emptyList());

        ResolutionResult result = resolutionService.resolve(deviceId, now);
        
        assertTrue(result.isAmbiguous());
        assertNull(result.getOrder());
    }
}