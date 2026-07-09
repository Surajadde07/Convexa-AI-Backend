package com.convexa.ai.convexa_ai_backend.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * ── Schema decision ──────────────────────────────────────────────────────
 * One table, one row per user, holding every "simple app preference" the
 * Settings page manages: theme, notification toggles, default landing page,
 * export format, and the privacy toggle.
 *
 * Why one table instead of three (UserSettings / NotificationPreference /
 * AppearanceSettings) as separately-named entities:
 *   - Identical cardinality: every one of these is exactly 1 row per user,
 *     with no independent lifecycle (a NotificationPreference row is never
 *     created, queried, or deleted independently of its owning user).
 *   - Identical access pattern: the frontend always wants "give me this
 *     user's settings" as one payload (loaded once after login) and always
 *     writes one field at a time via a partial PATCH. Splitting the table
 *     would mean three round-trips (or three joins) to reconstruct one
 *     screen, for no query, indexing, or access-control benefit.
 *   - No differing growth/read pattern that would justify separating hot
 *     vs. cold columns (these are all cold, rarely-written columns).
 * If in the future notification preferences grow to be per-channel,
 * per-event, and independently queried (e.g. a notifications microservice),
 * splitting them out then would be the right call — but that's YAGNI today.
 * ──────────────────────────────────────────────────────────────────────── */
@Entity
@Table(name = "user_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // One settings row per user. unique=true enforces the 1:1 at the DB level.
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    // ── Appearance ──
    @Builder.Default
    @Column(nullable = false, length = 10)
    private String theme = "dark"; // "dark" | "light"

    // ── Notifications ──
    @Builder.Default
    @Column(nullable = false)
    private Boolean notifCallReady = true;

    @Builder.Default
    @Column(nullable = false)
    private Boolean notifNeedsAttention = true;

    @Builder.Default
    @Column(nullable = false)
    private Boolean notifWeeklyDigest = true;

    // ── Preferences ──
    @Builder.Default
    @Column(nullable = false, length = 50)
    private String defaultLandingPage = "/dashboard";

    @Builder.Default
    @Column(nullable = false, length = 10)
    private String exportFormat = "csv"; // "csv" | "json"

    // ── Privacy ──
    @Builder.Default
    @Column(nullable = false)
    private Boolean shareAnonymizedData = false;
}
