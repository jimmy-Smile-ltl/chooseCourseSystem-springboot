package com.example.choosecourse.auth;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 单实例内存限流。多实例部署时应换成 Redis/数据库计数。
 */
@Service
public class LoginAttemptService {

    private static final int MAX_ATTEMPTS = 15;
    private static final Duration WINDOW = Duration.ofMinutes(5);

    private final Map<String, Deque<Instant>> attempts = new ConcurrentHashMap<>();

    public boolean isBlocked(String key) {
        AtomicInteger count = new AtomicInteger();
        Instant cutoff = Instant.now().minus(WINDOW);
        attempts.compute(key, (ignored, deque) -> {
            if (deque == null) {
                return null;
            }
            prune(deque, cutoff);
            count.set(deque.size());
            return deque.isEmpty() ? null : deque;
        });
        return count.get() >= MAX_ATTEMPTS;
    }

    public void record(String key) {
        Instant now = Instant.now();
        Instant cutoff = now.minus(WINDOW);
        attempts.compute(key, (ignored, deque) -> {
            Deque<Instant> updated = deque == null ? new ArrayDeque<>() : deque;
            prune(updated, cutoff);
            updated.addLast(now);
            return updated;
        });
    }

    public void reset(String key) {
        attempts.remove(key);
    }

    private static void prune(Deque<Instant> deque, Instant cutoff) {
        while (!deque.isEmpty() && deque.peekFirst().isBefore(cutoff)) {
            deque.removeFirst();
        }
    }
}
