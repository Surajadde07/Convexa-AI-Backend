package com.convexa.ai.convexa_ai_backend.repository;

import com.convexa.ai.convexa_ai_backend.entity.CallRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CallRecordRepository extends JpaRepository<CallRecord, Long> {

    List<CallRecord> findByCompanyIdOrderByCreatedAtDesc(Long companyId);

    Optional<CallRecord> findByIdAndCompanyId(Long id, Long companyId);

    List<CallRecord> findByUserIdAndCompanyIdOrderByCreatedAtDesc(Long userId, Long companyId);

    List<CallRecord> findByUserId(Long userId);

    /**
     * Single-query aggregation for the Media Library KPI card.
     *
     * Returns List<Object[]> with one element (the aggregate row):
     *   [0] Long  — total recording count (COUNT(*))
     *   [1] Long  — sum of known file sizes (SUM of non-null fileSizeBytes, 0 if none)
     *   [2] Long  — number of recordings with a known file size
     *   [3] java.time.LocalDateTime — latest upload timestamp (MAX createdAt), null if no records
     *
     * Spring Data JPA always returns List<Object[]> for multi-projection queries.
     * COALESCE around SUM guarantees Long 0 instead of null when no tracked sizes exist.
     */
    @Query("""
            SELECT
              COUNT(c),
              COALESCE(SUM(CASE WHEN c.fileSizeBytes IS NOT NULL THEN c.fileSizeBytes ELSE 0L END), 0L),
              COUNT(CASE WHEN c.fileSizeBytes IS NOT NULL THEN c.id END),
              MAX(c.createdAt)
            FROM CallRecord c
            WHERE c.company.id = :companyId
            """)
    List<Object[]> getMediaLibraryStats(@Param("companyId") Long companyId);
}