package com.convexa.ai.convexa_ai_backend.service;

import com.convexa.ai.convexa_ai_backend.dto.AnalyticsResponse;
import com.convexa.ai.convexa_ai_backend.dto.AnalyticsResponse.DailyPoint;
import com.convexa.ai.convexa_ai_backend.dto.AnalyticsResponse.KeywordCount;
import com.convexa.ai.convexa_ai_backend.entity.CallRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.convexa.ai.convexa_ai_backend.security.CallRangeFilter;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AnalyticsService {

    @Autowired
    private CallRecordService callRecordService;

    private static final DateTimeFormatter DAY_KEY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public AnalyticsResponse getAnalytics(Long userId, String range) {

        List<CallRecord> allCalls = callRecordService.getCallsByUserId(userId);
        List<CallRecord> calls = CallRangeFilter.apply(allCalls, range);

        int totalCalls = calls.size();

        if (totalCalls == 0) {
            return AnalyticsResponse.builder()
                    .totalCalls(0)
                    .dailySeries(List.of())
                    .topKeywords(List.of())
                    .outcomeDistribution(Map.of())
                    .callTypeDistribution(Map.of())
                    .buyingIntentDistribution(Map.of())
                    .build();
        }

        double avgScore = calls.stream().mapToInt(c -> nz(c.getOverallScore())).average().orElse(0);
        int positive = (int) calls.stream().filter(c -> "POSITIVE".equals(c.getSentiment())).count();
        int negative = (int) calls.stream().filter(c -> "NEGATIVE".equals(c.getSentiment())).count();
        int neutral  = (int) calls.stream().filter(c -> "NEUTRAL".equals(c.getSentiment())).count();

        // ── Daily series, oldest first ─────────────────────────────────────
        Map<String, List<CallRecord>> byDay = calls.stream()
                .filter(c -> c.getCreatedAt() != null)
                .collect(Collectors.groupingBy(
                        c -> c.getCreatedAt().format(DAY_KEY),
                        LinkedHashMap::new,
                        Collectors.toList()));

        List<String> sortedDays = new ArrayList<>(byDay.keySet());
        Collections.sort(sortedDays);

        List<DailyPoint> dailySeries = sortedDays.stream()
                .map(day -> {
                    List<CallRecord> dayCalls = byDay.get(day);
                    double dayAvg = dayCalls.stream().mapToInt(c -> nz(c.getOverallScore())).average().orElse(0);
                    return DailyPoint.builder()
                            .date(day)
                            .callCount(dayCalls.size())
                            .avgScore(round1(dayAvg))
                            .build();
                })
                .toList();

        Double scoreTrendPercent = trendPercent(dailySeries.stream().map(DailyPoint::getAvgScore).toList());
        Double callsTrendPercent = trendPercent(dailySeries.stream().map(d -> (double) d.getCallCount()).toList());

        // ── Keyword frequency ───────────────────────────────────────────────
        Map<String, Long> keywordFreq = calls.stream()
                .flatMap(c -> splitKeywords(c.getKeywords()).stream())
                .collect(Collectors.groupingBy(k -> k, Collectors.counting()));

        List<KeywordCount> topKeywords = keywordFreq.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(12)
                .map(e -> KeywordCount.builder().keyword(e.getKey()).count(e.getValue()).build())
                .toList();

        // ── Distributions (Sprint 1 fields — null-safe for legacy rows) ─────
        Map<String, Long> outcomeDist = countBy(calls, CallRecord::getOutcomeStatus);
        Map<String, Long> callTypeDist = countBy(calls, CallRecord::getCallType);
        Map<String, Long> buyingIntentDist = countBy(calls, CallRecord::getBuyingIntent);

        return AnalyticsResponse.builder()
                .totalCalls(totalCalls)
                .avgScore(round1(avgScore))
                .positivePercent(round1(pct(positive, totalCalls)))
                .negativePercent(round1(pct(negative, totalCalls)))
                .neutralPercent(round1(pct(neutral, totalCalls)))
                .scoreTrendPercent(scoreTrendPercent)
                .callsTrendPercent(callsTrendPercent)
                .dailySeries(dailySeries)
                .topKeywords(topKeywords)
                .outcomeDistribution(outcomeDist)
                .callTypeDistribution(callTypeDist)
                .buyingIntentDistribution(buyingIntentDist)
                .build();
    }

    /** Same definition as the frontend's seriesTrend(): second half vs first half average, as a %. */
    private Double trendPercent(List<Double> series) {
        if (series.size() < 2) return null;
        int mid = (int) Math.ceil(series.size() / 2.0);
        double first = series.subList(0, mid).stream().mapToDouble(Double::doubleValue).average().orElse(0);
        List<Double> secondHalf = series.subList(mid, series.size());
        double second = secondHalf.isEmpty() ? 0
                : secondHalf.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        if (first == 0) return null;
        return round1(((second - first) / first) * 100);
    }

    private List<String> splitKeywords(String keywords) {
        if (keywords == null || keywords.isBlank()) return List.of();
        return Arrays.stream(keywords.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private Map<String, Long> countBy(List<CallRecord> calls, java.util.function.Function<CallRecord, String> selector) {
        return calls.stream()
                .map(selector)
                .filter(v -> v != null && !v.isBlank())
                .collect(Collectors.groupingBy(v -> v, LinkedHashMap::new, Collectors.counting()));
    }

    private int nz(Integer v) { return v == null ? 0 : v; }

    private double pct(int part, int total) { return total == 0 ? 0 : (part * 100.0) / total; }

    private double round1(double v) { return Math.round(v * 10) / 10.0; }
}
