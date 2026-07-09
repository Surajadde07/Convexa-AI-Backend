package com.convexa.ai.convexa_ai_backend.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response body for GET /api/dashboard/employee.
 *
 * This centralizes KPI math that previously lived only in DashboardPage.jsx
 * (and was independently re-derived in AnalyticsPage.jsx): average scores,
 * sentiment breakdown, weakest QA dimension, "needs attention" calls, and
 * a plain-language recommendation list.
 *
 * Nothing here is fabricated — every field is either a direct aggregate of
 * CallRecord rows for the current user, or null/empty when there isn't
 * enough data yet (e.g. totalCalls == 0). The frontend should render an
 * empty state in that case rather than the API inventing placeholder values.
 */
@Data
@Builder
public class DashboardStatsResponse {

    private int totalCalls;

    private double avgScore;
    private int bestScore;

    private int positiveCalls;
    private int negativeCalls;
    private int neutralCalls;
    private double positivePercent;
    private double negativePercent;
    private double neutralPercent;
    private String dominantSentiment; // POSITIVE | NEGATIVE | NEUTRAL | null

    // Average of each of the 4 QA sub-scores across the current call set.
    private int avgCommunication;
    private int avgProblemResolution;
    private int avgProfessionalism;
    private int avgCustomerSatisfaction;

    // The lowest-scoring of the 4 dimensions above — used to drive the
    // "focus here first" coaching recommendation on the dashboard.
    private String weakestDimensionLabel;
    private int weakestDimensionScore;

    // Calls that are NEGATIVE sentiment or scored under 50 — same rule the
    // frontend used, just computed once, server-side, over the full call set
    // (not just whatever page/filter happens to be loaded client-side).
    private List<NeedsAttentionItem> needsAttention;

    // Plain-language coaching call-outs, same rules as the old client logic
    // (high negative %, low avg score, weak dimension, or "all clear").
    private List<String> recommendations;

    // One-line natural-language summary for the "briefing" card.
    private String briefing;

    @Data
    @Builder
    public static class NeedsAttentionItem {
        private Long id;
        private String fileName;
        private String sentiment;
        private Integer overallScore;
        private String riskLevel; // "high" | "medium" | "low"
        private LocalDateTime createdAt;
    }
}
