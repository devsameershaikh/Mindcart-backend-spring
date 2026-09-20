package com.mindcart.backend.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

/**
 * Lightweight in-memory rate limiter keyed by client IP (or any other key
 * you pass in, e.g. email for login attempts). Good enough to blunt brute
 * force / credential-stuffing against a single instance; for a multi-node
 * deployment back this with Redis instead (bucket4j has a distributed mode)
 * -- noted in the README.
 */
@Component
public class RateLimiter {

    private final ConcurrentMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public boolean tryConsume(String key, int capacity, int refillPerMinute) {
        Bucket bucket = buckets.computeIfAbsent(key, k -> newBucket(capacity, refillPerMinute));
        return bucket.tryConsume(1);
    }

    private Bucket newBucket(int capacity, int refillPerMinute) {
        Bandwidth limit = Bandwidth.classic(capacity, Refill.greedy(refillPerMinute, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limit).build();
    }

    /** Helper to extract the real client IP, respecting a trusted reverse proxy's X-Forwarded-For. */
    public static final Function<jakarta.servlet.http.HttpServletRequest, String> CLIENT_IP = request -> {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    };
}
