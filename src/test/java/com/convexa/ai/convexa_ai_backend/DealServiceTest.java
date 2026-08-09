package com.convexa.ai.convexa_ai_backend;

import com.convexa.ai.convexa_ai_backend.dto.DealRequest;
import com.convexa.ai.convexa_ai_backend.dto.PipelineSummaryResponse;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class DealServiceTest {

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
    private User userA;
    private User userB;
    private CallRecord callA1;
    private CallRecord callA2;
    private CallRecord callB1;

    private WorkspacePrincipal principalA;
    private WorkspacePrincipal principalB;

    @BeforeEach
    void setUp() {
        // Setup Company A and its owner
        companyA = Company.builder()
                .companyName("Company A")
                .companySlug("company-a-" + System.currentTimeMillis())
                .status(CompanyStatus.ACTIVE)
                .build();
        companyA = companyRepository.save(companyA);

        userA = User.builder()
                .email("owner.a@" + System.currentTimeMillis() + ".test")
                .name("Owner A")
                .password("Password@123")
                .role(Role.OWNER)
                .company(companyA)
                .build();
        userA = userRepository.save(userA);

        principalA = WorkspacePrincipal.builder()
                .userId(userA.getId())
                .companyId(companyA.getId())
                .role(userA.getRole())
                .email(userA.getEmail())
                .build();

        // Setup Company B and its owner
        companyB = Company.builder()
                .companyName("Company B")
                .companySlug("company-b-" + System.currentTimeMillis())
                .status(CompanyStatus.ACTIVE)
                .build();
        companyB = companyRepository.save(companyB);

        userB = User.builder()
                .email("owner.b@" + System.currentTimeMillis() + ".test")
                .name("Owner B")
                .password("Password@123")
                .role(Role.OWNER)
                .company(companyB)
                .build();
        userB = userRepository.save(userB);

        principalB = WorkspacePrincipal.builder()
                .userId(userB.getId())
                .companyId(companyB.getId())
                .role(userB.getRole())
                .email(userB.getEmail())
                .build();

        // Setup Call Records
        callA1 = CallRecord.builder()
                .fileName("call-a1.wav")
                .transcript("Hello world from A1")
                .company(companyA)
                .user(userA)
                .build();
        callA1 = callRecordRepository.save(callA1);

        callA2 = CallRecord.builder()
                .fileName("call-a2.wav")
                .transcript("Hello world from A2")
                .company(companyA)
                .user(userA)
                .build();
        callA2 = callRecordRepository.save(callA2);

        callB1 = CallRecord.builder()
                .fileName("call-b1.wav")
                .transcript("Hello world from B1")
                .company(companyB)
                .user(userB)
                .build();
        callB1 = callRecordRepository.save(callB1);
    }

    @Test
    void test1_CreateDeal() {
        DealRequest request = DealRequest.builder()
                .dealValue(new BigDecimal("15000.00"))
                .dealStatus(DealStatus.OPEN)
                .dealStage(DealStage.DISCOVERY)
                .build();

        Deal deal = dealService.saveOrUpdateDealForCall(callA1.getId(), request, principalA);

        assertNotNull(deal.getId());
        assertEquals(new BigDecimal("15000.00"), deal.getDealValue());
        assertEquals(DealStatus.OPEN, deal.getDealStatus());
        assertEquals(DealStage.DISCOVERY, deal.getDealStage());
        assertEquals(companyA.getId(), deal.getCompany().getId());
        assertEquals(userA.getId(), deal.getCreatedBy().getId());

        // Verify relationship on CallRecord
        CallRecord updatedCall = callRecordRepository.findById(callA1.getId()).orElseThrow();
        assertNotNull(updatedCall.getDeal());
        assertEquals(deal.getId(), updatedCall.getDeal().getId());
    }

    @Test
    void test2_UpdateDeal() {
        DealRequest createReq = DealRequest.builder()
                .dealValue(new BigDecimal("15000.00"))
                .dealStatus(DealStatus.OPEN)
                .dealStage(DealStage.DISCOVERY)
                .build();

        Deal deal = dealService.saveOrUpdateDealForCall(callA1.getId(), createReq, principalA);

        DealRequest updateReq = DealRequest.builder()
                .dealValue(new BigDecimal("25000.50"))
                .dealStatus(DealStatus.WON)
                .dealStage(DealStage.CLOSED)
                .build();

        Deal updatedDeal = dealService.saveOrUpdateDealForCall(callA1.getId(), updateReq, principalA);

        assertEquals(deal.getId(), updatedDeal.getId()); // Same ID
        assertEquals(new BigDecimal("25000.50"), updatedDeal.getDealValue());
        assertEquals(DealStatus.WON, updatedDeal.getDealStatus());
        assertEquals(DealStage.CLOSED, updatedDeal.getDealStage());
    }

    @Test
    void test3_AttachDealToCall() {
        DealRequest request = DealRequest.builder()
                .dealValue(new BigDecimal("10000.00"))
                .dealStatus(DealStatus.OPEN)
                .dealStage(DealStage.DEMO)
                .build();

        Deal deal = dealService.saveOrUpdateDealForCall(callA1.getId(), request, principalA);

        CallRecord fetchedCall = callRecordRepository.findById(callA1.getId()).orElseThrow();
        assertEquals(deal.getId(), fetchedCall.getDeal().getId());
    }

    @Test
    void test4_RemoveDealFromCall() {
        DealRequest request = DealRequest.builder()
                .dealValue(new BigDecimal("10000.00"))
                .dealStatus(DealStatus.OPEN)
                .dealStage(DealStage.DEMO)
                .build();

        Deal deal = dealService.saveOrUpdateDealForCall(callA1.getId(), request, principalA);
        Long dealId = deal.getId();

        dealService.removeDealFromCall(callA1.getId(), principalA);

        CallRecord fetchedCall = callRecordRepository.findById(callA1.getId()).orElseThrow();
        assertNull(fetchedCall.getDeal());

        // Verify deal entity is removed from DB since it has 0 references left
        Optional<Deal> deletedDeal = dealRepository.findById(dealId);
        assertTrue(deletedDeal.isEmpty());
    }

    @Test
    void test5_FetchDeal() {
        DealRequest request = DealRequest.builder()
                .dealValue(new BigDecimal("8000.00"))
                .dealStatus(DealStatus.OPEN)
                .dealStage(DealStage.PROPOSAL)
                .build();

        Deal deal = dealService.saveOrUpdateDealForCall(callA1.getId(), request, principalA);

        Optional<Deal> fetched = dealRepository.findByIdAndCompanyId(deal.getId(), companyA.getId());
        assertTrue(fetched.isPresent());
        assertEquals(new BigDecimal("8000.00"), fetched.get().getDealValue());
    }

    @Test
    void test6_PipelineSummaryWithZeroDeals() {
        PipelineSummaryResponse summary = dealService.getPipelineSummary(principalA);

        assertEquals(BigDecimal.ZERO, summary.getPipelineCovered());
        assertEquals(BigDecimal.ZERO, summary.getClosedWon());
        assertEquals(0, summary.getOpenDealCount());
        assertEquals(0, summary.getWonDealCount());
        assertEquals(0, summary.getLostDealCount());
    }

    @Test
    void test7_PipelineSummaryWithOneOpenDeal() {
        DealRequest request = DealRequest.builder()
                .dealValue(new BigDecimal("50000.00"))
                .dealStatus(DealStatus.OPEN)
                .dealStage(DealStage.NEGOTIATION)
                .build();

        dealService.saveOrUpdateDealForCall(callA1.getId(), request, principalA);

        PipelineSummaryResponse summary = dealService.getPipelineSummary(principalA);

        assertEquals(new BigDecimal("50000.00"), summary.getPipelineCovered());
        assertEquals(BigDecimal.ZERO, summary.getClosedWon());
        assertEquals(1, summary.getOpenDealCount());
        assertEquals(0, summary.getWonDealCount());
        assertEquals(0, summary.getLostDealCount());
    }

    @Test
    void test8_PipelineSummaryWithMultipleOpenDeals() {
        DealRequest req1 = DealRequest.builder()
                .dealValue(new BigDecimal("30000.00"))
                .dealStatus(DealStatus.OPEN)
                .dealStage(DealStage.DEMO)
                .build();
        dealService.saveOrUpdateDealForCall(callA1.getId(), req1, principalA);

        DealRequest req2 = DealRequest.builder()
                .dealValue(new BigDecimal("45000.00"))
                .dealStatus(DealStatus.OPEN)
                .dealStage(DealStage.PROPOSAL)
                .build();
        dealService.saveOrUpdateDealForCall(callA2.getId(), req2, principalA);

        PipelineSummaryResponse summary = dealService.getPipelineSummary(principalA);

        assertEquals(new BigDecimal("75000.00"), summary.getPipelineCovered());
        assertEquals(BigDecimal.ZERO, summary.getClosedWon());
        assertEquals(2, summary.getOpenDealCount());
    }

    @Test
    void test9_TwoWonDealsAppearInClosedWon() {
        DealRequest wonReq1 = DealRequest.builder()
                .dealValue(new BigDecimal("99.00"))
                .dealStatus(DealStatus.WON)
                .dealStage(DealStage.CLOSED)
                .build();
        dealService.saveOrUpdateDealForCall(callA1.getId(), wonReq1, principalA);

        DealRequest wonReq2 = DealRequest.builder()
                .dealValue(new BigDecimal("40.00"))
                .dealStatus(DealStatus.WON)
                .dealStage(DealStage.CLOSED)
                .build();
        dealService.saveOrUpdateDealForCall(callA2.getId(), wonReq2, principalA);

        PipelineSummaryResponse summary = dealService.getPipelineSummary(principalA);

        assertEquals(BigDecimal.ZERO, summary.getPipelineCovered()); // Won is not open/covered
        assertEquals(new BigDecimal("139.00"), summary.getClosedWon());
        assertEquals(0, summary.getOpenDealCount());
        assertEquals(2, summary.getWonDealCount());
    }

    @Test
    void test10_LostDealDoesNotAppearInPipelineCovered() {
        DealRequest lostReq = DealRequest.builder()
                .dealValue(new BigDecimal("9000.00"))
                .dealStatus(DealStatus.LOST)
                .dealStage(DealStage.CLOSED)
                .build();
        dealService.saveOrUpdateDealForCall(callA1.getId(), lostReq, principalA);

        PipelineSummaryResponse summary = dealService.getPipelineSummary(principalA);

        assertEquals(BigDecimal.ZERO, summary.getPipelineCovered());
        assertEquals(BigDecimal.ZERO, summary.getClosedWon());
        assertEquals(new BigDecimal("9000.00"), summary.getLostDealValue());
        assertEquals(0, summary.getOpenDealCount());
        assertEquals(1, summary.getLostDealCount());
    }

    @Test
    void test15_OpenAndWonAndLostDealsCombination() {
        DealRequest openReq = DealRequest.builder()
                .dealValue(new BigDecimal("500.00"))
                .dealStatus(DealStatus.OPEN)
                .dealStage(DealStage.DEMO)
                .build();
        dealService.saveOrUpdateDealForCall(callA1.getId(), openReq, principalA);

        DealRequest wonReq = DealRequest.builder()
                .dealValue(new BigDecimal("139.00"))
                .dealStatus(DealStatus.WON)
                .dealStage(DealStage.CLOSED)
                .build();
        dealService.saveOrUpdateDealForCall(callA2.getId(), wonReq, principalA);

        CallRecord callA3 = CallRecord.builder()
                .fileName("call-a3.wav")
                .transcript("Lost call")
                .company(companyA)
                .user(userA)
                .build();
        callA3 = callRecordRepository.save(callA3);

        DealRequest lostReq = DealRequest.builder()
                .dealValue(new BigDecimal("200.00"))
                .dealStatus(DealStatus.LOST)
                .dealStage(DealStage.CLOSED)
                .build();
        dealService.saveOrUpdateDealForCall(callA3.getId(), lostReq, principalA);

        PipelineSummaryResponse summary = dealService.getPipelineSummary(principalA);

        assertEquals(new BigDecimal("500.00"), summary.getPipelineCovered());
        assertEquals(new BigDecimal("139.00"), summary.getClosedWon());
        assertEquals(new BigDecimal("200.00"), summary.getLostDealValue());
        assertEquals(1, summary.getOpenDealCount());
        assertEquals(1, summary.getWonDealCount());
        assertEquals(1, summary.getLostDealCount());
    }

    @Test
    void test11_MultipleCallsAttachedToSameDealDoNotDoubleCount() {
        DealRequest req = DealRequest.builder()
                .dealValue(new BigDecimal("60000.00"))
                .dealStatus(DealStatus.OPEN)
                .dealStage(DealStage.NEGOTIATION)
                .build();

        Deal deal = dealService.saveOrUpdateDealForCall(callA1.getId(), req, principalA);

        // Attach same deal to callA2
        callA2.setDeal(deal);
        callRecordRepository.save(callA2);

        PipelineSummaryResponse summary = dealService.getPipelineSummary(principalA);

        // Sum should be 60000, NOT 120000
        assertEquals(new BigDecimal("60000.00"), summary.getPipelineCovered());
        assertEquals(1, summary.getOpenDealCount());
    }

    @Test
    void test12_CompanyA_CannotAccessCompanyB_Deal() {
        DealRequest request = DealRequest.builder()
                .dealValue(new BigDecimal("20000.00"))
                .dealStatus(DealStatus.OPEN)
                .dealStage(DealStage.DISCOVERY)
                .build();

        // Save deal in Company A
        Deal dealA = dealService.saveOrUpdateDealForCall(callA1.getId(), request, principalA);

        // Try to fetch/modify using Company B principal
        Optional<Deal> fetchedByB = dealRepository.findByIdAndCompanyId(dealA.getId(), companyB.getId());
        assertTrue(fetchedByB.isEmpty());

        assertThrows(RuntimeException.class, () -> {
            dealService.saveOrUpdateDealForCall(callA1.getId(), request, principalB);
        });
    }

    @Test
    void test13_InvalidDealValueIsRejected() {
        DealRequest request = DealRequest.builder()
                .dealValue(new BigDecimal("-100.00"))
                .dealStatus(DealStatus.OPEN)
                .dealStage(DealStage.DISCOVERY)
                .build();

        assertThrows(IllegalArgumentException.class, () -> {
            dealService.saveOrUpdateDealForCall(callA1.getId(), request, principalA);
        });
    }

    @Test
    void test14_InvalidStatusOrStageIsRejected() {
        // Status is null
        DealRequest reqNullStatus = DealRequest.builder()
                .dealValue(new BigDecimal("100.00"))
                .dealStatus(null)
                .dealStage(DealStage.DISCOVERY)
                .build();

        assertThrows(IllegalArgumentException.class, () -> {
            dealService.saveOrUpdateDealForCall(callA1.getId(), reqNullStatus, principalA);
        });

        // Stage is null
        DealRequest reqNullStage = DealRequest.builder()
                .dealValue(new BigDecimal("100.00"))
                .dealStatus(DealStatus.OPEN)
                .dealStage(null)
                .build();

        assertThrows(IllegalArgumentException.class, () -> {
            dealService.saveOrUpdateDealForCall(callA1.getId(), reqNullStage, principalA);
        });
    }
}
