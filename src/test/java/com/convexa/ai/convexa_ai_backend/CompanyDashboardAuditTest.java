package com.convexa.ai.convexa_ai_backend;

import com.convexa.ai.convexa_ai_backend.dto.CompanyAlertDTO;
import com.convexa.ai.convexa_ai_backend.dto.CompanyStatsResponse;
import com.convexa.ai.convexa_ai_backend.dto.ExecutiveTeamInsightsDTO;
import com.convexa.ai.convexa_ai_backend.entity.*;
import com.convexa.ai.convexa_ai_backend.repository.*;
import com.convexa.ai.convexa_ai_backend.service.CompanyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class CompanyDashboardAuditTest {

    @Autowired
    private CompanyService companyService;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CallRecordRepository callRecordRepository;

    @Autowired
    private OrganizationMembershipRepository organizationMembershipRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    private Company companyA;
    private Company companyB;
    private User userA1;
    private User userA2;
    private User userB1;

    @BeforeEach
    void setUp() {
        long ts = System.currentTimeMillis();

        companyA = companyRepository.save(Company.builder()
                .companyName("Audit Corp A")
                .companySlug("audit-a-" + ts)
                .status(CompanyStatus.ACTIVE)
                .build());

        companyB = companyRepository.save(Company.builder()
                .companyName("Audit Corp B")
                .companySlug("audit-b-" + ts)
                .status(CompanyStatus.ACTIVE)
                .build());

        userA1 = userRepository.save(User.builder()
                .email("rep1.a@" + ts + ".test")
                .name("Alice Rep")
                .password("Password@123")
                .role(Role.USER)
                .company(companyA)
                .build());

        userA2 = userRepository.save(User.builder()
                .email("rep2.a@" + ts + ".test")
                .name("Bob Rep")
                .password("Password@123")
                .role(Role.USER)
                .company(companyA)
                .build());

        userB1 = userRepository.save(User.builder()
                .email("rep1.b@" + ts + ".test")
                .name("Charlie B")
                .password("Password@123")
                .role(Role.USER)
                .company(companyB)
                .build());

        organizationMembershipRepository.save(OrganizationMembership.builder()
                .user(userA1)
                .company(companyA)
                .role(Role.USER)
                .status(MembershipStatus.ACTIVE)
                .build());

        organizationMembershipRepository.save(OrganizationMembership.builder()
                .user(userA2)
                .company(companyA)
                .role(Role.USER)
                .status(MembershipStatus.ACTIVE)
                .build());

        organizationMembershipRepository.save(OrganizationMembership.builder()
                .user(userB1)
                .company(companyB)
                .role(Role.USER)
                .status(MembershipStatus.ACTIVE)
                .build());

        subscriptionRepository.save(Subscription.builder()
                .company(companyA)
                .plan(SubscriptionPlan.GROWTH)
                .status(SubscriptionStatus.ACTIVE)
                .seatLimit(10)
                .currentSeatCount(2)
                .build());
    }

    private CallRecord createCall(Company company, User user, int overallScore, String sentiment, String outcome, String riskFlagsJson, String objectionsJson, LocalDateTime createdAt) {
        CallRecord call = CallRecord.builder()
                .fileName("call_" + System.nanoTime() + ".mp3")
                .transcript("Test transcript for conversation.")
                .overallScore(overallScore)
                .communication(overallScore)
                .professionalism(overallScore)
                .problemResolution(overallScore)
                .customerSatisfaction(overallScore)
                .sentiment(sentiment)
                .outcomeStatus(outcome)
                .riskFlags(riskFlagsJson)
                .objections(objectionsJson)
                .status("COMPLETED")
                .company(company)
                .user(user)
                .createdAt(createdAt != null ? createdAt : LocalDateTime.now())
                .build();
        return callRecordRepository.save(call);
    }

    @Test
    void test_riskFlagsCalculation_and_unification() {
        // Call 1: 2 structured risk flags in JSON
        createCall(companyA, userA1, 85, "POSITIVE", "Won",
                "[{\"severity\":\"High\",\"message\":\"Pricing resistance\"},{\"severity\":\"Medium\",\"message\":\"Timeline delay\"}]",
                null, LocalDateTime.now());

        // Call 2: 1 structured risk flag
        createCall(companyA, userA2, 90, "POSITIVE", "Follow Up Required",
                "[{\"severity\":\"Low\",\"message\":\"Competitor mentioned\"}]",
                null, LocalDateTime.now());

        // Call 3: No structured JSON, but low QA < 65 and Escalated
        createCall(companyA, userA2, 55, "NEGATIVE", "Escalated",
                null, null, LocalDateTime.now());

        CompanyStatsResponse stats = companyService.getCompanyStats(companyA.getId(), "30d");

        // Total risk flags = 2 + 1 + 1 = 4
        assertEquals(4, stats.getRiskFlagsCount(), "Risk flags count should equal total risk items across calls");
        // Coaching needed count (distinct reps with avg QA < 65)
        // Alice: 85 -> 85 avg. Bob: (90 + 55)/2 = 72.5 avg. Neither is < 65.
        assertEquals(0, stats.getCoachingNeededCount());
    }

    @Test
    void test_executiveTeamInsights_calculation_and_company_isolation() {
        // Alice (userA1): 2 calls, scores 95 and 90 -> avg 92.5. 2 positive sentiments.
        createCall(companyA, userA1, 95, "POSITIVE", "Won", null, null, LocalDateTime.now());
        createCall(companyA, userA1, 90, "POSITIVE", "Won", null, null, LocalDateTime.now());

        // Bob (userA2): 1 call, score 60 -> avg 60.0. Negative sentiment.
        createCall(companyA, userA2, 60, "NEGATIVE", "Follow Up Required", null, "pricing too high", LocalDateTime.now());

        // Charlie (userB1) in Company B: score 100
        createCall(companyB, userB1, 100, "POSITIVE", "Won", null, null, LocalDateTime.now());

        CompanyStatsResponse statsA = companyService.getCompanyStats(companyA.getId(), "30d");
        ExecutiveTeamInsightsDTO insightsA = statsA.getTeamInsights();

        assertNotNull(insightsA);

        // Top Performer must be Alice (92.5 avg), NOT Charlie from Company B
        assertNotNull(insightsA.getTopPerformer());
        assertEquals("Alice Rep", insightsA.getTopPerformer().getEmployeeName());
        assertEquals(92.5, insightsA.getTopPerformer().getAvgScore());
        assertEquals(2, insightsA.getTopPerformer().getCallCount());

        // Needs Coaching must be Bob (60.0 avg)
        assertNotNull(insightsA.getNeedsCoaching());
        assertEquals("Bob Rep", insightsA.getNeedsCoaching().getEmployeeName());
        assertEquals(60.0, insightsA.getNeedsCoaching().getAvgScore());

        // Highest Volume must be Alice (2 calls)
        assertNotNull(insightsA.getHighestVolume());
        assertEquals("Alice Rep", insightsA.getHighestVolume().getEmployeeName());
        assertEquals(2, insightsA.getHighestVolume().getCallCount());

        // Best Single QA call must be Alice with 95 score
        assertNotNull(insightsA.getBestQA());
        assertEquals("Alice Rep", insightsA.getBestQA().getEmployeeName());
        assertEquals(95.0, insightsA.getBestQA().getScore());

        // Best Sentiment must be Alice (100% positive)
        assertNotNull(insightsA.getHighestSentiment());
        assertEquals("Alice Rep", insightsA.getHighestSentiment().getEmployeeName());
        assertEquals(100.0, insightsA.getHighestSentiment().getPositiveRatio());

        // Company B isolation check
        CompanyStatsResponse statsB = companyService.getCompanyStats(companyB.getId(), "30d");
        assertEquals("Charlie B", statsB.getTeamInsights().getTopPerformer().getEmployeeName());
        assertEquals(100.0, statsB.getTeamInsights().getTopPerformer().getAvgScore());
    }

    @Test
    void test_companyAlerts_generation() {
        // Create a call with pricing objection and low score for Bob
        createCall(companyA, userA2, 58, "NEGATIVE", "Escalated",
                "[{\"severity\":\"High\",\"message\":\"Executive escalation\"}]",
                "[{\"objection\":\"Pricing is 30% higher than budget\"}]",
                LocalDateTime.now());

        CompanyStatsResponse stats = companyService.getCompanyStats(companyA.getId(), "30d");
        List<CompanyAlertDTO> alerts = stats.getAlerts();

        assertNotNull(alerts);
        assertFalse(alerts.isEmpty(), "Alerts list should contain data-driven alerts");

        // Should contain QA drop alert, High risk conversation alert, Pricing objection alert
        boolean hasQaAlert = alerts.stream().anyMatch(a -> a.getTitle().contains("QA Score Dropped"));
        boolean hasRiskAlert = alerts.stream().anyMatch(a -> a.getTitle().contains("High-Risk") || a.getTitle().contains("Escalation"));
        boolean hasObjectionAlert = alerts.stream().anyMatch(a -> a.getTitle().contains("Pricing"));

        assertTrue(hasQaAlert, "Should generate a QA Drop alert");
        assertTrue(hasRiskAlert, "Should generate a High-Risk call alert");
        assertTrue(hasObjectionAlert, "Should generate an Objection concentration alert");
    }

    @Test
    void test_emptyCompany_returnsZeroAndHonestEmptyStates() {
        CompanyStatsResponse stats = companyService.getCompanyStats(companyA.getId(), "30d");

        assertEquals(0, stats.getTotalCalls());
        assertEquals(0.0, stats.getAvgScore());
        assertEquals(0, stats.getRiskFlagsCount());
        assertEquals(0, stats.getCoachingNeededCount());
        assertEquals(100.0, stats.getAiSuccessRate());

        assertNotNull(stats.getTeamInsights());
        assertNull(stats.getTeamInsights().getTopPerformer());
        assertNull(stats.getTeamInsights().getNeedsCoaching());
        assertNull(stats.getTeamInsights().getMostImproved());

        assertNotNull(stats.getAlerts());
    }

    @Test
    void test_dateRangeFiltering_insights_and_alerts() {
        // Call from 20 days ago
        createCall(companyA, userA1, 95, "POSITIVE", "Won", null, null, LocalDateTime.now().minusDays(20));

        // Call from 2 days ago
        createCall(companyA, userA2, 60, "NEGATIVE", "Follow Up Required", null, null, LocalDateTime.now().minusDays(2));

        // 7-day range should only see userA2's call
        CompanyStatsResponse stats7d = companyService.getCompanyStats(companyA.getId(), "7d");
        assertEquals(1, stats7d.getTotalCalls());
        assertEquals("Bob Rep", stats7d.getTeamInsights().getTopPerformer().getEmployeeName());

        // 30-day range should see both calls
        CompanyStatsResponse stats30d = companyService.getCompanyStats(companyA.getId(), "30d");
        assertEquals(2, stats30d.getTotalCalls());
        assertEquals("Alice Rep", stats30d.getTeamInsights().getTopPerformer().getEmployeeName());
    }
}
