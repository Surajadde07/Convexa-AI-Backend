package com.convexa.ai.convexa_ai_backend;

import com.convexa.ai.convexa_ai_backend.dto.DealRequest;
import com.convexa.ai.convexa_ai_backend.dto.PipelineIntelligenceResponse;
import com.convexa.ai.convexa_ai_backend.dto.RevenueTargetRequest;
import com.convexa.ai.convexa_ai_backend.entity.*;
import com.convexa.ai.convexa_ai_backend.repository.CallRecordRepository;
import com.convexa.ai.convexa_ai_backend.repository.CompanyRepository;
import com.convexa.ai.convexa_ai_backend.repository.DealRepository;
import com.convexa.ai.convexa_ai_backend.repository.UserRepository;
import com.convexa.ai.convexa_ai_backend.security.WorkspacePrincipal;
import com.convexa.ai.convexa_ai_backend.service.DealService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates Problem #2 Requirements:
 * Snapshot vs Period Temporal Alignment in Gap-to-Target & Pipeline Coverage.
 */
@SpringBootTest
@Transactional
public class RevenueTargetTemporalAlignmentTest {

    @Autowired
    private DealService dealService;

    @Autowired
    private DealRepository dealRepository;

    @Autowired
    private CallRecordRepository callRecordRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private UserRepository userRepository;

    private Company companyA;
    private Company companyB;
    private User ownerA;
    private User ownerB;
    private WorkspacePrincipal principalA;
    private WorkspacePrincipal principalB;

    @BeforeEach
    void setUp() {
        companyA = Company.builder()
                .companyName("Temporal Alignment Corp A")
                .companySlug("temp-a-" + System.currentTimeMillis())
                .status(CompanyStatus.ACTIVE)
                .build();
        companyA = companyRepository.save(companyA);

        ownerA = User.builder()
                .email("ownerA@" + System.currentTimeMillis() + ".com")
                .name("Owner A")
                .password("Password123!")
                .role(Role.OWNER)
                .company(companyA)
                .build();
        ownerA = userRepository.save(ownerA);

        principalA = WorkspacePrincipal.builder()
                .userId(ownerA.getId())
                .companyId(companyA.getId())
                .role(ownerA.getRole())
                .email(ownerA.getEmail())
                .build();

        companyB = Company.builder()
                .companyName("Temporal Alignment Corp B")
                .companySlug("temp-b-" + System.currentTimeMillis())
                .status(CompanyStatus.ACTIVE)
                .build();
        companyB = companyRepository.save(companyB);

        ownerB = User.builder()
                .email("ownerB@" + System.currentTimeMillis() + ".com")
                .name("Owner B")
                .password("Password123!")
                .role(Role.OWNER)
                .company(companyB)
                .build();
        ownerB = userRepository.save(ownerB);

        principalB = WorkspacePrincipal.builder()
                .userId(ownerB.getId())
                .companyId(companyB.getId())
                .role(ownerB.getRole())
                .email(ownerB.getEmail())
                .build();
    }

    // 1. Target $100 and Closed Won $40 -> actual gap = $60, open pipeline is not added, target not achieved
    @Test
    void test1_Target100_ClosedWon40_ActualGap60_NotAchieved() {
        setTarget(principalA, new BigDecimal("100.00"), "QUARTERLY");
        createClosedWonDeal(companyA, ownerA, principalA, new BigDecimal("40.00"));
        createOpenDeal(companyA, ownerA, principalA, new BigDecimal("328.00"), DealStage.NEGOTIATION);

        PipelineIntelligenceResponse intel = dealService.getPipelineIntelligence(principalA, "this_quarter");

        assertEquals(new BigDecimal("100.00"), intel.getRevenueTarget());
        assertEquals(new BigDecimal("40.00"), intel.getPeriodClosedWon());
        assertEquals("ALIGNED", intel.getTargetAlignmentStatus());
        assertEquals(new BigDecimal("60.00"), intel.getGapToTarget());
        assertEquals(new BigDecimal("60.00"), intel.getActualRevenueGap());
        assertTrue(intel.getGapToTarget().compareTo(BigDecimal.ZERO) > 0, "Target must NOT be marked achieved");
    }

