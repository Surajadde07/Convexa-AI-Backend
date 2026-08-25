package com.convexa.ai.convexa_ai_backend.repository;

import com.convexa.ai.convexa_ai_backend.entity.Deal;
import com.convexa.ai.convexa_ai_backend.entity.DealStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface DealRepository extends JpaRepository<Deal, Long> {

    Optional<Deal> findByIdAndCompanyId(Long id, Long companyId);

    List<Deal> findByCompanyId(Long companyId);

    @Query("SELECT COUNT(d) FROM Deal d WHERE d.company.id = :companyId AND d.dealStatus = :status")
    long countByCompanyIdAndStatus(@Param("companyId") Long companyId, @Param("status") DealStatus status);

    @Query("SELECT COALESCE(SUM(d.dealValue), 0) FROM Deal d WHERE d.company.id = :companyId AND d.dealStatus = com.convexa.ai.convexa_ai_backend.entity.DealStatus.WON")
    BigDecimal sumClosedWonByCompanyId(@Param("companyId") Long companyId);

    @Query("SELECT COALESCE(SUM(d.dealValue), 0) FROM Deal d WHERE d.company.id = :companyId AND d.dealStatus = com.convexa.ai.convexa_ai_backend.entity.DealStatus.LOST")
    BigDecimal sumLostDealValueByCompanyId(@Param("companyId") Long companyId);

    @Query("SELECT COALESCE(SUM(d.dealValue), 0) FROM Deal d WHERE d.company.id = :companyId AND d.dealStatus = com.convexa.ai.convexa_ai_backend.entity.DealStatus.OPEN AND d.id IN (SELECT DISTINCT c.deal.id FROM CallRecord c WHERE c.deal IS NOT NULL AND c.company.id = :companyId)")
    BigDecimal sumPipelineCoveredByCompanyId(@Param("companyId") Long companyId);

    @Query("SELECT COUNT(DISTINCT d) FROM Deal d WHERE d.company.id = :companyId AND d.dealStatus = :status AND d.id IN (SELECT DISTINCT c.deal.id FROM CallRecord c WHERE c.deal IS NOT NULL AND c.company.id = :companyId)")
    long countCoveredDealsByCompanyIdAndStatus(@Param("companyId") Long companyId, @Param("status") DealStatus status);

    /**
     * Returns per-stage aggregates for OPEN deals in a company.
     * Result: List of Object[] with [dealStage (DealStage enum), dealCount (Long), totalValue (BigDecimal)]
     * Used by PipelineIntelligenceResponse.stageBreakdown.
     */
    @Query("SELECT d.dealStage, COUNT(d), COALESCE(SUM(d.dealValue), 0) FROM Deal d WHERE d.company.id = :companyId AND d.dealStatus = com.convexa.ai.convexa_ai_backend.entity.DealStatus.OPEN AND d.dealStage != com.convexa.ai.convexa_ai_backend.entity.DealStage.CLOSED GROUP BY d.dealStage")
    List<Object[]> countAndSumByStageForOpenDeals(@Param("companyId") Long companyId);

    /**
     * Total value of all OPEN deals (not just call-covered) for pipeline overview.
     */
    @Query("SELECT COALESCE(SUM(d.dealValue), 0) FROM Deal d WHERE d.company.id = :companyId AND d.dealStatus = com.convexa.ai.convexa_ai_backend.entity.DealStatus.OPEN AND d.dealStage != com.convexa.ai.convexa_ai_backend.entity.DealStage.CLOSED")
    BigDecimal sumOpenDealValueByCompanyId(@Param("companyId") Long companyId);

    /**
     * Count of all OPEN deals (not just call-covered).
     */
    @Query("SELECT COUNT(d) FROM Deal d WHERE d.company.id = :companyId AND d.dealStatus = com.convexa.ai.convexa_ai_backend.entity.DealStatus.OPEN AND d.dealStage != com.convexa.ai.convexa_ai_backend.entity.DealStage.CLOSED")
    long countOpenDealsByCompanyId(@Param("companyId") Long companyId);


    /**
     * Count of all WON deals.
     */
    @Query("SELECT COUNT(d) FROM Deal d WHERE d.company.id = :companyId AND d.dealStatus = com.convexa.ai.convexa_ai_backend.entity.DealStatus.WON")
    long countWonDealsByCompanyId(@Param("companyId") Long companyId);

    /**
     * Count of all LOST deals.
     */
    @Query("SELECT COUNT(d) FROM Deal d WHERE d.company.id = :companyId AND d.dealStatus = com.convexa.ai.convexa_ai_backend.entity.DealStatus.LOST")
    long countLostDealsByCompanyId(@Param("companyId") Long companyId);
}

