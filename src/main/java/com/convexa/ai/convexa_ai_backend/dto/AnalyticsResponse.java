package com.convexa.ai.convexa_ai_backend.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Response body for GET /api/analytics/employee.
 *
 * AnalyticsPage.jsx previously fetched the same /api/calls/my-calls list as
 * the dashboard and re-derived score trend, sentiment %, and daily series
 * itself. This DTO carries that same math computed once on the backend, so
 * both pages read from one source of truth instead of two parallel
 * implementations that can silently drift apart.
 */
@Data
@Builder
public class AnalyticsResponse {

    private int totalCalls;
    private double avgScore;

    private double positivePercent;
    private double negativePercent;
    private double neutralPercent;

    // % change comparing the second half of the range to the first half —
    // same "trend" definition the frontend used (seriesTrend()).
    private Double scoreTrendPercent;   // null when not enough data points
    private Double callsTrendPercent;   // null when not enough data points

    // One point per calendar day that had at least one call, oldest first.
    private List<DailyPoint> dailySeries;

    // Top keywords across the range, most frequent first.
    private List<KeywordCount> topKeywords;

    // Distribution maps — key is the raw enum-ish string already used by the
    // AI service (e.g. "Won", "Lost", "Follow Up Required" / "Demo",
    // "Discovery" / "High", "Medium", "Low", "None", "N/A"). Absent/legacy
    // records are simply not counted rather than being coerced into a bucket.
    private Map<String, Long> outcomeDistribution;
    private Map<String, Long> callTypeDistribution;
    private Map<String, Long> buyingIntentDistribution;

    @Data
    @Builder
    public static class DailyPoint {
        private String date;       // yyyy-MM-dd
        private int callCount;
        private double avgScore;   // average overallScore of calls that day
    }

    @Data
    @Builder
    public static class KeywordCount {
        private String keyword;
        private long count;
    }
}
