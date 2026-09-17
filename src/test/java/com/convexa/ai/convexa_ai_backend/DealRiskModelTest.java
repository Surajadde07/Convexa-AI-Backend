package com.convexa.ai.convexa_ai_backend;

import com.convexa.ai.convexa_ai_backend.dto.DealRequest;
import com.convexa.ai.convexa_ai_backend.dto.PipelineIntelligenceResponse;
import com.convexa.ai.convexa_ai_backend.entity.*;
import com.convexa.ai.convexa_ai_backend.repository.CallRecordRepository;
import com.convexa.ai.convexa_ai_backend.repository.CompanyRepository;
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

@SpringBootTest
@Transactional
public class DealRiskModelTest {

    @Autowired
    private DealService dealService;

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
                .companyName("Risk Model Test Corp")
                .companySlug("risk-corp-" + System.currentTimeMillis())
                .status(CompanyStatus.ACTIVE)
                .build();
        company = companyRepository.save(company);

        owner = User.builder()
                .email("risk.owner@" + System.currentTimeMillis() + ".com")
                .name("Risk Model Owner")
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

    // 1. Discovery inactive 31 days with no other risks -> NOT CRITICAL (STALLED, $0 financial exposure)
    @Test
    void test1_DiscoveryInactive31Days_NotCritical() {
        CallRecord call = createCallWithDate("discovery.wav", LocalDateTime.now().minusDays(31), null, null, "Neutral", "Medium");
        createDeal(call, new BigDecimal("10000.00"), DealStatus.OPEN, DealStage.DISCOVERY, "Discovery Deal");

        PipelineIntelligenceResponse intel = dealService.getPipelineIntelligence(principal, "all");

        assertEquals(0, intel.getAtRiskDealCount(), "Inactivity alone must not increment at-risk deal count");
        assertEquals(BigDecimal.ZERO, intel.getAtRiskPipelineValue(), "Inactivity alone must not add to at-risk financial exposure");
        assertEquals(1, intel.getAtRiskDeals().size(), "Deal should still appear in intervention list as STALLED");
        PipelineIntelligenceResponse.AtRiskDealItem item = intel.getAtRiskDeals().get(0);
        assertNotEquals("CRITICAL", item.getRiskLevel(), "Discovery deal with inactivity alone must NOT be CRITICAL");
        assertEquals("STALLED", item.getRiskLevel(), "Discovery deal with inactivity alone must be STALLED");
        assertTrue(item.getMainRiskReason().contains("31 days"));
    }

    // 2. Demo inactive 22 days with no other risks -> NOT CRITICAL (STALLED, $0 financial exposure)
    @Test
    void test2_DemoInactive22Days_NotCritical() {
        CallRecord call = createCallWithDate("demo.wav", LocalDateTime.now().minusDays(22), null, null, "Neutral", "Medium");
        createDeal(call, new BigDecimal("20000.00"), DealStatus.OPEN, DealStage.DEMO, "Demo Deal");

        PipelineIntelligenceResponse intel = dealService.getPipelineIntelligence(principal, "all");

        assertEquals(0, intel.getAtRiskDealCount(), "Inactivity alone must not increment at-risk deal count");
        assertEquals(BigDecimal.ZERO, intel.getAtRiskPipelineValue(), "Inactivity alone must not add to at-risk financial exposure");
        assertEquals(1, intel.getAtRiskDeals().size(), "Deal should still appear in intervention list as STALLED");
        PipelineIntelligenceResponse.AtRiskDealItem item = intel.getAtRiskDeals().get(0);
        assertNotEquals("CRITICAL", item.getRiskLevel(), "Demo deal with inactivity alone must NOT be CRITICAL");
        assertEquals("STALLED", item.getRiskLevel(), "Demo deal with inactivity alone must be STALLED");
        assertTrue(item.getMainRiskReason().contains("22 days"));
    }

