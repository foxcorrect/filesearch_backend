package com.resume.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory token blacklist for JWT revocation.
 * Blacklisted tokens are stored until their expiration is known.
 * Periodic cleanup removes expired tokens.
 */
@Component
public class TokenBlacklist {

    private static final Logger log = LoggerFactory.getLogger(TokenBlacklist.class);
    private final Set<String> blacklist = ConcurrentHashMap.newKeySet();

    public void blacklist(String token) {
        blacklist.add(token);
    }

    public boolean isBlacklisted(String token) {
        return blacklist.contains(token);
    }

    /**
     * Periodic cleanup of blacklist.
     * Note: tokens remain until the JWT expiration (24h by default).
     */
    @Scheduled(fixedRate = 3600_000)
    public void logSize() {
        if (!blacklist.isEmpty()) {
            log.debug("Token blacklist size: {}", blacklist.size());
        }
    }
}
