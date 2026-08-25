package com.convexa.ai.convexa_ai_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Response body for GET /api/company/pipeline-intelligence.
 *
 * Phase 1 Architecture:
 * - Real, transparent revenue & pipeline data derived directly from database entities
 * - Active open stages (DISCOVERY, DEMO, PROPOSAL, NEGOTIATION) only — excludes CLOSED
 * - Deterministic Health-Weighted Pipeline
 * - Real At-Risk Pipeline ($ value + affected deals) with actionable risk triggers
 * - Configurable Revenue Target & Pipeline Coverage (Open Pipeline / Target)
 * - Period-filtered metrics (Closed Won, Win Rate) alongside Current State snapshot
 * - Revenue-connected conversation signals (Pricing Pressure, Competitive Exposure, Engagement)
 *
 * Zero fabricated or hardcoded marketing numbers.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PipelineIntelligenceResponse {

    // ── Current State: Target & Coverage ───────────────────────────────────────
    private BigDecimal revenueTarget;
    private String revenueTargetPeriod; // "QUARTERLY" or "MONTHLY"
    private Double pipelineCoverageRatio; // Open Pipeline / Target (null if no target)
    private BigDecimal gapToTarget; // Target - (Closed Won in period + Health Weighted Pipeline)

    // ── Current State: Open Pipeline & Health-Weighted ─────────────────────────
    private BigDecimal totalOpenValue;
    private long totalOpenDeals;
    private BigDecimal healthWeightedPipeline; // Deterministic calculation

    // ── Current State: At-Risk Pipeline & Deals ────────────────────────────────
    private BigDecimal atRiskPipelineValue;
    private long atRiskDealCount;
    private List<AtRiskDealItem> atRiskDeals;

    // ── Current State: Active Stage Breakdown (Excludes CLOSED) ────────────────
    private List<StageBreakdown> stageBreakdown;

    // ── Selected Period Metrics (Filtered by range parameter) ──────────────────
    private String periodRange;  // e.g. "this_quarter", "30d", "all"
    private String periodLabel;  // e.g. "This Quarter", "Last 30 Days"
    private BigDecimal periodClosedWon;
    private long periodClosedWonCount;
    private BigDecimal periodClosedLost;
    private long periodClosedLostCount;
    private Double periodWinRatePct; // Won / (Won + Lost) * 100 in period
    private long periodDealsCreated;

    // ── All-Time Deal Totals ───────────────────────────────────────────────────
    private BigDecimal totalWonValue;
    private BigDecimal totalLostValue;
    private long totalWonDeals;
    private long totalLostDeals;

    // ── Revenue-Connected Conversation Signals ($ Exposure) ────────────────────
    private BigDecimal pricingPressureValue; // $ value of open deals with active pricing friction
    private int pricingPressureDeals;        // count of open deals affected
    private BigDecimal competitiveExposureValue; // $ value of late-stage open deals mentioning competitors
    private int competitiveExposureDeals;        // count of late-stage deals affected
    private int healthyEngagementDeals;      // active deals with positive sentiment & recent calls
    private int decliningEngagementDeals;    // active deals with negative sentiment, low intent, or inactivity
    private int unlinkedOpenDeals;           // active deals with 0 recorded calls
    private BigDecimal unlinkedOpenDealValue;// total $ value of deals with 0 recorded calls
    private int totalCalls;                  // total company calls
    private int callsWithDeal;               // calls attached to deals

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StageBreakdown {
        private String stage;          // "DISCOVERY", "DEMO", "PROPOSAL", "NEGOTIATION"
        private String stageLabel;     // "Discovery", "Demo", "Proposal", "Negotiation"
        private long dealCount;
        private BigDecimal totalValue;
        private String color;          // Semantic color for UI bar
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AtRiskDealItem {
        private Long dealId;
        private String dealName;       // e.g. "Acme Corp Enterprise" or call filename or "Deal #id"
        private String accountName;    // Company/account name if known
        private BigDecimal dealValue;
        private String stage;          // "NEGOTIATION"
        private String stageLabel;     // "Negotiation"
        private String ownerName;      // Rep or call uploader name
        private String riskLevel;      // "CRITICAL", "HIGH", "MEDIUM"
        private int daysSinceLastActivity;
        private String mainRiskReason; // Primary risk trigger for executive scan
        private List<String> allRiskReasons;
        private int relatedCallCount;
        private Long latestCallId;     // For direct drilldown to call details
    }
}