    // 3. Proposal inactive 15 days with no other risks -> NOT CRITICAL (STALLED, $0 financial exposure)
    @Test
    void test3_ProposalInactive15Days_NotCritical() {
        CallRecord call = createCallWithDate("proposal.wav", LocalDateTime.now().minusDays(15), null, null, "Neutral", "Medium");
        createDeal(call, new BigDecimal("30000.00"), DealStatus.OPEN, DealStage.PROPOSAL, "Proposal Deal");

        PipelineIntelligenceResponse intel = dealService.getPipelineIntelligence(principal, "all");

        assertEquals(0, intel.getAtRiskDealCount(), "Inactivity alone must not increment at-risk deal count");
        assertEquals(BigDecimal.ZERO, intel.getAtRiskPipelineValue(), "Inactivity alone must not add to at-risk financial exposure");
        assertEquals(1, intel.getAtRiskDeals().size(), "Deal should still appear in intervention list as STALLED");
        PipelineIntelligenceResponse.AtRiskDealItem item = intel.getAtRiskDeals().get(0);
        assertNotEquals("CRITICAL", item.getRiskLevel(), "Proposal deal with inactivity alone must NOT be CRITICAL");
        assertEquals("STALLED", item.getRiskLevel(), "Proposal deal with inactivity alone must be STALLED");
        assertTrue(item.getMainRiskReason().contains("15 days"));
    }

    // 4. Negotiation inactive 11 days with no other risks -> NOT CRITICAL (STALLED, $0 financial exposure)
    @Test
    void test4_NegotiationInactive11Days_NotCritical() {
        CallRecord call = createCallWithDate("negotiation.wav", LocalDateTime.now().minusDays(11), null, null, "Neutral", "Medium");
        createDeal(call, new BigDecimal("40000.00"), DealStatus.OPEN, DealStage.NEGOTIATION, "Negotiation Deal");

        PipelineIntelligenceResponse intel = dealService.getPipelineIntelligence(principal, "all");

        assertEquals(0, intel.getAtRiskDealCount(), "Inactivity alone must not increment at-risk deal count");
        assertEquals(BigDecimal.ZERO, intel.getAtRiskPipelineValue(), "Inactivity alone must not add to at-risk financial exposure");
        assertEquals(1, intel.getAtRiskDeals().size(), "Deal should still appear in intervention list as STALLED");
        PipelineIntelligenceResponse.AtRiskDealItem item = intel.getAtRiskDeals().get(0);
        assertNotEquals("CRITICAL", item.getRiskLevel(), "Negotiation deal with inactivity alone must NOT be CRITICAL");
        assertEquals("STALLED", item.getRiskLevel(), "Negotiation deal with inactivity alone must be STALLED");
        assertTrue(item.getMainRiskReason().contains("11 days"));
    }

    // 5. High-severity risk flag alone -> NOT CRITICAL (HIGH)
    @Test
    void test5_HighSeverityRiskFlagAlone_NotCritical() {
        // Recent call (today) so no inactivity risk
        CallRecord call = createCallWithDate("flag_alone.wav", LocalDateTime.now(),
                "[{\"severity\":\"High\",\"message\":\"Single sponsor risk\"}]", null, "Neutral", "Medium");
        createDeal(call, new BigDecimal("50000.00"), DealStatus.OPEN, DealStage.DISCOVERY, "Flag Alone Deal");

        PipelineIntelligenceResponse intel = dealService.getPipelineIntelligence(principal, "all");

        assertEquals(1, intel.getAtRiskDealCount());
        PipelineIntelligenceResponse.AtRiskDealItem item = intel.getAtRiskDeals().get(0);
        assertNotEquals("CRITICAL", item.getRiskLevel(), "High-severity risk flag alone must NOT be CRITICAL");
        assertEquals("HIGH", item.getRiskLevel());
        assertEquals("High-severity risk flag detected on deal call", item.getMainRiskReason());
    }