    // 2. Target $100 and Closed Won $100 -> actual gap = $0, target achieved based on actual Closed Won
    @Test
    void test2_Target100_ClosedWon100_ActualGap0_Achieved() {
        setTarget(principalA, new BigDecimal("100.00"), "QUARTERLY");
        createClosedWonDeal(companyA, ownerA, principalA, new BigDecimal("100.00"));
        createOpenDeal(companyA, ownerA, principalA, new BigDecimal("50.00"), DealStage.DEMO);

        PipelineIntelligenceResponse intel = dealService.getPipelineIntelligence(principalA, "this_quarter");

        assertEquals(new BigDecimal("100.00"), intel.getRevenueTarget());
        assertEquals(new BigDecimal("100.00"), intel.getPeriodClosedWon());
        assertEquals("ALIGNED", intel.getTargetAlignmentStatus());
        assertEquals(new BigDecimal("0.00"), intel.getGapToTarget());
        assertEquals(new BigDecimal("0.00"), intel.getActualRevenueGap());
        assertNull(intel.getPipelineCoverageRatio(), "Coverage ratio must be null when remaining gap is 0");
    }

    // 3. Target $100 and Closed Won $120 -> actual gap = $0, target achieved
    @Test
    void test3_Target100_ClosedWon120_ActualGap0_Achieved() {
        setTarget(principalA, new BigDecimal("100.00"), "QUARTERLY");
        createClosedWonDeal(companyA, ownerA, principalA, new BigDecimal("120.00"));

        PipelineIntelligenceResponse intel = dealService.getPipelineIntelligence(principalA, "this_quarter");

        assertEquals(new BigDecimal("100.00"), intel.getRevenueTarget());
        assertEquals(new BigDecimal("120.00"), intel.getPeriodClosedWon());
        assertEquals("ALIGNED", intel.getTargetAlignmentStatus());
        assertEquals(new BigDecimal("0.00"), intel.getGapToTarget());
        assertEquals(new BigDecimal("0.00"), intel.getActualRevenueGap());
        assertNull(intel.getPipelineCoverageRatio());
    }

    // 4. Open pipeline must not reduce actual revenue gap (Open $500, Target $100, Won $40 -> Gap is still $60)
    @Test
    void test4_OpenPipelineDoesNotReduceActualRevenueGap() {
        setTarget(principalA, new BigDecimal("100.00"), "QUARTERLY");
        createClosedWonDeal(companyA, ownerA, principalA, new BigDecimal("40.00"));
        createOpenDeal(companyA, ownerA, principalA, new BigDecimal("500.00"), DealStage.NEGOTIATION);

        PipelineIntelligenceResponse intel = dealService.getPipelineIntelligence(principalA, "this_quarter");

        assertEquals(new BigDecimal("500.00"), intel.getTotalOpenValue());
        assertEquals(new BigDecimal("60.00"), intel.getGapToTarget(), "Large open pipeline must NEVER reduce actual revenue gap");
        assertEquals(new BigDecimal("60.00"), intel.getActualRevenueGap());
    }

    // 5. Health-weighted pipeline must not reduce actual revenue gap
    @Test
    void test5_HealthWeightedPipelineDoesNotReduceActualRevenueGap() {
        setTarget(principalA, new BigDecimal("100.00"), "QUARTERLY");
        createClosedWonDeal(companyA, ownerA, principalA, new BigDecimal("40.00"));
        // Create 2 late-stage deals ($500 each) that produce high health-weighted pipeline
        createOpenDeal(companyA, ownerA, principalA, new BigDecimal("500.00"), DealStage.PROPOSAL);
        createOpenDeal(companyA, ownerA, principalA, new BigDecimal("500.00"), DealStage.NEGOTIATION);

        PipelineIntelligenceResponse intel = dealService.getPipelineIntelligence(principalA, "this_quarter");

        assertTrue(intel.getHealthWeightedPipeline().compareTo(new BigDecimal("500.00")) > 0);
        assertEquals(new BigDecimal("60.00"), intel.getGapToTarget(), "Health-weighted pipeline must NEVER reduce actual revenue gap");
    }

