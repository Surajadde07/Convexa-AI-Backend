package com.convexa.ai.convexa_ai_backend.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Extended Response body for GET /api/company/employee/{id} (Sprint 2.6).
 *
 * Embeds all manager workspace persistence fields (coaching, learning, notes, improvement plans),
 * alongside unified progress timeline, upcoming actions, team comparison data, and health status badges.
 */
@Data
@Builder
public class EmployeeProfileResponse {

    private Long employeeId;
    private String name;
    private String email;
    private String role;
    private LocalDateTime joinedDate;

    private DashboardStatsResponse dashboard;
    private AnalyticsResponse analytics;

    private List<RecentCall> recentCalls;
    private AiCoachingSummary coachingSummary;

    // Persistent manager actions & history
    private List<CoachingSessionDto> coachingSessions;
    private List<LearningAssignmentDto> learningAssignments;
    private List<ManagerNoteDto> managerNotes;
    private List<ImprovementPlanDto> improvementPlans;
    private List<ProgressTimelineEvent> progressTimeline;
    private List<UpcomingActionItem> upcomingActions;
    private List<TeamComparisonData> teamComparison;
    private List<String> alerts;

    // Header stats
    private String statusBadge; // Excellent Performer, Consistent Performer, Needs Attention, Critical, Improving, Declining
    private String healthStatus; // Green, Yellow, Red
    private String riskLevel; // Low, Medium, High
    private String performanceTrend; // Improving, Stable, Declining
    private LocalDate lastCoachingDate;
    private LocalDate nextReviewDate;
    private String overallRecommendation;

    @Data
    @Builder
    public static class RecentCall {
        private Long id;
        private String fileName;
        private LocalDateTime createdAt;
        private Integer overallScore;
        private String sentiment;
        private String outcomeStatus;
        private Integer durationSeconds;
    }

    @Data
    @Builder
    public static class AiCoachingSummary {
        private String strengths;
        private String weaknesses;
        private List<String> topObjections;
        private List<String> openActionItems;
        private List<Map<String, String>> riskFlags;
        private List<String> followUpSuggestions;
    }

    @Data
    @Builder
    public static class CoachingSessionDto {
        private Long id;
        private LocalDate sessionDate;
        private String sessionTime;
        private String reason;
        private String priority;
        private String notes;
        private String status;
        private LocalDateTime createdAt;
    }

    @Data
    @Builder
    public static class LearningAssignmentDto {
        private Long id;
        private String moduleName;
        private LocalDate deadline;
        private String priority;
        private String status;
        private LocalDate assignedDate;
        private LocalDateTime createdAt;
    }

    @Data
    @Builder
    public static class ManagerNoteDto {
        private Long id;
        private String text;
        private LocalDateTime createdAt;
    }

    @Data
    @Builder
    public static class ImprovementPlanDto {
        private Long id;
        private Integer targetQA;
        private String targetSentiment;
        private LocalDate deadline;
        private String assignedModules;
        private Integer progress;
        private String status;
        private LocalDateTime createdAt;
    }

    @Data
    @Builder
    public static class ProgressTimelineEvent {
        private String id;
        private String type; // "COACHING" | "LEARNING" | "NOTE" | "IMPROVEMENT" | "QA" | "ALERT" | "REVIEW"
        private String title;
        private String detail;
        private LocalDateTime date;
        private String priority;
        private String status;
    }

    @Data
    @Builder
    public static class UpcomingActionItem {
        private String type; // "COACHING" | "LEARNING" | "FOLLOW_UP" | "REVIEW"
        private String title;
        private String dueDate;
        private String priority;
    }

    @Data
    @Builder
    public static class TeamComparisonData {
        private String metric;
        private Double employeeValue;
        private Double teamAverage;
        private Double topPerformer;
        private Double companyAverage;
    }
}
