package com.resume.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory token blacklist for JWT revocation with automatic expiry cleanup.
 * Each entry stores token -> expiry timestamp (epoch millisecond).
 * Expired entries are removed lazily on check and periodically via scheduled cleanup.
 */
@Component
public class TokenBlacklist {

    private static final Logger log = LoggerFactory.getLogger(TokenBlacklist.class);
    private static final long DEFAULT_TOKEN_TTL_MS = 86_400_000L; // 24 hours

    // token -> expiry timestamp (epoch millisecond)
    private final ConcurrentHashMap<String, Long> blacklist = new ConcurrentHashMap<>();

    public void blacklist(String token) {
        blacklist.put(token, System.currentTimeMillis() + DEFAULT_TOKEN_TTL_MS);
    }

    public boolean isBlacklisted(String token) {
        Long expiry = blacklist.get(token);
        if (expiry == null) {
            return false;
        }
        // Lazy removal: delete expired entry on access
        if (System.currentTimeMillis() > expiry) {
            blacklist.remove(token);
            return false;
        }
        return true;
    }

    /**
     * Periodic cleanup: remove all expired tokens. Runs every 10 minutes.
     */
    @Scheduled(fixedRate = 600_000)
    public void cleanup() {
        long now = System.currentTimeMillis();
        int removed = 0;
        var iterator = blacklist.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (now > entry.getValue()) {
                iterator.remove();
                removed++;
            }
        }
        if (removed > 0) {
            log.debug("Cleaned up {} expired token blacklist entries", removed);
        }
    }
}
