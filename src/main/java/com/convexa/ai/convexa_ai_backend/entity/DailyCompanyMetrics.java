package com.convexa.ai.convexa_ai_backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Entity mapping to daily_company_metrics table.
 * Serves as the pre-aggregated analytics warehouse per company per day.
 */
@Entity
@Table(name = "daily_company_metrics", uniqueConstraints = {
        @UniqueConstraint(name = "uk_company_metric_date", columnNames = {"company_id", "metric_date"})
}, indexes = {
        @Index(name = "idx_daily_company_metrics_lookup", columnList = "company_id, metric_date")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyCompanyMetrics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "metric_date", nullable = false)
    private LocalDate metricDate;

    @Column(name = "total_calls")
    @Builder.Default
    private Integer totalCalls = 0;

    @Column(name = "avg_qa_score")
    @Builder.Default
    private Double avgQaScore = 0.0;

    @Column(name = "total_qa_score")
    @Builder.Default
    private Double totalQaScore = 0.0;

    @Column(name = "scored_calls")
    @Builder.Default
    private Integer scoredCalls = 0;

    @Column(name = "positive_calls")
    @Builder.Default
    private Integer positiveCalls = 0;

    @Column(name = "neutral_calls")
    @Builder.Default
    private Integer neutralCalls = 0;

    @Column(name = "negative_calls")
    @Builder.Default
    private Integer negativeCalls = 0;

    @Column(name = "positive_percent")
    @Builder.Default
    private Double positivePercent = 0.0;

    @Column(name = "neutral_percent")
    @Builder.Default
    private Double neutralPercent = 0.0;

    @Column(name = "negative_percent")
    @Builder.Default
    private Double negativePercent = 0.0;

    @Column(name = "coaching_needed_count")
    @Builder.Default
    private Integer coachingNeededCount = 0;

    @Column(name = "avg_sentiment_score")
    @Builder.Default
    private Double avgSentimentScore = 0.0;

    @Column(name = "organization_health")
    @Builder.Default
    private Integer organizationHealth = 0;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
