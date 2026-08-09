package com.convexa.ai.convexa_ai_backend.repository;

import com.convexa.ai.convexa_ai_backend.entity.DailyCompanyMetrics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DailyCompanyMetricsRepository extends JpaRepository<DailyCompanyMetrics, Long> {

    Optional<DailyCompanyMetrics> findByCompanyIdAndMetricDate(Long companyId, LocalDate metricDate);

    List<DailyCompanyMetrics> findByCompanyIdAndMetricDateBetweenOrderByMetricDateAsc(
            Long companyId, LocalDate startDate, LocalDate endDate
    );

    List<DailyCompanyMetrics> findByCompanyIdOrderByMetricDateAsc(Long companyId);
}