    // 6. Pipeline coverage uses current open pipeline / remaining actual revenue gap ($328 / $60 = 5.47x)
    @Test
    void test6_PipelineCoverageUsesRemainingActualRevenueGap() {
        setTarget(principalA, new BigDecimal("100.00"), "QUARTERLY");
        createClosedWonDeal(companyA, ownerA, principalA, new BigDecimal("40.00"));
        createOpenDeal(companyA, ownerA, principalA, new BigDecimal("328.00"), DealStage.DISCOVERY);

        PipelineIntelligenceResponse intel = dealService.getPipelineIntelligence(principalA, "this_quarter");

        // Remaining Gap = $100 - $40 = $60.00
        // Coverage = 328.00 / 60.00 = 5.47x
        assertEquals(new BigDecimal("60.00"), intel.getGapToTarget());
        assertEquals(5.47, intel.getPipelineCoverageRatio());
    }

    // 7. No target -> coverage null/unavailable, gap null/unavailable
    @Test
    void test7_NoTarget_CoverageAndGapNull() {
        createClosedWonDeal(companyA, ownerA, principalA, new BigDecimal("40.00"));
        createOpenDeal(companyA, ownerA, principalA, new BigDecimal("100.00"), DealStage.DEMO);

        PipelineIntelligenceResponse intel = dealService.getPipelineIntelligence(principalA, "this_quarter");

        assertNull(intel.getRevenueTarget());
        assertEquals("NO_TARGET", intel.getTargetAlignmentStatus());
        assertNull(intel.getGapToTarget());
        assertNull(intel.getActualRevenueGap());
        assertNull(intel.getPipelineCoverageRatio());
    }

    // 8. Zero or negative target -> coverage null/unavailable, gap null/unavailable
    @Test
    void test8_ZeroTarget_CoverageAndGapNull() {
        companyA.setQuarterlyRevenueTarget(BigDecimal.ZERO);
        companyA.setRevenueTargetPeriod("QUARTERLY");
        companyRepository.save(companyA);

        createClosedWonDeal(companyA, ownerA, principalA, new BigDecimal("40.00"));
        PipelineIntelligenceResponse intel = dealService.getPipelineIntelligence(principalA, "this_quarter");

        assertNull(intel.getRevenueTarget());
        assertEquals("NO_TARGET", intel.getTargetAlignmentStatus());
        assertNull(intel.getGapToTarget());
        assertNull(intel.getActualRevenueGap());
        assertNull(intel.getPipelineCoverageRatio());
    }

    // 9. Remaining gap zero -> no division by zero, no Infinity or NaN
    @Test
    void test9_RemainingGapZero_NoDivisionByZero() {
        setTarget(principalA, new BigDecimal("50.00"), "QUARTERLY");
        createClosedWonDeal(companyA, ownerA, principalA, new BigDecimal("50.00"));
        createOpenDeal(companyA, ownerA, principalA, new BigDecimal("200.00"), DealStage.DEMO);

        PipelineIntelligenceResponse intel = dealService.getPipelineIntelligence(principalA, "this_quarter");

        assertEquals(new BigDecimal("0.00"), intel.getGapToTarget());
        assertNull(intel.getPipelineCoverageRatio(), "Must return null when remaining gap is zero, no division by zero");
    }

