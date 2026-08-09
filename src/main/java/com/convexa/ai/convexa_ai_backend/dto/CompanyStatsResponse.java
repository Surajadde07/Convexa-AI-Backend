package com.convexa.ai.convexa_ai_backend.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Response body for GET /api/company/stats.
 *
 * Reuses AnalyticsResponse.DailyPoint for the call-volume series instead of
 * defining a second date/count/avgScore shape — same 3-field structure
 * already exists there, so this just imports it rather than duplicating it.
 *
 * TopPerformer and NeedsCoachingItem are genuinely new shapes (per-employee
 * aggregates), not present anywhere else in the DTO layer — DashboardStatsResponse.
 * NeedsAttentionItem is per-call, not per-employee, so it isn't reusable here.
 */
@Data
@Builder
public class CompanyStatsResponse {

    private int totalCalls;
    private double avgScore;

    private double positivePercent;
    private double negativePercent;
    private double neutralPercent;

    // Count of distinct employees whose range average score is < 65
    private int coachingNeededCount;

    // Total risk flags detected across calls in the range (from call.riskFlags JSON, low QA < 65, and escalations)
    private int riskFlagsCount;

    // AI processing pipeline success percentage (100.0 if all calls COMPLETED)
    private double aiSuccessRate;

    // Call volume series — bucketed per day, respecting the `range` query param
    private List<AnalyticsResponse.DailyPoint> callVolume;

    private List<TopPerformer> topPerformers;
    private List<NeedsCoachingItem> needsCoaching;

    private java.util.Map<String, Long> outcomeDistribution;

    // Server-computed 6 Executive Team Insights for the requested time range
    private ExecutiveTeamInsightsDTO teamInsights;

    // Server-computed data-driven Company Alerts
    private List<CompanyAlertDTO> alerts;

    @Data
    @Builder
    public static class TopPerformer {
        private Long employeeId;
        private String employeeName;
        private int callCount;
        private double avgScore;
        // Same "second half vs first half" definition as AnalyticsService's
        // trendPercent — null when the employee doesn't have enough calls
        // in the window to compute a trend.
        private Double trendPercent;
    }

    @Data
    @Builder
    public static class NeedsCoachingItem {
        private Long employeeId;
        private String employeeName;
        private double avgScore;
        private int callCount;
        // The weakest of the four real QA dimensions (Communication /
        // Problem Resolution / Professionalism / Cust. Satisfaction),
        // reused directly from DashboardService.getStats() for this
        // employee — not a fabricated category.
        private String primaryWeakness;
    }
}