    // 6. High-severity risk flag + another meaningful risk signal -> CRITICAL
    @Test
    void test6_HighSeverityRiskFlagPlusInactivity_Critical() {
        // Negotiation deal inactive for 12 days (> 10d threshold) + High severity risk flag
        CallRecord call = createCallWithDate("flag_compounding.wav", LocalDateTime.now().minusDays(12),
                "[{\"severity\":\"High\",\"message\":\"Champion departed\"}]", null, "Neutral", "Medium");
        createDeal(call, new BigDecimal("60000.00"), DealStatus.OPEN, DealStage.NEGOTIATION, "Compounding Flag Deal");

        PipelineIntelligenceResponse intel = dealService.getPipelineIntelligence(principal, "all");

        assertEquals(1, intel.getAtRiskDealCount());
        PipelineIntelligenceResponse.AtRiskDealItem item = intel.getAtRiskDeals().get(0);
        assertEquals("CRITICAL", item.getRiskLevel(), "High-severity risk flag + inactivity must be CRITICAL");
        assertEquals("High-severity risk flag detected on deal call", item.getMainRiskReason());
    }

    // 7. Proposal + unresolved pricing objection + meaningful inactivity -> CRITICAL
    @Test
    void test7_ProposalPricingPlusInactivity_Critical() {
        // Proposal inactive 16 days (> 14d) + unresolved pricing objection
        CallRecord call = createCallWithDate("proposal_pricing.wav", LocalDateTime.now().minusDays(16),
                null, "[{\"objection\":\"Price is too high for our budget\",\"resolved\":false}]", "Neutral", "Medium");
        createDeal(call, new BigDecimal("70000.00"), DealStatus.OPEN, DealStage.PROPOSAL, "Proposal Pricing Deal");

        PipelineIntelligenceResponse intel = dealService.getPipelineIntelligence(principal, "all");

        assertEquals(1, intel.getAtRiskDealCount());
        PipelineIntelligenceResponse.AtRiskDealItem item = intel.getAtRiskDeals().get(0);
        assertEquals("CRITICAL", item.getRiskLevel(), "Proposal with unresolved pricing + inactivity must be CRITICAL");
        assertEquals("Unresolved pricing/budget friction in late stage", item.getMainRiskReason());
    }

    // 8. Negotiation + unresolved pricing objection + meaningful inactivity -> CRITICAL
    @Test
    void test8_NegotiationPricingPlusInactivity_Critical() {
        // Negotiation inactive 12 days (> 10d) + unresolved pricing objection
        CallRecord call = createCallWithDate("neg_pricing.wav", LocalDateTime.now().minusDays(12),
                null, "[{\"objection\":\"Cost is too expensive\",\"resolved\":false}]", "Neutral", "Medium");
        createDeal(call, new BigDecimal("80000.00"), DealStatus.OPEN, DealStage.NEGOTIATION, "Negotiation Pricing Deal");

        PipelineIntelligenceResponse intel = dealService.getPipelineIntelligence(principal, "all");

        assertEquals(1, intel.getAtRiskDealCount());
        PipelineIntelligenceResponse.AtRiskDealItem item = intel.getAtRiskDeals().get(0);
        assertEquals("CRITICAL", item.getRiskLevel(), "Negotiation with unresolved pricing + inactivity must be CRITICAL");
        assertEquals("Unresolved pricing/budget friction in late stage", item.getMainRiskReason());
    }

    // 9. Negative sentiment + low/none buying intent + meaningful inactivity -> CRITICAL
    @Test
    void test9_NegativeSentimentLowIntentInactivity_Critical() {
        // Demo inactive 25 days (> 21d) + Negative sentiment + Low intent
        CallRecord call = createCallWithDate("triple_risk.wav", LocalDateTime.now().minusDays(25),
                null, null, "Negative", "Low");
        createDeal(call, new BigDecimal("90000.00"), DealStatus.OPEN, DealStage.DEMO, "Triple Negative Deal");

        PipelineIntelligenceResponse intel = dealService.getPipelineIntelligence(principal, "all");

        assertEquals(1, intel.getAtRiskDealCount());
        PipelineIntelligenceResponse.AtRiskDealItem item = intel.getAtRiskDeals().get(0);
        assertEquals("CRITICAL", item.getRiskLevel(), "Negative sentiment + low intent + inactivity must be CRITICAL");
    }