    // 10. Monthly target with monthly range -> aligned behavior
    @Test
    void test10_MonthlyTarget_MonthlyRange_Aligned() {
        setTarget(principalA, new BigDecimal("50000.00"), "MONTHLY");
        createClosedWonDeal(companyA, ownerA, principalA, new BigDecimal("20000.00"));
        createOpenDeal(companyA, ownerA, principalA, new BigDecimal("60000.00"), DealStage.PROPOSAL);

        PipelineIntelligenceResponse intel = dealService.getPipelineIntelligence(principalA, "this_month");

        assertEquals("ALIGNED", intel.getTargetAlignmentStatus());
        assertEquals(new BigDecimal("50000.00"), intel.getRevenueTarget());
        assertEquals("MONTHLY", intel.getRevenueTargetPeriod());
        assertEquals(new BigDecimal("30000.00"), intel.getGapToTarget());
        // Coverage = 60000 / 30000 = 2.0x
        assertEquals(2.0, intel.getPipelineCoverageRatio());
    }

    // 11. Quarterly target with quarterly range -> aligned behavior
    @Test
    void test11_QuarterlyTarget_QuarterlyRange_Aligned() {
        setTarget(principalA, new BigDecimal("150000.00"), "QUARTERLY");
        createClosedWonDeal(companyA, ownerA, principalA, new BigDecimal("50000.00"));
        createOpenDeal(companyA, ownerA, principalA, new BigDecimal("200000.00"), DealStage.NEGOTIATION);

        PipelineIntelligenceResponse intel = dealService.getPipelineIntelligence(principalA, "this_quarter");

        assertEquals("ALIGNED", intel.getTargetAlignmentStatus());
        assertEquals(new BigDecimal("150000.00"), intel.getRevenueTarget());
        assertEquals("QUARTERLY", intel.getRevenueTargetPeriod());
        assertEquals(new BigDecimal("100000.00"), intel.getGapToTarget());
        // Coverage = 200000 / 100000 = 2.0x
        assertEquals(2.0, intel.getPipelineCoverageRatio());
    }

    // 12. Mismatched target period -> honest unavailable/mismatch behavior
    @Test
    void test12_MismatchedTargetPeriod_HonestMismatch() {
        // Set QUARTERLY target, but request "this_month"
        setTarget(principalA, new BigDecimal("150000.00"), "QUARTERLY");
        createClosedWonDeal(companyA, ownerA, principalA, new BigDecimal("20000.00"));
        createOpenDeal(companyA, ownerA, principalA, new BigDecimal("100000.00"), DealStage.DEMO);

        PipelineIntelligenceResponse intelQuarterlyOnMonth = dealService.getPipelineIntelligence(principalA, "this_month");
        assertEquals("PERIOD_MISMATCH", intelQuarterlyOnMonth.getTargetAlignmentStatus());
        assertNull(intelQuarterlyOnMonth.getGapToTarget());
        assertNull(intelQuarterlyOnMonth.getActualRevenueGap());
        assertNull(intelQuarterlyOnMonth.getPipelineCoverageRatio());

        // Set MONTHLY target, but request "this_quarter"
        setTarget(principalA, new BigDecimal("40000.00"), "MONTHLY");
        PipelineIntelligenceResponse intelMonthlyOnQuarter = dealService.getPipelineIntelligence(principalA, "this_quarter");
        assertEquals("PERIOD_MISMATCH", intelMonthlyOnQuarter.getTargetAlignmentStatus());
        assertNull(intelMonthlyOnQuarter.getGapToTarget());
        assertNull(intelMonthlyOnQuarter.getActualRevenueGap());
        assertNull(intelMonthlyOnQuarter.getPipelineCoverageRatio());
    }

