package com.pratosh.moneytracker.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "app_users", uniqueConstraints = @UniqueConstraint(name = "uk_app_users_key", columnNames = "user_key"))
@Getter
@Setter
@NoArgsConstructor
public class AppUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Lower-cased User ID, used for uniqueness and sign-in. */
    @Column(name = "user_key", nullable = false, length = 24)
    private String userKey;

    /** User ID as typed at registration (display only). */
    @Column(name = "user_id", nullable = false, length = 24)
    private String userId;

    @Column(name = "display_name", length = 60)
    private String displayName;

    /** BCrypt hash. The raw PIN is never stored. */
    @Column(name = "pin_hash", nullable = false, length = 100)
    private String pinHash;

    @Column(nullable = false, length = 3)
    private String currency = "INR";

    @Column(nullable = false, length = 10)
    private String theme = "system";

    @Column(name = "demo_loaded", nullable = false)
    private boolean demoLoaded;

    @Column(nullable = false)
    private boolean onboarded;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();
}
