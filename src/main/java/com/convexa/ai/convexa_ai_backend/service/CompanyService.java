package com.convexa.ai.convexa_ai_backend.service;

import com.convexa.ai.convexa_ai_backend.dto.AnalyticsResponse;
import com.convexa.ai.convexa_ai_backend.dto.CompanyStatsResponse;
import com.convexa.ai.convexa_ai_backend.dto.CompanyStatsResponse.NeedsCoachingItem;
import com.convexa.ai.convexa_ai_backend.dto.CompanyStatsResponse.TopPerformer;
import com.convexa.ai.convexa_ai_backend.dto.DashboardStatsResponse;
import com.convexa.ai.convexa_ai_backend.dto.EmployeeProfileResponse;
import com.convexa.ai.convexa_ai_backend.dto.EmployeeProfileResponse.*;
import com.convexa.ai.convexa_ai_backend.entity.CallRecord;
import com.convexa.ai.convexa_ai_backend.entity.User;
import com.convexa.ai.convexa_ai_backend.entity.CoachingSession;
import com.convexa.ai.convexa_ai_backend.entity.LearningAssignment;
import com.convexa.ai.convexa_ai_backend.entity.ManagerNote;
import com.convexa.ai.convexa_ai_backend.entity.ImprovementPlan;
import com.convexa.ai.convexa_ai_backend.repository.UserRepository;
import com.convexa.ai.convexa_ai_backend.repository.CoachingSessionRepository;
import com.convexa.ai.convexa_ai_backend.repository.LearningAssignmentRepository;
import com.convexa.ai.convexa_ai_backend.repository.ManagerNoteRepository;
import com.convexa.ai.convexa_ai_backend.repository.ImprovementPlanRepository;
import com.convexa.ai.convexa_ai_backend.security.CallRangeFilter;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CompanyService {

    @Autowired
    private CallRecordService callRecordService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private AnalyticsService analyticsService;

    // Repositories for manager actions
    @Autowired
    private CoachingSessionRepository coachingSessionRepository;

    @Autowired
    private LearningAssignmentRepository learningAssignmentRepository;

    @Autowired
    private ManagerNoteRepository managerNoteRepository;

    @Autowired
    private ImprovementPlanRepository improvementPlanRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final DateTimeFormatter DAY_KEY = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final double COACHING_THRESHOLD = 65.0;

    public CompanyStatsResponse getCompanyStats(Long companyId, String range) {
        List<CallRecord> allCalls = callRecordService.getCallsByCompanyId(companyId);
        int totalCalls = allCalls.size();

        if (totalCalls == 0) {
            return CompanyStatsResponse.builder()
                    .totalCalls(0)
                    .callVolume(List.of())
                    .topPerformers(List.of())
                    .needsCoaching(List.of())
                    .build();
        }

        double avgScore = allCalls.stream().mapToInt(c -> nz(c.getOverallScore())).average().orElse(0);
        int positive = (int) allCalls.stream().filter(c -> "POSITIVE".equals(c.getSentiment())).count();
        int negative = (int) allCalls.stream().filter(c -> "NEGATIVE".equals(c.getSentiment())).count();
        int neutral  = (int) allCalls.stream().filter(c -> "NEUTRAL".equals(c.getSentiment())).count();

        List<AnalyticsResponse.DailyPoint> callVolume = buildDailySeries(CallRangeFilter.apply(allCalls, range));

        List<TopPerformer> employeeStats = buildEmployeeStats(companyId, CallRangeFilter.apply(allCalls, range));

        List<TopPerformer> topPerformers = employeeStats.stream()
                .sorted(Comparator.comparingDouble(TopPerformer::getAvgScore).reversed())
                .limit(10)
                .toList();

        List<NeedsCoachingItem> needsCoaching = employeeStats.stream()
                .filter(e -> e.getAvgScore() < COACHING_THRESHOLD)
                .sorted(Comparator.comparingDouble(TopPerformer::getAvgScore))
                .map(e -> NeedsCoachingItem.builder()
                        .employeeId(e.getEmployeeId())
                        .employeeName(e.getEmployeeName())
                        .avgScore(e.getAvgScore())
                        .callCount(e.getCallCount())
                        .primaryWeakness(primaryWeaknessFor(e.getEmployeeId(), range))
                        .build())
                .toList();

        return CompanyStatsResponse.builder()
                .totalCalls(totalCalls)
                .avgScore(round1(avgScore))
                .positivePercent(round1(pct(positive, totalCalls)))
                .negativePercent(round1(pct(negative, totalCalls)))
                .neutralPercent(round1(pct(neutral, totalCalls)))
                .coachingNeededCount(needsCoaching.size())
                .callVolume(callVolume)
                .topPerformers(topPerformers)
                .needsCoaching(needsCoaching)
                .build();
    }

    private List<TopPerformer> buildEmployeeStats(Long companyId, List<CallRecord> calls) {
        Map<Long, List<CallRecord>> byEmployee = calls.stream()
                .filter(c -> c.getUser() != null)
                .collect(Collectors.groupingBy(c -> c.getUser().getId()));

        if (byEmployee.isEmpty()) return List.of();

        Map<Long, String> namesById = userRepository.findByCompanyId(companyId).stream()
                .collect(Collectors.toMap(User::getId, u -> u.getName() != null && !u.getName().isBlank() ? u.getName() : u.getEmail()));

        return byEmployee.entrySet().stream()
                .filter(entry -> namesById.containsKey(entry.getKey()))
                .map(entry -> {
                    Long employeeId = entry.getKey();
                    List<CallRecord> employeeCalls = entry.getValue();
                    double avg = employeeCalls.stream().mapToInt(c -> nz(c.getOverallScore())).average().orElse(0);

                    return TopPerformer.builder()
                            .employeeId(employeeId)
                            .employeeName(namesById.get(employeeId))
                            .callCount(employeeCalls.size())
                            .avgScore(round1(avg))
                            .trendPercent(trendForEmployee(employeeCalls))
                            .build();
                })
                .toList();
    }

    private Double trendForEmployee(List<CallRecord> calls) {
        List<Integer> scoresOldestFirst = calls.stream()
                .filter(c -> c.getCreatedAt() != null)
                .sorted(Comparator.comparing(CallRecord::getCreatedAt))
                .map(c -> nz(c.getOverallScore()))
                .toList();

        if (scoresOldestFirst.size() < 2) return null;

        int mid = (int) Math.ceil(scoresOldestFirst.size() / 2.0);
        double first = scoresOldestFirst.subList(0, mid).stream().mapToInt(Integer::intValue).average().orElse(0);
        List<Integer> secondHalf = scoresOldestFirst.subList(mid, scoresOldestFirst.size());
        double second = secondHalf.isEmpty() ? 0
                : secondHalf.stream().mapToInt(Integer::intValue).average().orElse(0);

        if (first == 0) return null;
        return round1(((second - first) / first) * 100);
    }

    private List<AnalyticsResponse.DailyPoint> buildDailySeries(List<CallRecord> calls) {
        Map<String, List<CallRecord>> byDay = calls.stream()
                .filter(c -> c.getCreatedAt() != null)
                .collect(Collectors.groupingBy(
                        c -> c.getCreatedAt().format(DAY_KEY),
                        LinkedHashMap::new,
                        Collectors.toList()));

        List<String> sortedDays = new ArrayList<>(byDay.keySet());
        Collections.sort(sortedDays);

        return sortedDays.stream()
                .map(day -> {
                    List<CallRecord> dayCalls = byDay.get(day);
                    double dayAvg = dayCalls.stream().mapToInt(c -> nz(c.getOverallScore())).average().orElse(0);
                    return AnalyticsResponse.DailyPoint.builder()
                            .date(day)
                            .callCount(dayCalls.size())
                            .avgScore(round1(dayAvg))
                            .build();
                })
                .toList();
    }

    private String primaryWeaknessFor(Long employeeId, String range) {
        DashboardStatsResponse stats = dashboardService.getStats(employeeId, range);
        return stats.getWeakestDimensionLabel();
    }

    public EmployeeProfileResponse getEmployeeProfile(Long employeeId, Long companyId, String range) {
        User employee = userRepository.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        if (employee.getCompany() == null || !employee.getCompany().getId().equals(companyId)) {
            throw new RuntimeException("Unauthorized: Employee belongs to a different company.");
        }

        DashboardStatsResponse dashboard = dashboardService.getStats(employeeId, range);
        AnalyticsResponse analytics = analyticsService.getAnalytics(employeeId, range);

        List<CallRecord> allCalls = callRecordService.getCallsByUserId(employeeId);
        List<CallRecord> recentCallsSource = allCalls.stream()
                .sorted(Comparator.comparing(CallRecord::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(10)
                .toList();

        List<RecentCall> recentCalls = recentCallsSource.stream()
                .map(c -> RecentCall.builder()
                        .id(c.getId())
                        .fileName(c.getFileName())
                        .createdAt(c.getCreatedAt())
                        .overallScore(c.getOverallScore())
                        .sentiment(c.getSentiment())
                        .outcomeStatus(c.getOutcomeStatus())
                        .durationSeconds(null)
                        .build())
                .toList();

        // Load Persistent Manager Data
        List<CoachingSession> coachSessions = coachingSessionRepository.findByEmployeeIdOrderBySessionDateDescCreatedAtDesc(employeeId);
        List<LearningAssignment> learnAssigns = learningAssignmentRepository.findByEmployeeIdOrderByDeadlineAscCreatedAtDesc(employeeId);
        List<ManagerNote> managerNotesRaw = managerNoteRepository.findByEmployeeIdOrderByCreatedAtDesc(employeeId);
        List<ImprovementPlan> pipPlans = improvementPlanRepository.findByEmployeeIdOrderByCreatedAtDesc(employeeId);

        // Convert to DTOs
        List<CoachingSessionDto> coachingSessions = coachSessions.stream()
                .map(s -> CoachingSessionDto.builder()
                        .id(s.getId())
                        .sessionDate(s.getSessionDate())
                        .sessionTime(s.getSessionTime())
                        .reason(s.getReason())
                        .priority(s.getPriority())
                        .notes(s.getNotes())
                        .status(s.getStatus())
                        .createdAt(s.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        List<LearningAssignmentDto> learningAssignments = learnAssigns.stream()
                .map(a -> LearningAssignmentDto.builder()
                        .id(a.getId())
                        .moduleName(a.getModuleName())
                        .deadline(a.getDeadline())
                        .priority(a.getPriority())
                        .status(a.getStatus())
                        .assignedDate(a.getAssignedDate())
                        .createdAt(a.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        List<ManagerNoteDto> managerNotes = managerNotesRaw.stream()
                .map(n -> ManagerNoteDto.builder()
                        .id(n.getId())
                        .text(n.getText())
                        .createdAt(n.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        List<ImprovementPlanDto> improvementPlans = pipPlans.stream()
                .map(p -> ImprovementPlanDto.builder()
                        .id(p.getId())
                        .targetQA(p.getTargetQA())
                        .targetSentiment(p.getTargetSentiment())
                        .deadline(p.getDeadline())
                        .assignedModules(p.getAssignedModules())
                        .progress(p.getProgress())
                        .status(p.getStatus())
                        .createdAt(p.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        // Derive Trends & Statuses
        Double scoreTrend = analytics.getScoreTrendPercent();
        String performanceTrend = scoreTrend == null ? "Stable" : scoreTrend > 3.0 ? "Improving" : scoreTrend < -3.0 ? "Declining" : "Stable";
        String statusBadge = deriveStatusBadge(dashboard.getAvgScore(), scoreTrend);
        String riskLevel = deriveRiskLevel(dashboard.getAvgScore(), dashboard.getNegativePercent(), scoreTrend, recentCallsSource);
        String healthStatus = deriveHealthStatus(dashboard.getAvgScore(), riskLevel, pipPlans);

        LocalDate lastCoachingDate = coachSessions.stream()
                .filter(s -> "Completed".equals(s.getStatus()))
                .map(CoachingSession::getSessionDate)
                .max(Comparator.naturalOrder())
                .orElse(null);

        LocalDate nextReviewDate = pipPlans.stream()
                .filter(p -> "Active".equals(p.getStatus()))
                .map(ImprovementPlan::getDeadline)
                .findFirst()
                .orElse(LocalDate.now().plusDays(14));

        String overallRec = deriveOverallRecommendation(dashboard.getAvgScore(), performanceTrend, dashboard.getWeakestDimensionLabel());

        // Construct Upcoming Actions
        List<UpcomingActionItem> upcomingActions = new ArrayList<>();
        // Add pending coaching
        coachSessions.stream()
                .filter(s -> "Pending".equals(s.getStatus()))
                .sorted(Comparator.comparing(CoachingSession::getSessionDate))
                .limit(2)
                .forEach(s -> upcomingActions.add(UpcomingActionItem.builder()
                        .type("COACHING")
                        .title("Coaching Session: " + s.getReason())
                        .dueDate(s.getSessionDate() + " @ " + s.getSessionTime())
                        .priority(s.getPriority())
                        .build()));

        // Add pending learning
        learnAssigns.stream()
                .filter(a -> !"Completed".equals(a.getStatus()))
                .limit(2)
                .forEach(a -> upcomingActions.add(UpcomingActionItem.builder()
                        .type("LEARNING")
                        .title("Incomplete Module: " + a.getModuleName())
                        .dueDate("Due: " + a.getDeadline())
                        .priority(a.getPriority())
                        .build()));

        // Add low score reviews
        dashboard.getNeedsAttention().stream()
                .limit(2)
                .forEach(item -> upcomingActions.add(UpcomingActionItem.builder()
                        .type("REVIEW")
                        .title("Review call: " + item.getFileName())
                        .dueDate("Pending review")
                        .priority(item.getRiskLevel() != null ? item.getRiskLevel().toUpperCase() : "MEDIUM")
                        .build()));

        // Construct Team / Company Comparison Data
        List<TeamComparisonData> teamComparison = buildTeamComparison(employeeId, range, dashboard, recentCallsSource);

        // Construct Alerts
        List<String> alerts = deriveAlerts(employee, dashboard, coachSessions, learnAssigns, recentCallsSource, pipPlans);

        // Compile Progress Timeline Events (Jira-style Activity feed)
        List<ProgressTimelineEvent> progressTimeline = compileProgressTimeline(coachSessions, learnAssigns, managerNotesRaw, pipPlans, recentCallsSource);

        return EmployeeProfileResponse.builder()
                .employeeId(employee.getId())
                .name(employee.getName())
                .email(employee.getEmail())
                .role(employee.getRole() != null ? employee.getRole().name() : null)
                .joinedDate(employee.getCreatedAt())
                .dashboard(dashboard)
                .analytics(analytics)
                .recentCalls(recentCalls)
                .coachingSummary(buildCoachingSummary(recentCallsSource))
                .coachingSessions(coachingSessions)
                .learningAssignments(learningAssignments)
                .managerNotes(managerNotes)
                .improvementPlans(improvementPlans)
                .progressTimeline(progressTimeline)
                .upcomingActions(upcomingActions)
                .teamComparison(teamComparison)
                .alerts(alerts)
                .statusBadge(statusBadge)
                .healthStatus(healthStatus)
                .riskLevel(riskLevel)
                .performanceTrend(performanceTrend)
                .lastCoachingDate(lastCoachingDate)
                .nextReviewDate(nextReviewDate)
                .overallRecommendation(overallRec)
                .build();
    }

    private String deriveStatusBadge(double avgScore, Double trendPercent) {
        if (avgScore < 60) return "Critical";
        if (trendPercent != null && trendPercent < -5.0) return "Declining";
        if (avgScore < 70) return "Needs Attention";
        if (trendPercent != null && trendPercent >= 4.0) return "Improving";
        if (avgScore >= 85) return "Excellent Performer";
        return "Consistent Performer";
    }

    private String deriveRiskLevel(double avgScore, double negativePercent, Double trendPercent, List<CallRecord> recentCalls) {
        int points = 0;
        if (avgScore < 65) points += 2;
        if (negativePercent > 25.0) points += 2;
        if (trendPercent != null && trendPercent < -5.0) points += 1;
        long escalations = recentCalls.stream().filter(c -> "Escalated".equals(c.getOutcomeStatus())).count();
        if (escalations >= 2) points += 2;

        return points >= 4 ? "High" : points >= 2 ? "Medium" : "Low";
    }

    private String deriveHealthStatus(double avgScore, String riskLevel, List<ImprovementPlan> pipPlans) {
        if (avgScore < 60 || "High".equals(riskLevel)) return "Red";
        boolean hasOverdueActivePip = pipPlans.stream()
                .anyMatch(p -> "Active".equals(p.getStatus()) && p.getDeadline().isBefore(LocalDate.now()));
        if (avgScore < 70 || "Medium".equals(riskLevel) || hasOverdueActivePip) return "Yellow";
        return "Green";
    }

    private String deriveOverallRecommendation(double avgScore, String trend, String weakestLabel) {
        if (avgScore < 65) {
            return "Place on structured Performance Improvement Plan and schedule intensive 1-on-1 coaching.";
        }
        if ("Declining".equals(trend)) {
            return "Perform targeted review of recent negative calls and assign remediation learning modules.";
        }
        if (weakestLabel != null && !weakestLabel.isBlank()) {
            return "Assign skill module for " + weakestLabel + " to solidify consistent performance metrics.";
        }
        return "Performance exceeds baseline targets. Continue regular check-ins and monthly evaluations.";
    }

    private List<TeamComparisonData> buildTeamComparison(Long employeeId, String range, DashboardStatsResponse dashboard, List<CallRecord> recentCallsSource) {
        List<CallRecord> allCalls = callRecordService.getAllCallRecords();
        List<CallRecord> rangeCalls = CallRangeFilter.apply(allCalls, range);

        Map<Long, List<CallRecord>> callsByUser = rangeCalls.stream()
                .filter(c -> c.getUser() != null)
                .collect(Collectors.groupingBy(c -> c.getUser().getId()));

        double topQa = 0.0;
        double topComm = 0.0;
        double topProf = 0.0;
        double topRes = 0.0;
        double topPos = 0.0;
        double topEscRate = 0.0;

        for (List<CallRecord> userCalls : callsByUser.values()) {
            double userAvg = userCalls.stream().mapToInt(c -> nz(c.getOverallScore())).average().orElse(0.0);
            if (userAvg > topQa) topQa = userAvg;

            double userComm = userCalls.stream().mapToInt(c -> nz(c.getCommunication())).average().orElse(0.0);
            if (userComm > topComm) topComm = userComm;

            double userProf = userCalls.stream().mapToInt(c -> nz(c.getProfessionalism())).average().orElse(0.0);
            if (userProf > topProf) topProf = userProf;

            double userRes = userCalls.stream().mapToInt(c -> nz(c.getProblemResolution())).average().orElse(0.0);
            if (userRes > topRes) topRes = userRes;

            long posCount = userCalls.stream().filter(c -> "POSITIVE".equals(c.getSentiment())).count();
            double userPos = userCalls.isEmpty() ? 0.0 : (posCount * 100.0) / userCalls.size();
            if (userPos > topPos) topPos = userPos;

            long escCount = userCalls.stream().filter(c -> "Escalated".equals(c.getOutcomeStatus())).count();
            double userEsc = userCalls.isEmpty() ? 0.0 : (escCount * 100.0) / userCalls.size();
            if (userEsc > topEscRate) topEscRate = userEsc;
        }

        double compQa = rangeCalls.stream().mapToInt(c -> nz(c.getOverallScore())).average().orElse(0.0);
        double compComm = rangeCalls.stream().mapToInt(c -> nz(c.getCommunication())).average().orElse(0.0);
        double compProf = rangeCalls.stream().mapToInt(c -> nz(c.getProfessionalism())).average().orElse(0.0);
        double compRes = rangeCalls.stream().mapToInt(c -> nz(c.getProblemResolution())).average().orElse(0.0);
        long compPosCount = rangeCalls.stream().filter(c -> "POSITIVE".equals(c.getSentiment())).count();
        double compPos = rangeCalls.isEmpty() ? 0.0 : (compPosCount * 100.0) / rangeCalls.size();
        long compEscCount = rangeCalls.stream().filter(c -> "Escalated".equals(c.getOutcomeStatus())).count();
        double compEsc = rangeCalls.isEmpty() ? 0.0 : (compEscCount * 100.0) / rangeCalls.size();

        double empQa = dashboard.getAvgScore();
        double empComm = dashboard.getAvgCommunication();
        double empProf = dashboard.getAvgProfessionalism();
        double empRes = dashboard.getAvgProblemResolution();
        double empPos = dashboard.getPositivePercent();
        long empEscCount = recentCallsSource.stream().filter(c -> "Escalated".equals(c.getOutcomeStatus())).count();
        double empEsc = recentCallsSource.isEmpty() ? 0.0 : (empEscCount * 100.0) / recentCallsSource.size();

        return List.of(
            TeamComparisonData.builder().metric("QA Score").employeeValue(round1(empQa)).teamAverage(round1(compQa)).topPerformer(round1(topQa)).companyAverage(round1(compQa)).build(),
            TeamComparisonData.builder().metric("Positive Sentiment %").employeeValue(round1(empPos)).teamAverage(round1(compPos)).topPerformer(round1(topPos)).companyAverage(round1(compPos)).build(),
            TeamComparisonData.builder().metric("Professionalism").employeeValue(round1(empProf)).teamAverage(round1(compProf)).topPerformer(round1(topProf)).companyAverage(round1(compProf)).build(),
            TeamComparisonData.builder().metric("Communication").employeeValue(round1(empComm)).teamAverage(round1(compComm)).topPerformer(round1(topComm)).companyAverage(round1(compComm)).build(),
            TeamComparisonData.builder().metric("Problem Resolution").employeeValue(round1(empRes)).teamAverage(round1(compRes)).topPerformer(round1(topRes)).companyAverage(round1(compRes)).build(),
            TeamComparisonData.builder().metric("Escalation Rate %").employeeValue(round1(empEsc)).teamAverage(round1(compEsc)).topPerformer(round1(topEscRate)).companyAverage(round1(compEsc)).build()
        );
    }

    private List<String> deriveAlerts(User employee, DashboardStatsResponse dashboard, List<CoachingSession> coachSessions, List<LearningAssignment> learnAssigns, List<CallRecord> recentCalls, List<ImprovementPlan> pipPlans) {
        List<String> alerts = new ArrayList<>();

        // Alert 1: No coaching in 45 days
        LocalDate lastCoaching = coachSessions.stream()
                .filter(s -> "Completed".equals(s.getStatus()))
                .map(CoachingSession::getSessionDate)
                .max(Comparator.naturalOrder())
                .orElse(null);
        if (lastCoaching == null) {
            if (employee.getCreatedAt() != null && employee.getCreatedAt().toLocalDate().isBefore(LocalDate.now().minusDays(45))) {
                alerts.add("No coaching in 45 days");
            }
        } else if (lastCoaching.isBefore(LocalDate.now().minusDays(45))) {
            alerts.add("No coaching in 45 days");
        }

        // Alert 2: Escalations increased
        long recentEsc = recentCalls.stream().filter(c -> "Escalated".equals(c.getOutcomeStatus())).count();
        if (recentEsc >= 2) {
            alerts.add("Escalations increased (" + recentEsc + " recent calls escalated)");
        }

        // Alert 3: Negative sentiment rising
        if (dashboard.getNegativePercent() > 20.0) {
            alerts.add("Negative sentiment rising (" + round1(dashboard.getNegativePercent()) + "% negative calls)");
        }

        // Alert 4: Missed QA target
        ImprovementPlan activePlan = pipPlans.stream()
                .filter(p -> "Active".equals(p.getStatus()))
                .findFirst()
                .orElse(null);
        if (activePlan != null && dashboard.getAvgScore() < activePlan.getTargetQA()) {
            alerts.add("Missed QA target (Current QA " + round1(dashboard.getAvgScore()) + " is below PIP Target " + activePlan.getTargetQA() + ")");
        } else if (dashboard.getAvgScore() < 70) {
            alerts.add("Missed QA target (Avg QA " + round1(dashboard.getAvgScore()) + " is below standard 70)");
        }

        // Alert 5: Customer satisfaction dropping
        if (dashboard.getAvgCustomerSatisfaction() < 75) {
            alerts.add("Customer satisfaction dropping (CSAT average is " + dashboard.getAvgCustomerSatisfaction() + "%)");
        }

        return alerts;
    }

    private List<ProgressTimelineEvent> compileProgressTimeline(List<CoachingSession> coachSessions, List<LearningAssignment> learnAssigns, List<ManagerNote> notes, List<ImprovementPlan> pips, List<CallRecord> calls) {
        List<ProgressTimelineEvent> events = new ArrayList<>();

        coachSessions.forEach(s -> events.add(ProgressTimelineEvent.builder()
                .id("COACHING_" + s.getId())
                .type("COACHING")
                .title("Coaching session " + (s.getStatus() != null ? s.getStatus().toLowerCase() : "scheduled"))
                .detail(s.getReason() + " (Priority: " + s.getPriority() + "). Notes: " + (s.getNotes() != null ? s.getNotes() : ""))
                .date(s.getCreatedAt())
                .priority(s.getPriority())
                .status(s.getStatus())
                .build()));

        learnAssigns.forEach(a -> events.add(ProgressTimelineEvent.builder()
                .id("LEARNING_" + a.getId())
                .type("LEARNING")
                .title("Learning module assigned: " + a.getModuleName())
                .detail("Deadline: " + a.getDeadline() + " | Status: " + a.getStatus())
                .date(a.getCreatedAt())
                .priority(a.getPriority())
                .status(a.getStatus())
                .build()));

        notes.forEach(n -> events.add(ProgressTimelineEvent.builder()
                .id("NOTE_" + n.getId())
                .type("NOTE")
                .title("Manager note added")
                .detail(n.getText())
                .date(n.getCreatedAt())
                .build()));

        pips.forEach(p -> events.add(ProgressTimelineEvent.builder()
                .id("PIP_" + p.getId())
                .type("IMPROVEMENT")
                .title("Performance Improvement Plan created")
                .detail("Target QA: " + p.getTargetQA() + " | Deadline: " + p.getDeadline() + " | Status: " + p.getStatus())
                .date(p.getCreatedAt())
                .status(p.getStatus())
                .build()));

        // Add call improvements / QA notifications
        calls.stream()
                .filter(c -> c.getOverallScore() != null)
                .forEach(c -> {
                    String detailMsg = "Call '" + c.getFileName() + "' scored " + c.getOverallScore() + "/100.";
                    if (c.getOverallScore() >= 80) {
                        events.add(ProgressTimelineEvent.builder()
                                .id("QA_IMP_" + c.getId())
                                .type("QA")
                                .title("QA Achievement Unlocked")
                                .detail(detailMsg + " High performance score recorded.")
                                .date(c.getCreatedAt() != null ? c.getCreatedAt() : LocalDateTime.now())
                                .build());
                    }
                    if ("NEGATIVE".equals(c.getSentiment()) || c.getOverallScore() < 60) {
                        events.add(ProgressTimelineEvent.builder()
                                .id("QA_RISK_" + c.getId())
                                .type("ALERT")
                                .title("High Risk Call Alert")
                                .detail(detailMsg + " Low score or negative sentiment flagged.")
                                .date(c.getCreatedAt() != null ? c.getCreatedAt() : LocalDateTime.now())
                                .build());
                    }
                });

        events.sort((e1, e2) -> e2.getDate().compareTo(e1.getDate()));
        return events;
    }

    private AiCoachingSummary buildCoachingSummary(List<CallRecord> recentCalls) {
        String strengths = recentCalls.stream()
                .map(CallRecord::getStrengths)
                .filter(s -> s != null && !s.isBlank())
                .findFirst().orElse(null);

        String weaknesses = recentCalls.stream()
                .map(CallRecord::getImprovements)
                .filter(s -> s != null && !s.isBlank())
                .findFirst().orElse(null);

        List<String> topObjections = recentCalls.stream()
                .flatMap(c -> parseObjections(c.getObjections()).stream())
                .map(o -> (String) o.getOrDefault("objection", null))
                .filter(Objects::nonNull)
                .distinct()
                .limit(5)
                .toList();

        List<String> openActionItems = recentCalls.stream()
                .flatMap(c -> parseActionItems(c.getActionItems()).stream())
                .filter(item -> Boolean.FALSE.equals(item.get("completed")))
                .map(item -> (String) item.getOrDefault("title", null))
                .filter(Objects::nonNull)
                .distinct()
                .limit(8)
                .toList();

        List<Map<String, String>> riskFlags = recentCalls.stream()
                .flatMap(c -> parseRiskFlags(c.getRiskFlags()).stream())
                .distinct()
                .limit(6)
                .toList();

        List<String> followUpSuggestions = recentCalls.stream()
                .flatMap(c -> parseStringList(c.getFollowUpSuggestions()).stream())
                .distinct()
                .limit(6)
                .toList();

        return AiCoachingSummary.builder()
                .strengths(strengths)
                .weaknesses(weaknesses)
                .topObjections(topObjections)
                .openActionItems(openActionItems)
                .riskFlags(riskFlags)
                .followUpSuggestions(followUpSuggestions)
                .build();
    }

    private List<Map<String, Object>> parseObjections(String json) {
        return parseListOfMaps(json, new TypeReference<List<Map<String, Object>>>() {});
    }

    private List<Map<String, Object>> parseActionItems(String json) {
        return parseListOfMaps(json, new TypeReference<List<Map<String, Object>>>() {});
    }

    private List<Map<String, String>> parseRiskFlags(String json) {
        return parseListOfMaps(json, new TypeReference<List<Map<String, String>>>() {});
    }

    private List<String> parseStringList(String json) {
        return parseListOfMaps(json, new TypeReference<List<String>>() {});
    }

    private <T> List<T> parseListOfMaps(String json, TypeReference<List<T>> typeRef) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, typeRef);
        } catch (Exception e) {
            return List.of();
        }
    }

    private int nz(Integer v) { return v == null ? 0 : v; }

    private double pct(int part, int total) { return total == 0 ? 0 : (part * 100.0) / total; }

    private double round1(double v) { return Math.round(v * 10) / 10.0; }
}