    // 13. Last Quarter / Last 30 Days / 7d -> do not invent historical target values
    @Test
    void test13_HistoricalRanges_TargetsNotInvented() {
        setTarget(principalA, new BigDecimal("100000.00"), "QUARTERLY");
        createClosedWonDeal(companyA, ownerA, principalA, new BigDecimal("30000.00"));

        PipelineIntelligenceResponse intelLastQ = dealService.getPipelineIntelligence(principalA, "last_quarter");
        assertEquals("HISTORICAL_UNAVAILABLE", intelLastQ.getTargetAlignmentStatus());
        assertNull(intelLastQ.getGapToTarget());
        assertNull(intelLastQ.getPipelineCoverageRatio());

        PipelineIntelligenceResponse intel30d = dealService.getPipelineIntelligence(principalA, "30d");
        assertEquals("HISTORICAL_UNAVAILABLE", intel30d.getTargetAlignmentStatus());
        assertNull(intel30d.getGapToTarget());
        assertNull(intel30d.getPipelineCoverageRatio());
    }

    // 14. All Time -> do not compare all-time revenue against a period-specific target misleadingly
    @Test
    void test14_AllTime_TargetNotApplicable() {
        setTarget(principalA, new BigDecimal("100000.00"), "QUARTERLY");
        createClosedWonDeal(companyA, ownerA, principalA, new BigDecimal("500000.00"));

        PipelineIntelligenceResponse intelAll = dealService.getPipelineIntelligence(principalA, "all");
        assertEquals("NOT_APPLICABLE_ALL_TIME", intelAll.getTargetAlignmentStatus());
        assertNull(intelAll.getGapToTarget());
        assertNull(intelAll.getActualRevenueGap());
        assertNull(intelAll.getPipelineCoverageRatio());
    }

    // 15. Problem #1 behavior remains intact: stalled deals remain STALLED, $0 financial exposure
    @Test
    void test15_Problem1BehaviorRemainsIntact() {
        setTarget(principalA, new BigDecimal("100000.00"), "QUARTERLY");
        // Stalled deal (Discovery inactive 35 days)
        CallRecord staleCall = CallRecord.builder()
                .fileName("stale.wav")
                .transcript("Transcript")
                .company(companyA)
                .user(ownerA)
                .createdAt(LocalDateTime.now().minusDays(35))
                .build();
        staleCall = callRecordRepository.save(staleCall);
        createDealForCall(staleCall, new BigDecimal("25000.00"), DealStatus.OPEN, DealStage.DISCOVERY, "Stalled Deal", principalA);

        PipelineIntelligenceResponse intel = dealService.getPipelineIntelligence(principalA, "this_quarter");

        assertEquals(1, intel.getAtRiskDeals().size());
        assertEquals("STALLED", intel.getAtRiskDeals().get(0).getRiskLevel());
        assertEquals(0, intel.getAtRiskDealCount());
        assertEquals(BigDecimal.ZERO, intel.getAtRiskPipelineValue());
    }

    // 16. Tenant/company scoping remains intact
    @Test
    void test16_TenantScopingRemainsIntact() {
        setTarget(principalA, new BigDecimal("100000.00"), "QUARTERLY");
        createClosedWonDeal(companyA, ownerA, principalA, new BigDecimal("40000.00"));

        // Company B has no target and no won deals
        PipelineIntelligenceResponse intelB = dealService.getPipelineIntelligence(principalB, "this_quarter");
        assertNull(intelB.getRevenueTarget());
        assertEquals("NO_TARGET", intelB.getTargetAlignmentStatus());
        assertEquals(BigDecimal.ZERO, intelB.getPeriodClosedWon());
        assertNull(intelB.getGapToTarget());
        assertNull(intelB.getPipelineCoverageRatio());
    }

    // 17. Target amount 400 is accepted and persisted correctly
    @Test
    void test17_TargetAmount_400Accepted() {
        setTarget(principalA, new BigDecimal("400.00"), "QUARTERLY");
        PipelineIntelligenceResponse intel = dealService.getPipelineIntelligence(principalA, "this_quarter");
        assertEquals(new BigDecimal("400.00"), intel.getRevenueTarget());
    }