    // 10. Positive sentiment + high buying intent + inactivity -> NOT CRITICAL (STALLED, $0 financial exposure)
    @Test
    void test10_PositiveSentimentHighIntentWithInactivity_NotCritical() {
        // Discovery inactive 35 days (> 30d) but with Positive sentiment and High intent
        CallRecord call = createCallWithDate("positive_inactive.wav", LocalDateTime.now().minusDays(35),
                null, null, "Positive", "High");
        createDeal(call, new BigDecimal("55000.00"), DealStatus.OPEN, DealStage.DISCOVERY, "Positive Inactive Deal");

        PipelineIntelligenceResponse intel = dealService.getPipelineIntelligence(principal, "all");

        assertEquals(0, intel.getAtRiskDealCount(), "Inactivity alone with positive sentiment must not increment at-risk deal count");
        assertEquals(BigDecimal.ZERO, intel.getAtRiskPipelineValue(), "Inactivity alone must not add to at-risk pipeline value");
        assertEquals(1, intel.getAtRiskDeals().size(), "Deal should still appear in intervention list as STALLED");
        PipelineIntelligenceResponse.AtRiskDealItem item = intel.getAtRiskDeals().get(0);
        assertNotEquals("CRITICAL", item.getRiskLevel(), "Positive sentiment + high intent + inactivity alone must NOT be CRITICAL");
        assertEquals("STALLED", item.getRiskLevel(), "Positive sentiment + high intent + inactivity alone must be STALLED");
    }

    // 13. Separation of Stalled Deals from Material Financial Exposure
    @Test
    void test13_SeparationOfStalledAndMaterialFinancialExposure() {
        // Deal A: Stalled only (Discovery inactive 35 days, $15,000, no objections/risk flags)
        CallRecord callA = createCallWithDate("stalled_deal.wav", LocalDateTime.now().minusDays(35),
                null, null, "Neutral", "Medium");
        createDeal(callA, new BigDecimal("15000.00"), DealStatus.OPEN, DealStage.DISCOVERY, "Stalled Deal A");

        // Deal B: Material financial risk (Proposal, recent 2 days, $30,000, unresolved pricing objection)
        CallRecord callB = createCallWithDate("material_risk.wav", LocalDateTime.now().minusDays(2),
                null, "[{\"objection\":\"Pricing is 40% over budget\",\"resolved\":false}]", "Neutral", "Medium");
        createDeal(callB, new BigDecimal("30000.00"), DealStatus.OPEN, DealStage.PROPOSAL, "Material Risk Deal B");

        // Deal C: Healthy active deal (Demo, recent 1 day, $20,000, positive sentiment, high intent)
        CallRecord callC = createCallWithDate("healthy_active.wav", LocalDateTime.now().minusDays(1),
                null, null, "Positive", "High");
        createDeal(callC, new BigDecimal("20000.00"), DealStatus.OPEN, DealStage.DEMO, "Healthy Deal C");

        PipelineIntelligenceResponse intel = dealService.getPipelineIntelligence(principal, "all");

        // Total Open: 3 deals, $65,000
        assertEquals(3, intel.getTotalOpenDeals());
        assertEquals(new BigDecimal("65000.00"), intel.getTotalOpenValue());

        // Material Financial Exposure: only Deal B ($30,000, 1 deal)
        assertEquals(1, intel.getAtRiskDealCount(), "Only Deal B has material commercial risk");
        assertEquals(new BigDecimal("30000.00"), intel.getAtRiskPipelineValue(), "Only Deal B contributes to at-risk financial exposure");

        // Intervention list: 2 deals (Deal B material HIGH + Deal A operational STALLED)
        assertEquals(2, intel.getAtRiskDeals().size(), "Both Deal B (material) and Deal A (stalled) appear in intervention list");

        // Deal B is ranked higher than Deal A (HIGH severity 2 > STALLED severity 1)
        PipelineIntelligenceResponse.AtRiskDealItem firstItem = intel.getAtRiskDeals().get(0);
        PipelineIntelligenceResponse.AtRiskDealItem secondItem = intel.getAtRiskDeals().get(1);

        assertEquals("Material Risk Deal B", firstItem.getDealName());
        assertEquals("HIGH", firstItem.getRiskLevel());

        assertEquals("Stalled Deal A", secondItem.getDealName());
        assertEquals("STALLED", secondItem.getRiskLevel());

        // Deal C is healthy and excluded from intervention
        boolean dealCInIntervention = intel.getAtRiskDeals().stream()
                .anyMatch(d -> "Healthy Deal C".equals(d.getDealName()));
        assertFalse(dealCInIntervention, "Healthy Deal C must not be in intervention list");
    }

