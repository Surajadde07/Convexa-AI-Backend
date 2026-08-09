package com.convexa.ai.convexa_ai_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO carrying the 6 Executive Team Insights computed from real workspace data.
 * All properties are calculated server-side across the requested date window (7d, 30d, 90d, all).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutiveTeamInsightsDTO {

    private PerformerInsight topPerformer;
    private CoachingInsight needsCoaching;
    private ImprovedInsight mostImproved;
    private VolumeInsight highestVolume;
    private BestQaInsight bestQA;
    private SentimentInsight highestSentiment;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PerformerInsight {
        private Long employeeId;
        private String employeeName;
        private Double avgScore;
        private Integer callCount;
        private String statusText;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CoachingInsight {
        private Long employeeId;
        private String employeeName;
        private Double avgScore;
        private Integer callCount;
        private String primaryWeakness;
        private String statusText;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImprovedInsight {
        private Long employeeId;
        private String employeeName;
        private Double deltaScore;
        private Double currentScore;
        private Double previousScore;
        private String deltaPercent;
        private String statusText;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VolumeInsight {
        private Long employeeId;
        private String employeeName;
        private Integer callCount;
        private String statusText;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BestQaInsight {
        private Long employeeId;
        private String employeeName;
        private Double score;
        private String callTitle;
        private String statusText;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SentimentInsight {
        private Long employeeId;
        private String employeeName;
        private Double positiveRatio;
        private Integer callCount;
        private String statusText;
    }
}
