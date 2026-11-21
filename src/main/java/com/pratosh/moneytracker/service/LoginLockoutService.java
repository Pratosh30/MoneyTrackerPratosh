package com.pratosh.moneytracker.service;

import com.pratosh.moneytracker.domain.LoginAttempt;
import com.pratosh.moneytracker.repo.LoginAttemptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Escalating lockout for wrong PINs (same schedule as the original app):
 * 10 attempts then a 10 h wait, then 5 attempts / 15 h, then 4 / 24 h, then 2 / 24 h from then on.
 * Tracked per User ID on the server so clearing the browser cannot bypass it.
 */
@Service
@RequiredArgsConstructor
public class LoginLockoutService {
    /** {maxAttempts, waitHours} per stage; the last stage repeats. */
    static final int[][] STAGES = {{10, 10}, {5, 15}, {4, 24}, {2, 24}};

    private final LoginAttemptRepository repo;

    public record Status(boolean locked, long remainingSeconds, int attemptsRemaining, int waitHours) {
    }

    /** If a lockout has expired, move to the next (stricter) stage. */
    static void advance(LoginAttempt a, Instant now) {
        if (a.getLockedUntil() != null && !a.getLockedUntil().isAfter(now)) {
            a.setStage(Math.min(a.getStage() + 1, STAGES.length - 1));
            a.setFailedAttempts(0);
            a.setLockedUntil(null);
        }
    }

    static Status statusOf(LoginAttempt a, Instant now) {
        int[] stage = STAGES[Math.min(a.getStage(), STAGES.length - 1)];
        boolean locked = a.getLockedUntil() != null && a.getLockedUntil().isAfter(now);
        long remaining = locked ? Duration.between(now, a.getLockedUntil()).getSeconds() : 0;
        return new Status(locked, remaining, Math.max(0, stage[0] - a.getFailedAttempts()), stage[1]);
    }

    @Transactional
    public Status check(String userKey) {
        Instant now = Instant.now();
        LoginAttempt a = repo.findById(userKey).orElse(null);
        if (a == null) return statusOf(new LoginAttempt(userKey), now);
        advance(a, now);
        repo.save(a);
        return statusOf(a, now);
    }

    @Transactional
    public Status recordFailure(String userKey) {
        Instant now = Instant.now();
        LoginAttempt a = repo.findById(userKey).orElseGet(() -> new LoginAttempt(userKey));
        advance(a, now);
        Status s = statusOf(a, now);
        if (!s.locked()) {
            a.setFailedAttempts(a.getFailedAttempts() + 1);
            int[] stage = STAGES[Math.min(a.getStage(), STAGES.length - 1)];
            if (a.getFailedAttempts() >= stage[0]) a.setLockedUntil(now.plus(Duration.ofHours(stage[1])));
        }
        repo.save(a);
        return statusOf(a, now);
    }

    @Transactional
    public void clear(String userKey) {
        repo.deleteById(userKey);
    }

    public static String describe(long seconds) {
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        if (h > 0) return h + " h " + m + " min";
        return Math.max(1, m) + " min";
    }
}