    // 11. No risk signals and recent activity -> Healthy / not at risk
    @Test
    void test11_HealthyRecentActivity_NotAtRisk() {
        CallRecord call = createCallWithDate("healthy.wav", LocalDateTime.now().minusDays(3),
                null, null, "Positive", "High");
        Deal deal = createDeal(call, new BigDecimal("35000.00"), DealStatus.OPEN, DealStage.DEMO, "Healthy Deal");

        PipelineIntelligenceResponse intel = dealService.getPipelineIntelligence(principal, "all");

        assertEquals(0, intel.getAtRiskDealCount());
        assertEquals(BigDecimal.ZERO, intel.getAtRiskPipelineValue());
        assertTrue(intel.getAtRiskDeals().isEmpty());
        assertEquals(1, intel.getHealthyEngagementDeals());
        assertEquals(0, intel.getDecliningEngagementDeals());
    }

    // 12. Closed deals -> remain excluded from active pipeline risk calculations
    @Test
    void test12_ClosedDeals_ExcludedFromAtRisk() {
        // Closed Won deal inactive for 50 days with negative sentiment and objections
        CallRecord callWon = createCallWithDate("closed_won.wav", LocalDateTime.now().minusDays(50),
                "[{\"severity\":\"High\",\"message\":\"Old flag\"}]",
                "[{\"objection\":\"Old objection\",\"resolved\":false}]", "Negative", "Low");
        createDeal(callWon, new BigDecimal("100000.00"), DealStatus.WON, DealStage.CLOSED, "Closed Won Deal");

        // Closed Lost deal inactive for 60 days
        CallRecord callLost = createCallWithDate("closed_lost.wav", LocalDateTime.now().minusDays(60),
                null, null, "Negative", "None");
        createDeal(callLost, new BigDecimal("50000.00"), DealStatus.LOST, DealStage.CLOSED, "Closed Lost Deal");

        PipelineIntelligenceResponse intel = dealService.getPipelineIntelligence(principal, "all");

        assertEquals(0, intel.getTotalOpenDeals());
        assertEquals(BigDecimal.ZERO, intel.getTotalOpenValue());
        assertEquals(0, intel.getAtRiskDealCount());
        assertEquals(BigDecimal.ZERO, intel.getAtRiskPipelineValue());
        assertTrue(intel.getAtRiskDeals().isEmpty());
    }

    private CallRecord createCallWithDate(String filename, LocalDateTime createdAt,
                                          String riskFlags, String objections, String sentiment, String buyingIntent) {
        CallRecord call = CallRecord.builder()
                .fileName(filename)
                .transcript("Transcript for " + filename)
                .riskFlags(riskFlags)
                .objections(objections)
                .sentiment(sentiment)
                .buyingIntent(buyingIntent)
                .company(company)
                .user(owner)
                .createdAt(createdAt)
                .build();
        return callRecordRepository.save(call);
    }

    private Deal createDeal(CallRecord call, BigDecimal value, DealStatus status, DealStage stage, String dealName) {
        DealRequest request = DealRequest.builder()
                .dealName(dealName)
                .accountName(company.getCompanyName())
                .dealValue(value)
                .dealStatus(status)
                .dealStage(stage)
                .build();
        return dealService.saveOrUpdateDealForCall(call.getId(), request, principal);
    }
}
