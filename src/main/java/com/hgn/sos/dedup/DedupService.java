package com.hgn.sos.dedup;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class DedupService {

    private final StringRedisTemplate redis;

    @Value("${sos.dedup-ttl-seconds:60}")
    private long ttlSeconds;

    public DedupService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * Returns true if this is a NEW signal (not seen before within the TTL
     * window), false if it's a duplicate. Uses SETNX semantics so the
     * check-and-set is atomic — two near-simultaneous retries can't both
     * pass.
     */
    public boolean registerIfNew(UUID deviceId, Instant deviceTimestamp,
                                  double lat, double lon) {
        String key = buildKey(deviceId, deviceTimestamp, lat, lon);
        Boolean wasSet = redis.opsForValue()
                .setIfAbsent(key, "1", Duration.ofSeconds(ttlSeconds));
        return Boolean.TRUE.equals(wasSet);
    }

    private String buildKey(UUID deviceId, Instant ts, double lat, double lon) {
        // Round timestamp to the second and coords to ~11m precision (4 dp)
        // so near-identical retries collide on the same key even with tiny
        // float jitter from the device.
        long epochSecond = ts.getEpochSecond();
        return "sos:dedup:%s:%d:%.4f:%.4f".formatted(deviceId, epochSecond, lat, lon);
    }
}