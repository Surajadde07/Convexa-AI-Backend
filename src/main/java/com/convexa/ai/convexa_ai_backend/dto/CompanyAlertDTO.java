package com.convexa.ai.convexa_ai_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing an actionable, data-driven alert for the Executive Dashboard Alert Center.
 * Every alert is computed server-side from real company call records, QA scores, sentiment,
 * risk flags, objections, seat capacity, or processing status.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyAlertDTO {

    private String id;
    private String category;    // "CRITICAL", "WARNING", "SYSTEM", "OPPORTUNITY"
    private String severity;    // "critical", "warning", "system"
    private String title;
    private String description;
    private String timeAgo;     // e.g. "25 mins ago", "2 hours ago", "Yesterday"
    private String timestamp;   // ISO-8601 string
    private String actionLabel; // e.g. "Review Call", "Manage Seats", "View Insights"
    private String link;        // e.g. "/w/{slug}/history", "/w/{slug}/company"
    private String entityType;  // "CALL", "REP", "WORKSPACE", "PIPELINE"
    private Long entityId;
}
