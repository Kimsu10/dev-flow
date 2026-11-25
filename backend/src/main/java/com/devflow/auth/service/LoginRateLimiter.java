package com.devflow.auth.service;

import java.time.Clock;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Bounded, process-local budgets. No passwords or raw network/identity values are retained. */
@Component
public class LoginRateLimiter {
    private static final int MAX_KEYS = 10_000;
    private final Clock clock;
    private final String salt = UUID.randomUUID().toString();
    private final Map<String, Bucket> addresses = new HashMap<>();
    private final Map<String, Bucket> identities = new HashMap<>();
    private record Bucket(long expiresAt, int requests) {}
    public record Decision(boolean allowed, long retryAfterSeconds) {}

    public LoginRateLimiter() { this(Clock.systemUTC()); }
    LoginRateLimiter(Clock clock) { this.clock = clock; }

    public synchronized Decision address(String address) {
        return reserve(addresses, "address:" + address, 30, 60);
    }
    public synchronized Decision identity(String login) {
        return reserve(identities, "identity:" + login.toLowerCase(Locale.ROOT), 10, 300);
    }
    private Decision reserve(Map<String, Bucket> buckets, String value, int limit, long seconds) {
        long now = clock.instant().getEpochSecond();
        String key = AccountService.hash(salt + value);
        Bucket bucket = buckets.get(key);
        if (bucket != null && now >= bucket.expiresAt()) { buckets.remove(key); bucket = null; }
        if (bucket == null) {
            if (buckets.size() >= MAX_KEYS) buckets.values().removeIf(b -> b.expiresAt() <= now);
            if (buckets.size() >= MAX_KEYS) return new Decision(false, seconds);
            bucket = new Bucket(now + seconds, 0);
        }
        long retry = Math.max(1, bucket.expiresAt() - now);
        if (bucket.requests() >= limit) return new Decision(false, retry);
        buckets.put(key, new Bucket(bucket.expiresAt(), bucket.requests() + 1));
        return new Decision(true, 0);
    }
}
