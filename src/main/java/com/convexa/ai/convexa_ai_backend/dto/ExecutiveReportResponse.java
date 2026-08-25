package com.convexa.ai.convexa_ai_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Aggregated response for GET /api/company/executive-report.
 * Summarizes full company-level executive metrics, AI synthesis, revenue pipeline,
 * media storage, team insights, alerts, and ranged calls for executive reporting.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutiveReportResponse {
    private String companyName;
    private String companyLogo;
    private String range;
    private String generatedAt;
    private CompanyStatsResponse stats;
    private ExecutiveBriefingResponse briefing;
    private PipelineSummaryResponse pipeline;
    private MediaLibraryResponse mediaLibrary;
    private List<DailyCompanyMetricsDTO> dailyMetrics;
    private List<CallRecordReportItem> calls;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CallRecordReportItem {
        private Long id;
        private String fileName;
        private String uploaderName;
        private String createdAt;
        private Integer overallScore;
        private String outcomeStatus;
        private String sentiment;
        private String callType;
    }
}
