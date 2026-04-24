package com.resume.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Deque;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LoginRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(LoginRateLimiter.class);
    private static final int MAX_ATTEMPTS = 5;
    private static final long WINDOW_MS = 60_000;

    private final Map<String, Deque<Long>> attempts = new ConcurrentHashMap<>();

    public boolean isAllowed(String ip) {
        long now = System.currentTimeMillis();
        Deque<Long> timestamps = attempts.computeIfAbsent(ip, k -> new LinkedList<>());

        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst() < now - WINDOW_MS) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= MAX_ATTEMPTS) {
                return false;
            }
            timestamps.addLast(now);
            return true;
        }
    }

    /**
     * Periodically clean up stale entries to prevent memory leak.
     * Runs every 60 seconds.
     */
    @Scheduled(fixedRate = 60_000)
    public void cleanup() {
        long now = System.currentTimeMillis();
        int removed = 0;
        var iterator = attempts.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            Deque<Long> timestamps = entry.getValue();
            synchronized (timestamps) {
                while (!timestamps.isEmpty() && timestamps.peekFirst() < now - WINDOW_MS) {
                    timestamps.pollFirst();
                }
                if (timestamps.isEmpty()) {
                    iterator.remove();
                    removed++;
                }
            }
        }
        if (removed > 0) {
            log.debug("Cleaned up {} expired rate limit entries", removed);
        }
    }
}
