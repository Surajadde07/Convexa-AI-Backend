package com.convexa.ai.convexa_ai_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutiveBriefingResponse {
    private String summary;
    private List<FindingItem> findings;
    private RecommendationBlock recommendation;
    private LocalDateTime generatedAt;
    private boolean isCached;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FindingItem {
        private String status; // POSITIVE, WARNING, NEUTRAL, CRITICAL
        private String title;  // Short title e.g. "QA Score Stability"
        private String detail; // One short sentence explanation
        private String metric; // Supporting metric e.g. "QA 92.3 (+2.1)"
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecommendationBlock {
        private String title; // Single clear recommendation statement
        private List<String> expectedOutcomes; // 2-3 expected business outcomes
    }
}
