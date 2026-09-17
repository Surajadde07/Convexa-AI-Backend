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
import java.math.RoundingMode;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class RevenueIntelligenceValidationTest {

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

    private Company company;
    private User owner;
    private WorkspacePrincipal principal;

    @BeforeEach
    void setUp() {
        company = Company.builder()
                .companyName("Controlled Test Corp")
                .companySlug("test-corp-" + System.currentTimeMillis())
                .status(CompanyStatus.ACTIVE)
                .build();
        company = companyRepository.save(company);

        owner = User.builder()
                .email("owner@" + System.currentTimeMillis() + ".com")
                .name("Test Owner")
                .password("Password123!")
                .role(Role.OWNER)
                .company(company)
                .build();
        owner = userRepository.save(owner);

        principal = WorkspacePrincipal.builder()
                .userId(owner.getId())
                .companyId(company.getId())
                .role(owner.getRole())
                .email(owner.getEmail())
                .build();
    }

    @Test
    void test1_TargetSetAndCoverageCalculation() {
        // Setup 4 open deals: $100 (Discovery), $80 (Demo), $50 (Proposal), $98 (Negotiation) -> Total $328
        // And 1 Closed Won deal: $40
        CallRecord call1 = createCall("discovery_call.wav", "Discovery call transcript");
        createDealForCall(call1, new BigDecimal("100.00"), DealStatus.OPEN, DealStage.DISCOVERY, "Discovery Deal", "Acme");

        CallRecord call2 = createCall("demo_call.wav", "Demo call transcript");
        createDealForCall(call2, new BigDecimal("80.00"), DealStatus.OPEN, DealStage.DEMO, "Demo Deal", "Beta Inc");

        CallRecord call3 = createCall("proposal_call.wav", "Proposal call transcript");
        createDealForCall(call3, new BigDecimal("50.00"), DealStatus.OPEN, DealStage.PROPOSAL, "Proposal Deal", "Gamma LLC");

        CallRecord call4 = createCall("negotiation_call.wav", "Negotiation call transcript");
        createDealForCall(call4, new BigDecimal("98.00"), DealStatus.OPEN, DealStage.NEGOTIATION, "Negotiation Deal", "Delta Corp");

        CallRecord callWon = createCall("won_call.wav", "Closed won call transcript");
        createDealForCall(callWon, new BigDecimal("40.00"), DealStatus.WON, DealStage.CLOSED, "Won Deal", "Epsilon");

        // Set $400 quarterly target
        RevenueTargetRequest targetReq = RevenueTargetRequest.builder()
                .target(new BigDecimal("400.00"))
                .period("QUARTERLY")
                .build();
        dealService.updateRevenueTarget(targetReq, principal);

        // Fetch Pipeline Intelligence
        PipelineIntelligenceResponse intel = dealService.getPipelineIntelligence(principal, "this_quarter");

        System.out.println("=== TEST 1 RESULTS ===");
        System.out.println("Open Pipeline: $" + intel.getTotalOpenValue());
        System.out.println("Revenue Target: $" + intel.getRevenueTarget());
        System.out.println("Coverage Ratio: " + intel.getPipelineCoverageRatio() + "x");
        System.out.println("Closed Won: $" + intel.getPeriodClosedWon());
        System.out.println("Health Weighted Pipeline: $" + intel.getHealthWeightedPipeline());
        System.out.println("Gap to Target: $" + intel.getGapToTarget());

        assertEquals(new BigDecimal("328.00"), intel.getTotalOpenValue());
        assertEquals(new BigDecimal("400.00"), intel.getRevenueTarget());
        assertEquals("ALIGNED", intel.getTargetAlignmentStatus());
        // Remaining Gap = $400 - $40 = $360.00
        // Coverage Ratio = $328 / $360 = 0.91x
        assertEquals(0.91, intel.getPipelineCoverageRatio());
        assertEquals(new BigDecimal("360.00"), intel.getGapToTarget());
        assertEquals(new BigDecimal("360.00"), intel.getActualRevenueGap());

        // Expected Health Weighted is an estimate and NOT subtracted from actual revenue gap:
        assertNotNull(intel.getHealthWeightedPipeline());
        assertTrue(intel.getHealthWeightedPipeline().compareTo(BigDecimal.ZERO) > 0);
        // Verify actual gap does not include health-weighted pipeline
        assertNotEquals(new BigDecimal("400.00").subtract(intel.getPeriodClosedWon().add(intel.getHealthWeightedPipeline())), intel.getGapToTarget());
    }

    @Test
    void test2_CreateLostDeal() {
        // Setup 2 open deals ($100 and $98) -> Total $198
        CallRecord call1 = createCall("call1.wav", "Transcript 1");
        Deal deal1 = createDealForCall(call1, new BigDecimal("100.00"), DealStatus.OPEN, DealStage.DISCOVERY, "Deal 1", "Acme");

        CallRecord call2 = createCall("call2.wav", "Transcript 2");
        Deal deal2 = createDealForCall(call2, new BigDecimal("98.00"), DealStatus.OPEN, DealStage.NEGOTIATION, "Deal 2", "Beta");

        PipelineIntelligenceResponse initialIntel = dealService.getPipelineIntelligence(principal, "all");
        assertEquals(new BigDecimal("198.00"), initialIntel.getTotalOpenValue());
        assertEquals(2, initialIntel.getTotalOpenDeals());
        assertEquals(BigDecimal.ZERO, initialIntel.getTotalLostValue());

        // Change Deal 2 (Negotiation $98) to LOST + CLOSED
        DealRequest lostReq = DealRequest.builder()
                .dealValue(new BigDecimal("98.00"))
                .dealStatus(DealStatus.LOST)
                .dealStage(DealStage.CLOSED)
                .build();
        dealService.saveOrUpdateDealForCall(call2.getId(), lostReq, principal);

        PipelineIntelligenceResponse updatedIntel = dealService.getPipelineIntelligence(principal, "all");

        System.out.println("=== TEST 2 RESULTS ===");
        System.out.println("Initial Open Pipeline: $198.00 -> Updated Open Pipeline: $" + updatedIntel.getTotalOpenValue());
        System.out.println("Initial Open Deals: 2 -> Updated Open Deals: " + updatedIntel.getTotalOpenDeals());
        System.out.println("Updated Lost Value: $" + updatedIntel.getTotalLostValue());

        // Verify: Open Pipeline decreases
        assertEquals(new BigDecimal("100.00"), updatedIntel.getTotalOpenValue());
        // Verify: Active deal count decreases
        assertEquals(1, updatedIntel.getTotalOpenDeals());
        // Verify: Lost value increases
        assertEquals(new BigDecimal("98.00"), updatedIntel.getTotalLostValue());
        // Verify: Closed stage is not in active stage breakdown
        boolean closedInActiveBreakdown = updatedIntel.getStageBreakdown().stream()
                .anyMatch(s -> "CLOSED".equalsIgnoreCase(s.getStage()));
        assertFalse(closedInActiveBreakdown);
    }

    @Test
    void test3_CreatePricingObjection() {
        // Create an open Proposal deal ($99.00) with a call containing an unresolved pricing objection
        CallRecord callObj = CallRecord.builder()
                .fileName("pricing_call.wav")
                .transcript("Customer expressed serious price concerns")
                .objections("[{\"objection\":\"Pricing is too high for our budget\",\"resolved\":false}]")
                .company(company)
                .user(owner)
                .createdAt(LocalDateTime.now())
                .build();
        callObj = callRecordRepository.save(callObj);

        createDealForCall(callObj, new BigDecimal("99.00"), DealStatus.OPEN, DealStage.PROPOSAL, "Pricing Deal", "Pricing Account");

        PipelineIntelligenceResponse intel = dealService.getPipelineIntelligence(principal, "all");

        System.out.println("=== TEST 3 RESULTS ===");
        System.out.println("Pricing Pressure Value: $" + intel.getPricingPressureValue());
        System.out.println("Pricing Pressure Deals: " + intel.getPricingPressureDeals());

        assertEquals(new BigDecimal("99.00"), intel.getPricingPressureValue());
        assertEquals(1, intel.getPricingPressureDeals());
    }

    @Test
    void test4_CreateCompetitorExposure() {
        // Create an open Negotiation deal ($75.00) mentioning a competitor
        CallRecord callComp = CallRecord.builder()
                .fileName("competitor_call.wav")
                .transcript("Customer mentioned evaluating Salesforce and Gong against us")
                .keywords("gong, salesforce, evaluation")
                .company(company)
                .user(owner)
                .createdAt(LocalDateTime.now())
                .build();
        callComp = callRecordRepository.save(callComp);

        createDealForCall(callComp, new BigDecimal("75.00"), DealStatus.OPEN, DealStage.NEGOTIATION, "Competitor Deal", "Comp Account");

        PipelineIntelligenceResponse intel = dealService.getPipelineIntelligence(principal, "all");

        System.out.println("=== TEST 4 RESULTS ===");
        System.out.println("Competitive Exposure Value: $" + intel.getCompetitiveExposureValue());
        System.out.println("Competitive Exposure Deals: " + intel.getCompetitiveExposureDeals());

        assertEquals(new BigDecimal("75.00"), intel.getCompetitiveExposureValue());
        assertEquals(1, intel.getCompetitiveExposureDeals());
    }

    @Test
    void test5_HealthyDealEscapesRiskBucket() {
        // 1. Create a risky deal ($100 in Negotiation with high risk flag)
        CallRecord riskyCall = CallRecord.builder()
                .fileName("risky_call.wav")
                .transcript("Severe risk detected")
                .riskFlags("[{\"severity\":\"High\",\"message\":\"Executive Sponsor Left\"}]")
                .company(company)
                .user(owner)
                .createdAt(LocalDateTime.now().minusDays(20))
                .build();
        riskyCall = callRecordRepository.save(riskyCall);
        Deal riskyDeal = createDealForCall(riskyCall, new BigDecimal("100.00"), DealStatus.OPEN, DealStage.NEGOTIATION, "Risky Deal", "Risky Co");

        // 2. Create a HEALTHY deal ($150 in Demo with recent call, positive sentiment, high intent, no flags, no objections)
        CallRecord healthyCall = CallRecord.builder()
                .fileName("healthy_call.wav")
                .transcript("Great call, customer loves the demo and wants to proceed")
                .sentiment("Positive")
                .buyingIntent("High")
                .riskFlags(null)
                .objections(null)
                .company(company)
                .user(owner)
                .createdAt(LocalDateTime.now()) // fresh today
                .build();
        healthyCall = callRecordRepository.save(healthyCall);
        Deal healthyDeal = createDealForCall(healthyCall, new BigDecimal("150.00"), DealStatus.OPEN, DealStage.DEMO, "Healthy Deal", "Healthy Co");

        PipelineIntelligenceResponse intel = dealService.getPipelineIntelligence(principal, "all");

        System.out.println("=== TEST 5 RESULTS ===");
        System.out.println("Total Open Deals: " + intel.getTotalOpenDeals() + " ($" + intel.getTotalOpenValue() + ")");
        System.out.println("At-Risk Deal Count: " + intel.getAtRiskDealCount() + " ($" + intel.getAtRiskPipelineValue() + ")");
        System.out.println("Healthy Engagement Deals: " + intel.getHealthyEngagementDeals());
        System.out.println("Declining/Risk Engagement Deals: " + intel.getDecliningEngagementDeals());

        // Verify: Total 2 open deals ($250)
        assertEquals(2, intel.getTotalOpenDeals());
        assertEquals(new BigDecimal("250.00"), intel.getTotalOpenValue());

        // Verify: Only 1 deal ($100) is at risk
        assertEquals(1, intel.getAtRiskDealCount());
        assertEquals(new BigDecimal("100.00"), intel.getAtRiskPipelineValue());

        // Verify: Healthy deal (ID) is NOT in the atRiskDeals list
        boolean healthyDealInRiskList = intel.getAtRiskDeals().stream()
                .anyMatch(item -> item.getDealId().equals(healthyDeal.getId()));
        assertFalse(healthyDealInRiskList, "Healthy deal MUST NOT be in the at-risk deals list");

        // Verify: Risky deal IS in the atRiskDeals list
        boolean riskyDealInRiskList = intel.getAtRiskDeals().stream()
                .anyMatch(item -> item.getDealId().equals(riskyDeal.getId()));
        assertTrue(riskyDealInRiskList, "Risky deal MUST be in the at-risk deals list");

        // Verify: healthyEngagementDeals count is 1
        assertEquals(1, intel.getHealthyEngagementDeals());
    }

    private CallRecord createCall(String filename, String transcript) {
        CallRecord call = CallRecord.builder()
                .fileName(filename)
                .transcript(transcript)
                .company(company)
                .user(owner)
                .createdAt(LocalDateTime.now())
                .build();
        return callRecordRepository.save(call);
    }

    private Deal createDealForCall(CallRecord call, BigDecimal value, DealStatus status, DealStage stage, String dealName, String accountName) {
        DealRequest request = DealRequest.builder()
                .dealName(dealName)
                .accountName(accountName)
                .dealValue(value)
                .dealStatus(status)
                .dealStage(stage)
                .build();
        return dealService.saveOrUpdateDealForCall(call.getId(), request, principal);
    }
}
