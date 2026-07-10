package com.hgn.sos.dedup;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DedupServiceTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private DedupService dedupService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        dedupService = new DedupService(redisTemplate);
        ReflectionTestUtils.setField(dedupService, "ttlSeconds", 60L);
    }

    @Test
    void secondCallWithinWindowIsDuplicate() {
        UUID device = UUID.randomUUID();
        Instant now = Instant.now();

        // Simulate SETNX behavior: Returns true on first set, false if it already exists
        when(valueOperations.setIfAbsent(anyString(), eq("1"), eq(Duration.ofSeconds(60))))
                .thenReturn(true)  // First attempt sets the key successfully
                .thenReturn(false); // Second attempt hits existing key and fails

        assertTrue(dedupService.registerIfNew(device, now, 27.7, 85.3));
        assertFalse(dedupService.registerIfNew(device, now, 27.7, 85.3)); // duplicate
    }
}