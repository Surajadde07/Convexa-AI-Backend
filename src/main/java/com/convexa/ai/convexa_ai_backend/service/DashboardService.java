package com.convexa.ai.convexa_ai_backend.service;

import com.convexa.ai.convexa_ai_backend.dto.DashboardStatsResponse;
import com.convexa.ai.convexa_ai_backend.dto.DashboardStatsResponse.NeedsAttentionItem;
import com.convexa.ai.convexa_ai_backend.entity.CallRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.convexa.ai.convexa_ai_backend.security.CallRangeFilter;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

@Service
public class DashboardService {

    @Autowired
    private CallRecordService callRecordService;

    /**
     * @param userId the authenticated user's id
     * @param range  "7d" | "30d" | "all" (defaults to "all" for null/unknown)
     */
    public DashboardStatsResponse getStats(Long userId, String range) {

        List<CallRecord> allCalls = callRecordService.getCallsByUserId(userId);
        List<CallRecord> calls = CallRangeFilter.apply(allCalls, range);

        int totalCalls = calls.size();

        if (totalCalls == 0) {
            return DashboardStatsResponse.builder()
                    .totalCalls(0)
                    .needsAttention(List.of())
                    .recommendations(List.of())
                    .briefing("No calls analysed yet in this range. Upload a recording and I'll start briefing you here.")
                    .build();
        }

        int positive = (int) calls.stream().filter(c -> "POSITIVE".equals(c.getSentiment())).count();
        int negative = (int) calls.stream().filter(c -> "NEGATIVE".equals(c.getSentiment())).count();
        int neutral  = (int) calls.stream().filter(c -> "NEUTRAL".equals(c.getSentiment())).count();

        double avgScore = calls.stream().mapToInt(c -> nz(c.getOverallScore())).average().orElse(0);
        int bestScore = calls.stream().mapToInt(c -> nz(c.getOverallScore())).max().orElse(0);

        double positivePct = pct(positive, totalCalls);
        double negativePct = pct(negative, totalCalls);
        double neutralPct  = pct(neutral, totalCalls);

        Map<String, Integer> sentimentCounts = new LinkedHashMap<>();
        sentimentCounts.put("POSITIVE", positive);
        sentimentCounts.put("NEGATIVE", negative);
        sentimentCounts.put("NEUTRAL", neutral);
        String dominantSentiment = sentimentCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);

        int avgCommunication        = avgQa(calls, CallRecord::getCommunication);
        int avgProblemResolution    = avgQa(calls, CallRecord::getProblemResolution);
        int avgProfessionalism      = avgQa(calls, CallRecord::getProfessionalism);
        int avgCustomerSatisfaction = avgQa(calls, CallRecord::getCustomerSatisfaction);

        Map<String, Integer> dims = new LinkedHashMap<>();
        dims.put("Communication", avgCommunication);
        dims.put("Problem Resolution", avgProblemResolution);
        dims.put("Professionalism", avgProfessionalism);
        dims.put("Cust. Satisfaction", avgCustomerSatisfaction);
        Map.Entry<String, Integer> weakest = dims.entrySet().stream()
                .min(Comparator.comparingInt(Map.Entry::getValue))
                .orElseThrow();

        List<NeedsAttentionItem> needsAttention = calls.stream()
                .filter(c -> "NEGATIVE".equals(c.getSentiment()) || (c.getOverallScore() != null && c.getOverallScore() < 50))
                .sorted(Comparator.comparing(CallRecord::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(6)
                .map(c -> NeedsAttentionItem.builder()
                        .id(c.getId())
                        .fileName(c.getFileName())
                        .sentiment(c.getSentiment())
                        .overallScore(c.getOverallScore())
                        .riskLevel(riskLevel(c))
                        .createdAt(c.getCreatedAt())
                        .build())
                .toList();

        List<String> recommendations = buildRecommendations(
                negativePct, negative, avgScore, weakest.getKey(), weakest.getValue(), totalCalls);

        String briefing = String.format(
                "%d call%s analysed, averaging a %.1f QA score. Sentiment is running mostly %s%s",
                totalCalls, totalCalls != 1 ? "s" : "", avgScore,
                dominantSentiment != null ? dominantSentiment.toLowerCase() : "neutral",
                needsAttention.isEmpty()
                        ? ", and nothing urgent is waiting on you."
                        : String.format(", with %d conversation%s that need%s your attention.",
                                needsAttention.size(), needsAttention.size() != 1 ? "s" : "",
                                needsAttention.size() == 1 ? "s" : "")
        );

        return DashboardStatsResponse.builder()
                .totalCalls(totalCalls)
                .avgScore(round1(avgScore))
                .bestScore(bestScore)
                .positiveCalls(positive)
                .negativeCalls(negative)
                .neutralCalls(neutral)
                .positivePercent(round1(positivePct))
                .negativePercent(round1(negativePct))
                .neutralPercent(round1(neutralPct))
                .dominantSentiment(dominantSentiment)
                .avgCommunication(avgCommunication)
                .avgProblemResolution(avgProblemResolution)
                .avgProfessionalism(avgProfessionalism)
                .avgCustomerSatisfaction(avgCustomerSatisfaction)
                .weakestDimensionLabel(weakest.getKey())
                .weakestDimensionScore(weakest.getValue())
                .needsAttention(needsAttention)
                .recommendations(recommendations)
                .briefing(briefing)
                .build();
    }

    private List<String> buildRecommendations(double negativePct, int negativeCalls, double avgScore,
                                               String weakestLabel, int weakestScore, int totalCalls) {
        List<String> out = new java.util.ArrayList<>();

        if (negativePct >= 20) {
            out.add(String.format(
                    "%d call%s landed negative — review them for coaching opportunities before they repeat.",
                    negativeCalls, negativeCalls != 1 ? "s" : ""));
        }
        if (avgScore < 65) {
            out.add(String.format(
                    "Average QA score is %.1f. Aim for 70+ by tightening up the weakest dimension below.", avgScore));
        }
        if (weakestScore < 70) {
            out.add(String.format(
                    "%s is your lowest-scoring dimension at %d/100 — focus here first.", weakestLabel, weakestScore));
        }
        if (out.isEmpty()) {
            out.add(String.format(
                    "Strong stretch — sentiment and QA scores are healthy across %d analysed call%s.",
                    totalCalls, totalCalls != 1 ? "s" : ""));
        }
        return out;
    }

    private String riskLevel(CallRecord c) {
        Integer score = c.getOverallScore();
        boolean negative = "NEGATIVE".equals(c.getSentiment());
        if ((score != null && score < 35) || (negative && score != null && score < 50)) return "high";
        if (negative || (score != null && score < 50)) return "medium";
        return "low";
    }

    private int avgQa(List<CallRecord> calls, ToIntFunction<CallRecord> selector) {
        return (int) Math.round(calls.stream().mapToInt(c -> nz(selectorOrZero(c, selector))).average().orElse(0));
    }

    private int selectorOrZero(CallRecord c, ToIntFunction<CallRecord> selector) {
        try {
            return selector.applyAsInt(c);
        } catch (NullPointerException e) {
            return 0;
        }
    }

    private int nz(Integer v) {
        return v == null ? 0 : v;
    }

    private double pct(int part, int total) {
        return total == 0 ? 0 : (part * 100.0) / total;
    }

    private double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }
}
