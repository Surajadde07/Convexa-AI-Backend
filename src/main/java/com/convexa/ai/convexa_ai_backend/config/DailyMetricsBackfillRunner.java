package com.convexa.ai.convexa_ai_backend.config;

import com.convexa.ai.convexa_ai_backend.service.DailyCompanyMetricsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Executes historical metrics backfill on application startup.
 */
@Component
public class DailyMetricsBackfillRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DailyMetricsBackfillRunner.class);

    @Autowired
    private DailyCompanyMetricsService dailyCompanyMetricsService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            log.info("Executing DailyMetricsBackfillRunner...");
            dailyCompanyMetricsService.backfillHistoricalMetrics();
        } catch (Exception e) {
            log.error("Error executing DailyMetricsBackfillRunner: {}", e.getMessage(), e);
        }
    }
}
