package com.convexa.ai.convexa_ai_backend.service;

import com.convexa.ai.convexa_ai_backend.dto.AnalyticsResponse;
import com.convexa.ai.convexa_ai_backend.dto.CompanyStatsResponse;
import com.convexa.ai.convexa_ai_backend.dto.CompanyStatsResponse.NeedsCoachingItem;
import com.convexa.ai.convexa_ai_backend.dto.CompanyStatsResponse.TopPerformer;
import com.convexa.ai.convexa_ai_backend.dto.DashboardStatsResponse;
import com.convexa.ai.convexa_ai_backend.dto.DailyCompanyMetricsDTO;
import com.convexa.ai.convexa_ai_backend.dto.EmployeeProfileResponse;
import com.convexa.ai.convexa_ai_backend.dto.EmployeeProfileResponse.*;
import com.convexa.ai.convexa_ai_backend.entity.CallRecord;
import com.convexa.ai.convexa_ai_backend.entity.User;
import com.convexa.ai.convexa_ai_backend.entity.MembershipStatus;
import com.convexa.ai.convexa_ai_backend.entity.OrganizationMembership;
import com.convexa.ai.convexa_ai_backend.entity.CoachingSession;
import com.convexa.ai.convexa_ai_backend.entity.LearningAssignment;
import com.convexa.ai.convexa_ai_backend.entity.ManagerNote;
import com.convexa.ai.convexa_ai_backend.entity.ImprovementPlan;
import com.convexa.ai.convexa_ai_backend.repository.UserRepository;
import com.convexa.ai.convexa_ai_backend.repository.OrganizationMembershipRepository;
import com.convexa.ai.convexa_ai_backend.repository.CoachingSessionRepository;
import com.convexa.ai.convexa_ai_backend.repository.LearningAssignmentRepository;
import com.convexa.ai.convexa_ai_backend.repository.ManagerNoteRepository;
import com.convexa.ai.convexa_ai_backend.repository.ImprovementPlanRepository;
import com.convexa.ai.convexa_ai_backend.dto.CompanyAlertDTO;
import com.convexa.ai.convexa_ai_backend.dto.ExecutiveTeamInsightsDTO;
import com.convexa.ai.convexa_ai_backend.entity.Company;
import com.convexa.ai.convexa_ai_backend.entity.Subscription;
import com.convexa.ai.convexa_ai_backend.repository.CompanyRepository;
import com.convexa.ai.convexa_ai_backend.repository.SubscriptionRepository;
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
    private OrganizationMembershipRepository organizationMembershipRepository;

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

    @Autowired
    private DailyCompanyMetricsService dailyCompanyMetricsService;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final DateTimeFormatter DAY_KEY = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final double COACHING_THRESHOLD = 65.0;

    public CompanyStatsResponse getCompanyStats(Long companyId, String range) {
        List<CallRecord> allCompanyCalls = callRecordService.getCallsByCompanyId(companyId);
        List<CallRecord> rangeCalls = CallRangeFilter.apply(allCompanyCalls, range);
        int totalCalls = rangeCalls.size();

        Map<Long, String> namesById = getActiveMemberNames(companyId);
        String companySlug = companyRepository.findById(companyId).map(Company::getCompanySlug).orElse("default");

        List<DailyCompanyMetricsDTO> preAggregatedSeries = dailyCompanyMetricsService.getDailyMetrics(companyId, range);
        List<AnalyticsResponse.DailyPoint> callVolume = preAggregatedSeries.stream()
                .map(m -> AnalyticsResponse.DailyPoint.builder()
                        .date(m.getDate())
                        .callCount(m.getTotalCalls())
                        .avgScore(m.getAvgQaScore())
                        .build())
                .collect(Collectors.toList());

        long failedCalls = rangeCalls.stream().filter(c -> "FAILED".equalsIgnoreCase(c.getStatus())).count();
        double aiSuccessRate = totalCalls == 0 ? 100.0 : round1(((totalCalls - failedCalls) * 100.0) / totalCalls);

        if (totalCalls == 0) {
            ExecutiveTeamInsightsDTO emptyInsights = ExecutiveTeamInsightsDTO.builder().build();
            List<CompanyAlertDTO> emptyAlerts = generateCompanyAlerts(companyId, rangeCalls, 0, 0.0, 0.0, 0.0, 0, 0, List.of(), List.of(), companySlug);

            return CompanyStatsResponse.builder()
                    .totalCalls(0)
                    .avgScore(0.0)
                    .positivePercent(0.0)
                    .negativePercent(0.0)
                    .neutralPercent(0.0)
                    .coachingNeededCount(0)
                    .riskFlagsCount(0)
                    .aiSuccessRate(100.0)
                    .callVolume(callVolume)
                    .topPerformers(List.of())
                    .needsCoaching(List.of())
                    .outcomeDistribution(Map.of())
                    .teamInsights(emptyInsights)
                    .alerts(emptyAlerts)
                    .build();
        }

        double avgScore = rangeCalls.stream().mapToInt(c -> nz(c.getOverallScore())).average().orElse(0);
        int positive = (int) rangeCalls.stream().filter(c -> "POSITIVE".equalsIgnoreCase(c.getSentiment())).count();
        int negative = (int) rangeCalls.stream().filter(c -> "NEGATIVE".equalsIgnoreCase(c.getSentiment())).count();
        int neutral  = (int) rangeCalls.stream().filter(c -> "NEUTRAL".equalsIgnoreCase(c.getSentiment())).count();

        double positivePercent = round1(pct(positive, totalCalls));
        double negativePercent = round1(pct(negative, totalCalls));
        double neutralPercent  = round1(pct(neutral, totalCalls));

        List<TopPerformer> employeeStats = buildEmployeeStats(companyId, rangeCalls, namesById);

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
                        .primaryWeakness(primaryWeaknessFor(companyId, e.getEmployeeId(), range))
                        .build())
                .collect(Collectors.toList());

        Map<String, Long> outcomeDist = rangeCalls.stream()
                .filter(c -> c.getOutcomeStatus() != null && !c.getOutcomeStatus().isBlank())
                .collect(Collectors.groupingBy(CallRecord::getOutcomeStatus, Collectors.counting()));

        int riskFlagsCount = calculateRiskFlagsCount(rangeCalls);
        ExecutiveTeamInsightsDTO teamInsights = computeTeamInsights(companyId, rangeCalls, allCompanyCalls, range, namesById);
        List<CompanyAlertDTO> companyAlerts = generateCompanyAlerts(
                companyId, rangeCalls, totalCalls, avgScore, positivePercent, negativePercent,
                needsCoaching.size(), riskFlagsCount, topPerformers, needsCoaching, companySlug
        );

        return CompanyStatsResponse.builder()
                .totalCalls(totalCalls)
                .avgScore(round1(avgScore))
                .positivePercent(positivePercent)
                .negativePercent(negativePercent)
                .neutralPercent(neutralPercent)
                .coachingNeededCount(needsCoaching.size())
                .riskFlagsCount(riskFlagsCount)
                .aiSuccessRate(aiSuccessRate)
                .callVolume(callVolume)
                .topPerformers(topPerformers)
                .needsCoaching(needsCoaching)
                .outcomeDistribution(outcomeDist)
                .teamInsights(teamInsights)
                .alerts(companyAlerts)
                .build();
    }

    public Map<Long, String> getActiveMemberNames(Long companyId) {
        Map<Long, String> namesById = new HashMap<>();
        try {
            organizationMembershipRepository.findByCompanyIdAndStatus(companyId, MembershipStatus.ACTIVE).stream()
                    .filter(m -> m.getUser() != null)
                    .forEach(m -> {
                        String name = m.getUser().getName() != null && !m.getUser().getName().isBlank()
                                ? m.getUser().getName()
                                : m.getUser().getEmail();
                        namesById.put(m.getUser().getId(), name);
                    });
        } catch (Exception ignored) {}

        if (namesById.isEmpty()) {
            userRepository.findByCompanyId(companyId).forEach(u -> {
                String name = u.getName() != null && !u.getName().isBlank() ? u.getName() : u.getEmail();
                namesById.put(u.getId(), name);
            });
        }
        return namesById;
    }

    public int calculateRiskFlagsCount(List<CallRecord> calls) {
        if (calls == null || calls.isEmpty()) return 0;
        int count = 0;
        for (CallRecord c : calls) {
            List<Map<String, String>> flags = parseRiskFlags(c.getRiskFlags());
            if (flags != null && !flags.isEmpty()) {
                count += flags.size();
            } else {
                if ((c.getOverallScore() != null && c.getOverallScore() < 65) || "Escalated".equalsIgnoreCase(c.getOutcomeStatus())) {
                    count++;
                }
            }
        }
        return count;
    }

    public ExecutiveTeamInsightsDTO computeTeamInsights(
            Long companyId,
            List<CallRecord> rangeCalls,
            List<CallRecord> allCompanyCalls,
            String range,
            Map<Long, String> namesById
    ) {
        if (rangeCalls == null || rangeCalls.isEmpty() || namesById == null || namesById.isEmpty()) {
            return ExecutiveTeamInsightsDTO.builder().build();
        }

        Map<Long, List<CallRecord>> byEmployee = rangeCalls.stream()
                .filter(c -> c.getUser() != null && namesById.containsKey(c.getUser().getId()))
                .collect(Collectors.groupingBy(c -> c.getUser().getId()));

        if (byEmployee.isEmpty()) {
            return ExecutiveTeamInsightsDTO.builder().build();
        }

        List<TopPerformer> statsList = byEmployee.entrySet().stream()
                .map(entry -> {
                    Long empId = entry.getKey();
                    List<CallRecord> empCalls = entry.getValue();
                    double avg = empCalls.stream().mapToInt(c -> nz(c.getOverallScore())).average().orElse(0.0);
                    return TopPerformer.builder()
                            .employeeId(empId)
                            .employeeName(namesById.get(empId))
                            .callCount(empCalls.size())
                            .avgScore(round1(avg))
                            .build();
                })
                .sorted(Comparator.comparingDouble(TopPerformer::getAvgScore).reversed())
                .toList();

        ExecutiveTeamInsightsDTO.PerformerInsight topPerformer = null;
        ExecutiveTeamInsightsDTO.CoachingInsight needsCoaching = null;

        if (!statsList.isEmpty()) {
            TopPerformer best = statsList.get(0);
            topPerformer = ExecutiveTeamInsightsDTO.PerformerInsight.builder()
                    .employeeId(best.getEmployeeId())
                    .employeeName(best.getEmployeeName())
                    .avgScore(best.getAvgScore())
                    .callCount(best.getCallCount())
                    .statusText(String.format("%.1f QA Avg · %d Calls", best.getAvgScore(), best.getCallCount()))
                    .build();

            TopPerformer lowest = statsList.get(statsList.size() - 1);
            String weakness = primaryWeaknessFor(companyId, lowest.getEmployeeId(), range);
            if (weakness == null || weakness.isBlank()) weakness = "Objection Handling";

            needsCoaching = ExecutiveTeamInsightsDTO.CoachingInsight.builder()
                    .employeeId(lowest.getEmployeeId())
                    .employeeName(lowest.getEmployeeName())
                    .avgScore(lowest.getAvgScore())
                    .callCount(lowest.getCallCount())
                    .primaryWeakness(weakness)
                    .statusText("Focus: " + weakness)
                    .build();
        }

        // Highest Volume
        ExecutiveTeamInsightsDTO.VolumeInsight highestVolume = null;
        TopPerformer maxVol = statsList.stream().max(Comparator.comparingInt(TopPerformer::getCallCount)).orElse(null);
        if (maxVol != null) {
            highestVolume = ExecutiveTeamInsightsDTO.VolumeInsight.builder()
                    .employeeId(maxVol.getEmployeeId())
                    .employeeName(maxVol.getEmployeeName())
                    .callCount(maxVol.getCallCount())
                    .statusText(maxVol.getCallCount() + " Conversations Analysed")
                    .build();
        }

        // Best QA Score (single call)
        ExecutiveTeamInsightsDTO.BestQaInsight bestQA = null;
        CallRecord bestCall = rangeCalls.stream()
                .filter(c -> c.getOverallScore() != null && c.getUser() != null && namesById.containsKey(c.getUser().getId()))
                .max(Comparator.comparingInt(CallRecord::getOverallScore))
                .orElse(null);
        if (bestCall != null) {
            bestQA = ExecutiveTeamInsightsDTO.BestQaInsight.builder()
                    .employeeId(bestCall.getUser().getId())
                    .employeeName(namesById.get(bestCall.getUser().getId()))
                    .score((double) bestCall.getOverallScore())
                    .callTitle(bestCall.getFileName() != null ? bestCall.getFileName() : "Customer Call")
                    .statusText(bestCall.getOverallScore() + " / 100 Top Score")
                    .build();
        }

        // Highest Positive Sentiment
        ExecutiveTeamInsightsDTO.SentimentInsight highestSentiment = null;
        double maxPosPct = -1.0;
        for (Map.Entry<Long, List<CallRecord>> entry : byEmployee.entrySet()) {
            List<CallRecord> empCalls = entry.getValue();
            long posCount = empCalls.stream().filter(c -> "POSITIVE".equalsIgnoreCase(c.getSentiment())).count();
            double posRatio = empCalls.isEmpty() ? 0.0 : (posCount * 100.0) / empCalls.size();
            if (posRatio > maxPosPct) {
                maxPosPct = posRatio;
                highestSentiment = ExecutiveTeamInsightsDTO.SentimentInsight.builder()
                        .employeeId(entry.getKey())
                        .employeeName(namesById.get(entry.getKey()))
                        .positiveRatio(round1(posRatio))
                        .callCount(empCalls.size())
                        .statusText(String.format("%.0f%% Positive Ratio", posRatio))
                        .build();
            }
        }

        // Most Improved (Comparison between current range and prior equivalent period)
        ExecutiveTeamInsightsDTO.ImprovedInsight mostImproved = computeMostImproved(rangeCalls, allCompanyCalls, range, namesById);

        return ExecutiveTeamInsightsDTO.builder()
                .topPerformer(topPerformer)
                .needsCoaching(needsCoaching)
                .mostImproved(mostImproved)
                .highestVolume(highestVolume)
                .bestQA(bestQA)
                .highestSentiment(highestSentiment)
                .build();
    }

    private ExecutiveTeamInsightsDTO.ImprovedInsight computeMostImproved(
            List<CallRecord> rangeCalls,
            List<CallRecord> allCompanyCalls,
            String range,
            Map<Long, String> namesById
    ) {
        if (allCompanyCalls == null || allCompanyCalls.isEmpty()) return null;

        int days = switch (range != null ? range.toLowerCase() : "30d") {
            case "7d" -> 7;
            case "30d" -> 30;
            case "90d" -> 90;
            default -> 30;
        };

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime currentStart = now.minusDays(days);
        LocalDateTime priorStart = now.minusDays(days * 2L);

        List<CallRecord> currentCalls = allCompanyCalls.stream()
                .filter(c -> c.getCreatedAt() != null && !c.getCreatedAt().isBefore(currentStart))
                .toList();

        List<CallRecord> priorCalls = allCompanyCalls.stream()
                .filter(c -> c.getCreatedAt() != null && c.getCreatedAt().isBefore(currentStart) && !c.getCreatedAt().isBefore(priorStart))
                .toList();

        Map<Long, List<CallRecord>> currentByEmp = currentCalls.stream()
                .filter(c -> c.getUser() != null && namesById.containsKey(c.getUser().getId()))
                .collect(Collectors.groupingBy(c -> c.getUser().getId()));

        Map<Long, List<CallRecord>> priorByEmp = priorCalls.stream()
                .filter(c -> c.getUser() != null && namesById.containsKey(c.getUser().getId()))
                .collect(Collectors.groupingBy(c -> c.getUser().getId()));

        Long bestEmpId = null;
        double maxDelta = -999.0;
        double bestCurScore = 0.0;
        double bestPrevScore = 0.0;

        for (Map.Entry<Long, List<CallRecord>> entry : currentByEmp.entrySet()) {
            Long empId = entry.getKey();
            List<CallRecord> curList = entry.getValue();
            List<CallRecord> prevList = priorByEmp.get(empId);

            if (curList.isEmpty()) continue;

            double curAvg = curList.stream().mapToInt(c -> nz(c.getOverallScore())).average().orElse(0.0);

            if (prevList != null && !prevList.isEmpty()) {
                double prevAvg = prevList.stream().mapToInt(c -> nz(c.getOverallScore())).average().orElse(0.0);
                double delta = curAvg - prevAvg;
                if (delta > maxDelta) {
                    maxDelta = delta;
                    bestEmpId = empId;
                    bestCurScore = curAvg;
                    bestPrevScore = prevAvg;
                }
            } else if (curList.size() >= 2) {
                // Split employee calls into 1st half vs 2nd half
                List<CallRecord> sorted = curList.stream().sorted(Comparator.comparing(CallRecord::getCreatedAt)).toList();
                int mid = sorted.size() / 2;
                double firstHalf = sorted.subList(0, mid).stream().mapToInt(c -> nz(c.getOverallScore())).average().orElse(0.0);
                double secondHalf = sorted.subList(mid, sorted.size()).stream().mapToInt(c -> nz(c.getOverallScore())).average().orElse(0.0);
                double delta = secondHalf - firstHalf;
                if (delta > maxDelta) {
                    maxDelta = delta;
                    bestEmpId = empId;
                    bestCurScore = secondHalf;
                    bestPrevScore = firstHalf;
                }
            }
        }

        if (bestEmpId != null && maxDelta > 0) {
            double pct = bestPrevScore > 0 ? ((maxDelta) / bestPrevScore) * 100.0 : maxDelta;
            return ExecutiveTeamInsightsDTO.ImprovedInsight.builder()
                    .employeeId(bestEmpId)
                    .employeeName(namesById.get(bestEmpId))
                    .deltaScore(round1(maxDelta))
                    .currentScore(round1(bestCurScore))
                    .previousScore(round1(bestPrevScore))
                    .deltaPercent(String.format("+%.1f%%", pct))
                    .statusText(String.format("+%.1f%% Score Increase", pct))
                    .build();
        }

        return null;
    }

    public List<CompanyAlertDTO> generateCompanyAlerts(
            Long companyId,
            List<CallRecord> rangeCalls,
            int totalCalls,
            double avgScore,
            double posPct,
            double negPct,
            int coachingNeededCount,
            int riskFlagsCount,
            List<TopPerformer> topPerformers,
            List<NeedsCoachingItem> needsCoaching,
            String companySlug
    ) {
        List<CompanyAlertDTO> alerts = new ArrayList<>();
        String slug = (companySlug != null && !companySlug.isBlank()) ? companySlug : "default";

        // 1. Critical QA Alert: Drop below threshold or call with QA < 60
        if (needsCoaching != null && !needsCoaching.isEmpty()) {
            NeedsCoachingItem rep = needsCoaching.get(0);
            alerts.add(CompanyAlertDTO.builder()
                    .id("ALERT_QA_DROP")
                    .category("CRITICAL")
                    .severity("critical")
                    .title("QA Score Dropped Below Threshold")
                    .description(String.format("%s averaged %.1f on recent conversations. Focus: %s.",
                            rep.getEmployeeName(), rep.getAvgScore(), rep.getPrimaryWeakness() != null ? rep.getPrimaryWeakness() : "Objection Handling"))
                    .timeAgo("Active")
                    .timestamp(LocalDateTime.now().minusMinutes(25).toString())
                    .actionLabel("Review Rep")
                    .link("/w/" + slug + "/company/employee/" + rep.getEmployeeId())
                    .entityType("REP")
                    .entityId(rep.getEmployeeId())
                    .build());
        }

        // 2. High Risk / Escalation Alert: Calls with high risk flags or Escalated status
        CallRecord escalatedOrRiskCall = rangeCalls.stream()
                .filter(c -> "Escalated".equalsIgnoreCase(c.getOutcomeStatus()) ||
                        (c.getRiskFlags() != null && c.getRiskFlags().toLowerCase().contains("high")))
                .findFirst()
                .orElse(null);
        if (escalatedOrRiskCall != null) {
            String callTitle = escalatedOrRiskCall.getFileName() != null ? escalatedOrRiskCall.getFileName() : "Customer Call";
            alerts.add(CompanyAlertDTO.builder()
                    .id("ALERT_RISK_CALL_" + escalatedOrRiskCall.getId())
                    .category("CRITICAL")
                    .severity("critical")
                    .title("High-Risk Conversation Detected")
                    .description(String.format("Critical risk flag or customer escalation detected on '%s' (Score: %d).",
                            callTitle, escalatedOrRiskCall.getOverallScore() != null ? escalatedOrRiskCall.getOverallScore() : 0))
                    .timeAgo(formatTimeAgo(escalatedOrRiskCall.getCreatedAt()))
                    .timestamp(escalatedOrRiskCall.getCreatedAt() != null ? escalatedOrRiskCall.getCreatedAt().toString() : LocalDateTime.now().toString())
                    .actionLabel("Review Call")
                    .link("/w/" + slug + "/history")
                    .entityType("CALL")
                    .entityId(escalatedOrRiskCall.getId())
                    .build());
        }

        // 3. Pricing & Objection Concentration Alert
        long objectionCalls = rangeCalls.stream()
                .filter(c -> {
                    String obj = c.getObjections() != null ? c.getObjections().toLowerCase() : "";
                    String imp = c.getImprovements() != null ? c.getImprovements().toLowerCase() : "";
                    return obj.contains("pricing") || obj.contains("budget") || obj.contains("cost") || obj.contains("expensive") ||
                            imp.contains("pricing") || imp.contains("objection");
                })
                .count();
        if (objectionCalls > 0 && totalCalls > 0) {
            double objectionPct = round1((objectionCalls * 100.0) / totalCalls);
            alerts.add(CompanyAlertDTO.builder()
                    .id("ALERT_OBJECTION_SPIKE")
                    .category("WARNING")
                    .severity("warning")
                    .title("Pricing & Budget Objection Concentration")
                    .description(String.format("Pricing and budget objections detected in %d call%s (%.0f%% of conversations this period).",
                            objectionCalls, objectionCalls > 1 ? "s" : "", objectionPct))
                    .timeAgo("Active")
                    .timestamp(LocalDateTime.now().minusHours(4).toString())
                    .actionLabel("View Insights")
                    .link("/w/" + slug + "/insights")
                    .entityType("WORKSPACE")
                    .build());
        }

        // 4. Negative Sentiment Alert
        if (negPct >= 15.0 || rangeCalls.stream().filter(c -> "NEGATIVE".equalsIgnoreCase(c.getSentiment())).count() >= 2) {
            long negCount = rangeCalls.stream().filter(c -> "NEGATIVE".equalsIgnoreCase(c.getSentiment())).count();
            alerts.add(CompanyAlertDTO.builder()
                    .id("ALERT_SENTIMENT_SPIKE")
                    .category("WARNING")
                    .severity("warning")
                    .title("Negative Customer Sentiment Alert")
                    .description(String.format("%.1f%% of calls (%d conversation%s) expressed negative customer sentiment in this window.",
                            negPct, negCount, negCount > 1 ? "s" : ""))
                    .timeAgo("Active")
                    .timestamp(LocalDateTime.now().minusHours(6).toString())
                    .actionLabel("Check Calls")
                    .link("/w/" + slug + "/history")
                    .entityType("WORKSPACE")
                    .build());
        }

        // 5. Seat Capacity Alert
        try {
            var subOpt = subscriptionRepository.findByCompanyId(companyId);
            if (subOpt.isPresent()) {
                var sub = subOpt.get();
                int limit = sub.getSeatLimit() != null ? sub.getSeatLimit() : 25;
                int current = sub.getCurrentSeatCount() != null ? sub.getCurrentSeatCount() : 1;
                int seatPct = Math.min(100, Math.round((current * 100.0f) / limit));
                if (seatPct >= 80) {
                    alerts.add(CompanyAlertDTO.builder()
                            .id("ALERT_SEAT_CAPACITY")
                            .category("WARNING")
                            .severity(seatPct >= 95 ? "critical" : "warning")
                            .title(String.format("Seat Capacity Reached %d%%", seatPct))
                            .description(String.format("%d of %d workspace seats currently assigned. Expand capacity for new members.", current, limit))
                            .timeAgo("Active")
                            .timestamp(LocalDateTime.now().toString())
                            .actionLabel("Manage Seats")
                            .link("/w/" + slug + "/company")
                            .entityType("WORKSPACE")
                            .build());
                }
            }
        } catch (Exception ignored) {}

        // 6. System & AI Pipeline Status Alert
        long failedCalls = rangeCalls.stream().filter(c -> "FAILED".equalsIgnoreCase(c.getStatus())).count();
        if (failedCalls > 0) {
            alerts.add(CompanyAlertDTO.builder()
                    .id("ALERT_PIPELINE_FAILURES")
                    .category("CRITICAL")
                    .severity("critical")
                    .title("AI Pipeline Processing Alert")
                    .description(String.format("%d call recording%s encountered transcription/processing errors.",
                            failedCalls, failedCalls > 1 ? "s" : ""))
                    .timeAgo("Recent")
                    .timestamp(LocalDateTime.now().toString())
                    .actionLabel("Check Status")
                    .link("/w/" + slug + "/history")
                    .entityType("WORKSPACE")
                    .build());
        } else if (totalCalls > 0) {
            alerts.add(CompanyAlertDTO.builder()
                    .id("ALERT_PIPELINE_HEALTHY")
                    .category("SYSTEM")
                    .severity("system")
                    .title("AI Processing Pipeline Healthy")
                    .description(String.format("100%% of %d call recording%s transcribed and scored without latency.",
                            totalCalls, totalCalls > 1 ? "s" : ""))
                    .timeAgo("Operational")
                    .timestamp(LocalDateTime.now().toString())
                    .actionLabel("System Status")
                    .link("/w/" + slug + "/company/settings")
                    .entityType("WORKSPACE")
                    .build());
        }

        return alerts;
    }

    private String formatTimeAgo(LocalDateTime dt) {
        if (dt == null) return "Recently";
        long mins = java.time.Duration.between(dt, LocalDateTime.now()).toMinutes();
        if (mins < 60) return Math.max(1, mins) + " mins ago";
        long hours = mins / 60;
        if (hours < 24) return hours + (hours == 1 ? " hour ago" : " hours ago");
        long days = hours / 24;
        return days + (days == 1 ? " day ago" : " days ago");
    }

    private List<TopPerformer> buildEmployeeStats(Long companyId, List<CallRecord> calls, Map<Long, String> namesById) {
        Map<Long, List<CallRecord>> byEmployee = calls.stream()
                .filter(c -> c.getUser() != null)
                .collect(Collectors.groupingBy(c -> c.getUser().getId()));

        if (byEmployee.isEmpty() || namesById == null || namesById.isEmpty()) return List.of();

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

    private String primaryWeaknessFor(Long companyId, Long employeeId, String range) {
        DashboardStatsResponse stats = dashboardService.getStats(companyId, employeeId, range);
        return stats.getWeakestDimensionLabel();
    }

    public EmployeeProfileResponse getEmployeeProfile(Long employeeId, Long companyId, String range) {
        User employee = userRepository.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        if (!organizationMembershipRepository.existsByUserIdAndCompanyId(employeeId, companyId)) {
            throw new RuntimeException("Unauthorized: Employee belongs to a different company.");
        }

        DashboardStatsResponse dashboard = dashboardService.getStats(companyId, employeeId, range);
        AnalyticsResponse analytics = analyticsService.getAnalytics(companyId, employeeId, range);

        List<CallRecord> allCalls = callRecordService.getCallsByUserIdAndCompanyId(employeeId, companyId);
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
        List<TeamComparisonData> teamComparison = buildTeamComparison(companyId, employeeId, range, dashboard, recentCallsSource);

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

    private List<TeamComparisonData> buildTeamComparison(Long companyId, Long employeeId, String range, DashboardStatsResponse dashboard, List<CallRecord> recentCallsSource) {
        List<CallRecord> allCalls = callRecordService.getCallsByCompanyId(companyId);
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
