package com.pratosh.moneytracker.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "login_attempts")
@Getter
@Setter
@NoArgsConstructor
public class LoginAttempt {
    @Id
    @Column(name = "user_key", length = 24)
    private String userKey;

    @Column(nullable = false)
    private int stage;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    public LoginAttempt(String userKey) {
        this.userKey = userKey;
    }
}