    // 18. Positive decimal target amounts (e.g. 400.50) are accepted
    @Test
    void test18_TargetAmount_PositiveDecimalAccepted() {
        setTarget(principalA, new BigDecimal("400.50"), "QUARTERLY");
        PipelineIntelligenceResponse intel = dealService.getPipelineIntelligence(principalA, "this_quarter");
        assertEquals(new BigDecimal("400.50"), intel.getRevenueTarget());
    }

    // 19. Target amounts below 1000 (999) and above 1000 (1500) are accepted
    @Test
    void test19_TargetAmount_999And1500Accepted() {
        setTarget(principalA, new BigDecimal("999.00"), "QUARTERLY");
        PipelineIntelligenceResponse intel1 = dealService.getPipelineIntelligence(principalA, "this_quarter");
        assertEquals(new BigDecimal("999.00"), intel1.getRevenueTarget());

        setTarget(principalA, new BigDecimal("1500.00"), "QUARTERLY");
        PipelineIntelligenceResponse intel2 = dealService.getPipelineIntelligence(principalA, "this_quarter");
        assertEquals(new BigDecimal("1500.00"), intel2.getRevenueTarget());
    }

    // 20. Target amount of zero is rejected with IllegalArgumentException
    @Test
    void test20_TargetAmount_ZeroRejected() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            setTarget(principalA, BigDecimal.ZERO, "QUARTERLY");
        });
        assertTrue(ex.getMessage().contains("greater than zero"));
    }

    // 21. Negative target amount is rejected with IllegalArgumentException
    @Test
    void test21_TargetAmount_NegativeRejected() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            setTarget(principalA, new BigDecimal("-50.00"), "QUARTERLY");
        });
        assertTrue(ex.getMessage().contains("greater than zero"));
    }

    private void setTarget(WorkspacePrincipal principal, BigDecimal target, String period) {
        RevenueTargetRequest req = RevenueTargetRequest.builder()
                .target(target)
                .period(period)
                .build();
        dealService.updateRevenueTarget(req, principal);
    }

    private Deal createClosedWonDeal(Company company, User user, WorkspacePrincipal principal, BigDecimal value) {
        CallRecord call = CallRecord.builder()
                .fileName("won_" + System.currentTimeMillis() + ".wav")
                .transcript("Won call")
                .company(company)
                .user(user)
                .createdAt(LocalDateTime.now())
                .build();
        call = callRecordRepository.save(call);

        DealRequest req = DealRequest.builder()
                .dealName("Won Deal " + System.currentTimeMillis())
                .accountName(company.getCompanyName())
                .dealValue(value)
                .dealStatus(DealStatus.WON)
                .dealStage(DealStage.CLOSED)
                .build();
        return dealService.saveOrUpdateDealForCall(call.getId(), req, principal);
    }

    private Deal createOpenDeal(Company company, User user, WorkspacePrincipal principal, BigDecimal value, DealStage stage) {
        CallRecord call = CallRecord.builder()
                .fileName("open_" + System.currentTimeMillis() + ".wav")
                .transcript("Open call")
                .company(company)
                .user(user)
                .createdAt(LocalDateTime.now())
                .build();
        call = callRecordRepository.save(call);

        DealRequest req = DealRequest.builder()
                .dealName("Open Deal " + System.currentTimeMillis())
                .accountName(company.getCompanyName())
                .dealValue(value)
                .dealStatus(DealStatus.OPEN)
                .dealStage(stage)
                .build();
        return dealService.saveOrUpdateDealForCall(call.getId(), req, principal);
    }

    private Deal createDealForCall(CallRecord call, BigDecimal value, DealStatus status, DealStage stage, String dealName, WorkspacePrincipal principal) {
        DealRequest request = DealRequest.builder()
                .dealName(dealName)
                .accountName(principal.getEmail())
                .dealValue(value)
                .dealStatus(status)
                .dealStage(stage)
                .build();
        return dealService.saveOrUpdateDealForCall(call.getId(), request, principal);
    }
}
