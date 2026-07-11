package com.hgn.sos.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class DedupService {

    private static final Logger log = LoggerFactory.getLogger(DedupService.class);
    private final StringRedisTemplate redis;

    @Value("${sos.dedup-ttl-seconds:60}")
    private long ttlSeconds;

    public DedupService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public boolean registerIfNew(UUID deviceId, Instant deviceTimestamp,
                                  double lat, double lon) {
        String key = buildKey(deviceId, deviceTimestamp, lat, lon);
        try {
            Boolean wasSet = redis.opsForValue()
                    .setIfAbsent(key, "1", Duration.ofSeconds(ttlSeconds));
            return Boolean.TRUE.equals(wasSet);
        } catch (Exception e) {
            log.warn("Redis unavailable, allowing SOS through (fail-open): {}", e.getMessage());
            return true;
        }
    }

    private String buildKey(UUID deviceId, Instant ts, double lat, double lon) {
        long epochSecond = ts.getEpochSecond();
        return "sos:dedup:%s:%d:%.4f:%.4f".formatted(deviceId, epochSecond, lat, lon);
    }
}