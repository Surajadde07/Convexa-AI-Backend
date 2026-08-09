package com.convexa.ai.convexa_ai_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyCompanyMetricsDTO {
    private String date; // Formatted e.g. "Aug 7" or "2026-08-07"
    private LocalDate rawDate;
    private int totalCalls;
    private double avgQaScore;
    private int positiveCalls;
    private int neutralCalls;
    private int negativeCalls;
    private double positivePercent;
    private double neutralPercent;
    private double negativePercent;
    private int coachingNeeded;
    private double avgSentimentScore;
    private int organizationHealth;
}
