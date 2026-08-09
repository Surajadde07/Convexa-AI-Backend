package com.convexa.ai.convexa_ai_backend.service;

import com.convexa.ai.convexa_ai_backend.dto.DailyCompanyMetricsDTO;
import com.convexa.ai.convexa_ai_backend.entity.CallRecord;
import com.convexa.ai.convexa_ai_backend.entity.Company;
import com.convexa.ai.convexa_ai_backend.entity.DailyCompanyMetrics;
import com.convexa.ai.convexa_ai_backend.repository.CallRecordRepository;
import com.convexa.ai.convexa_ai_backend.repository.CompanyRepository;
import com.convexa.ai.convexa_ai_backend.repository.DailyCompanyMetricsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DailyCompanyMetricsService {

    private static final Logger log = LoggerFactory.getLogger(DailyCompanyMetricsService.class);
    private static final DateTimeFormatter DISPLAY_FORMATTER = DateTimeFormatter.ofPattern("MMM d");

    @Autowired
    private DailyCompanyMetricsRepository dailyCompanyMetricsRepository;

    @Autowired
    private CallRecordRepository callRecordRepository;

    @Autowired
    private CompanyRepository companyRepository;

    /**
     * Incremental transactional UPSERT of today's or any target date's metrics for a company.
     */
    @Transactional
    public void updateDailyMetricsForDate(Long companyId, LocalDate date) {
        Company company = companyRepository.findById(companyId).orElse(null);
        if (company == null) {
            log.warn("Cannot update daily metrics for non-existent companyId: {}", companyId);
            return;
        }

        List<CallRecord> allCompanyCalls = callRecordRepository.findByCompanyIdOrderByCreatedAtDesc(companyId);

        // Filter calls created on target date
        List<CallRecord> dateCalls = allCompanyCalls.stream()
                .filter(c -> c.getCreatedAt() != null && c.getCreatedAt().toLocalDate().equals(date))
                .collect(Collectors.toList());

        int totalCalls = dateCalls.size();
        int positiveCalls = 0;
        int neutralCalls = 0;
        int negativeCalls = 0;
        int coachingNeeded = 0;
        double totalQaScore = 0.0;
        int scoredCalls = 0;

        for (CallRecord c : dateCalls) {
            if (c.getOverallScore() != null) {
                totalQaScore += c.getOverallScore();
                scoredCalls++;
                if (c.getOverallScore() < 70) {
                    coachingNeeded++;
                }
            }

            String s = c.getSentiment() != null ? c.getSentiment().trim().toUpperCase() : "";
            if (s.contains("POS")) {
                positiveCalls++;
            } else if (s.contains("NEG")) {
                negativeCalls++;
            } else {
                neutralCalls++;
            }
        }

        double avgQaScore = scoredCalls > 0 ? round1(totalQaScore / scoredCalls) : 0.0;
        double positivePercent = totalCalls > 0 ? round1((positiveCalls * 100.0) / totalCalls) : 0.0;
        double neutralPercent = totalCalls > 0 ? round1((neutralCalls * 100.0) / totalCalls) : 0.0;
        double negativePercent = totalCalls > 0 ? round1((negativeCalls * 100.0) / totalCalls) : 0.0;
        int orgHealth = (int) Math.min(100, Math.round((avgQaScore * 0.7) + (positivePercent * 0.3)));

        DailyCompanyMetrics metrics = dailyCompanyMetricsRepository
                .findByCompanyIdAndMetricDate(companyId, date)
                .orElseGet(() -> DailyCompanyMetrics.builder()
                        .company(company)
                        .metricDate(date)
                        .build());

        metrics.setTotalCalls(totalCalls);
        metrics.setAvgQaScore(avgQaScore);
        metrics.setTotalQaScore(round1(totalQaScore));
        metrics.setScoredCalls(scoredCalls);
        metrics.setPositiveCalls(positiveCalls);
        metrics.setNeutralCalls(neutralCalls);
        metrics.setNegativeCalls(negativeCalls);
        metrics.setPositivePercent(positivePercent);
        metrics.setNeutralPercent(neutralPercent);
        metrics.setNegativePercent(negativePercent);
        metrics.setCoachingNeededCount(coachingNeeded);
        metrics.setOrganizationHealth(orgHealth);

        dailyCompanyMetricsRepository.save(metrics);
        log.info("Upserted daily_company_metrics for companyId: {} on date: {} (totalCalls: {}, avgQa: {})",
                companyId, date, totalCalls, avgQaScore);
    }

    /**
     * Retrieve pre-aggregated daily metrics for range (7d, 30d, 90d).
     */
    @Transactional(readOnly = true)
    public List<DailyCompanyMetricsDTO> getDailyMetrics(Long companyId, String range) {
        int days = parseRangeToDays(range);
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days - 1);

        List<DailyCompanyMetrics> dbMetrics = dailyCompanyMetricsRepository
                .findByCompanyIdAndMetricDateBetweenOrderByMetricDateAsc(companyId, startDate, endDate);

        Map<LocalDate, DailyCompanyMetrics> metricsByDate = dbMetrics.stream()
                .collect(Collectors.toMap(DailyCompanyMetrics::getMetricDate, m -> m, (m1, m2) -> m1));

        List<DailyCompanyMetricsDTO> series = new ArrayList<>();

        // Generate complete continuous date series
        for (int i = 0; i < days; i++) {
            LocalDate d = startDate.plusDays(i);
            DailyCompanyMetrics m = metricsByDate.get(d);

            if (m != null) {
                series.add(DailyCompanyMetricsDTO.builder()
                        .date(d.format(DISPLAY_FORMATTER))
                        .rawDate(d)
                        .totalCalls(m.getTotalCalls() != null ? m.getTotalCalls() : 0)
                        .avgQaScore(m.getAvgQaScore() != null ? m.getAvgQaScore() : 0.0)
                        .positiveCalls(m.getPositiveCalls() != null ? m.getPositiveCalls() : 0)
                        .neutralCalls(m.getNeutralCalls() != null ? m.getNeutralCalls() : 0)
                        .negativeCalls(m.getNegativeCalls() != null ? m.getNegativeCalls() : 0)
                        .positivePercent(m.getPositivePercent() != null ? m.getPositivePercent() : 0.0)
                        .neutralPercent(m.getNeutralPercent() != null ? m.getNeutralPercent() : 0.0)
                        .negativePercent(m.getNegativePercent() != null ? m.getNegativePercent() : 0.0)
                        .coachingNeeded(m.getCoachingNeededCount() != null ? m.getCoachingNeededCount() : 0)
                        .organizationHealth(m.getOrganizationHealth() != null ? m.getOrganizationHealth() : 0)
                        .build());
            } else {
                // Continuous padding for smooth charts
                series.add(DailyCompanyMetricsDTO.builder()
                        .date(d.format(DISPLAY_FORMATTER))
                        .rawDate(d)
                        .totalCalls(0)
                        .avgQaScore(0.0)
                        .positiveCalls(0)
                        .neutralCalls(0)
                        .negativeCalls(0)
                        .positivePercent(0.0)
                        .neutralPercent(0.0)
                        .negativePercent(0.0)
                        .coachingNeeded(0)
                        .organizationHealth(0)
                        .build());
            }
        }

        return series;
    }

    /**
     * Backfill all historical dates for all companies on startup.
     */
    @Transactional
    public void backfillHistoricalMetrics() {
        log.info("Starting historical backfill for daily_company_metrics...");
        List<Company> companies = companyRepository.findAll();

        for (Company company : companies) {
            List<CallRecord> calls = callRecordRepository.findByCompanyIdOrderByCreatedAtDesc(company.getId());
            if (calls.isEmpty()) {
                // Pre-seed today for empty workspace
                updateDailyMetricsForDate(company.getId(), LocalDate.now());
                continue;
            }

            Set<LocalDate> datesToProcess = calls.stream()
                    .filter(c -> c.getCreatedAt() != null)
                    .map(c -> c.getCreatedAt().toLocalDate())
                    .collect(Collectors.toSet());

            datesToProcess.add(LocalDate.now());

            for (LocalDate date : datesToProcess) {
                updateDailyMetricsForDate(company.getId(), date);
            }
        }
        log.info("Completed historical backfill for daily_company_metrics.");
    }

    private int parseRangeToDays(String range) {
        if ("7d".equalsIgnoreCase(range)) return 7;
        if ("90d".equalsIgnoreCase(range)) return 90;
        return 30; // default 30d
    }

    private double round1(double val) {
        return Math.round(val * 10.0) / 10.0;
    }
}
