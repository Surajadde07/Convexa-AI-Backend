package com.convexa.ai.convexa_ai_backend.service;

import com.convexa.ai.convexa_ai_backend.dto.AnalyticsResponse;
import com.convexa.ai.convexa_ai_backend.dto.CompanyStatsResponse;
import com.convexa.ai.convexa_ai_backend.dto.CompanyStatsResponse.NeedsCoachingItem;
import com.convexa.ai.convexa_ai_backend.dto.CompanyStatsResponse.TopPerformer;
import com.convexa.ai.convexa_ai_backend.entity.CallRecord;
import com.convexa.ai.convexa_ai_backend.entity.User;
import com.convexa.ai.convexa_ai_backend.repository.UserRepository;
import com.convexa.ai.convexa_ai_backend.security.CallRangeFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Company-wide aggregation for the Sprint 2 Company Dashboard.
 *
 * Per the Sprint 2 kickoff notes, this project is intentionally single-company
 * — there is no Company/Organization entity. "Company-wide" therefore means
 * every CallRecord in the system, exactly the way CallRecordService.
 * getAllCallRecords() already defines it. No new repository method was added
 * for this — that method already existed and already does exactly what's
 * needed here.
 *
 * This is a new service (not an extension of DashboardService/AnalyticsService)
 * because those compute stats for one userId at a time; grouping by employee
 * across everyone is a different shape of computation, not a variant of the
 * same query. It reuses, rather than reimplements:
 *   - CallRecordService.getAllCallRecords()  (existing)
 *   - CallRangeFilter.apply()                (existing, same semantics as
 *                                              Dashboard/Analytics use)
 *   - AnalyticsResponse.DailyPoint           (existing nested DTO, reused
 *                                              instead of duplicated)
 */
@Service
public class CompanyService {

    @Autowired
    private CallRecordService callRecordService;

    @Autowired
    private UserRepository userRepository;

    private static final DateTimeFormatter DAY_KEY = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final double COACHING_THRESHOLD = 65.0;

    /**
     * @param volumeRange "7d" | "30d" | "all" — controls ONLY the call-volume
     *                    chart series. Top Performers and Needs Coaching are
     *                    always computed over the last 30 days, per the
     *                    Sprint 2 spec, independent of this parameter.
     */
    public CompanyStatsResponse getCompanyStats(String volumeRange) {

        List<CallRecord> allCalls = callRecordService.getAllCallRecords();
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

        List<AnalyticsResponse.DailyPoint> callVolume = buildDailySeries(CallRangeFilter.apply(allCalls, volumeRange));

        List<TopPerformer> employeeStats = buildEmployeeStats(CallRangeFilter.apply(allCalls, "30d"));

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

    /** Groups calls by employee, computing count/avgScore/trend per employee. */
    private List<TopPerformer> buildEmployeeStats(List<CallRecord> calls) {

        Map<Long, List<CallRecord>> byEmployee = calls.stream()
                .filter(c -> c.getUser() != null)
                .collect(Collectors.groupingBy(c -> c.getUser().getId()));

        if (byEmployee.isEmpty()) return List.of();

        Map<Long, String> namesById = userRepository.findAll().stream()
                .collect(Collectors.toMap(User::getId, u -> u.getName() != null && !u.getName().isBlank() ? u.getName() : u.getEmail()));

        return byEmployee.entrySet().stream()
                .map(entry -> {
                    Long employeeId = entry.getKey();
                    List<CallRecord> employeeCalls = entry.getValue();
                    double avg = employeeCalls.stream().mapToInt(c -> nz(c.getOverallScore())).average().orElse(0);

                    return TopPerformer.builder()
                            .employeeId(employeeId)
                            .employeeName(namesById.getOrDefault(employeeId, "Unknown"))
                            .callCount(employeeCalls.size())
                            .avgScore(round1(avg))
                            .trendPercent(trendForEmployee(employeeCalls))
                            .build();
                })
                .toList();
    }

    /** Same "second half vs first half average" definition AnalyticsService uses for its trend %. */
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

    /** Same daily-bucketing shape as AnalyticsService.getAnalytics(), applied company-wide. */
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

    private int nz(Integer v) { return v == null ? 0 : v; }

    private double pct(int part, int total) { return total == 0 ? 0 : (part * 100.0) / total; }

    private double round1(double v) { return Math.round(v * 10) / 10.0; }
}
