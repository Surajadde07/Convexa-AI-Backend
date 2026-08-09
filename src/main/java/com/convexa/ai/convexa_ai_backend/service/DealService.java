package com.convexa.ai.convexa_ai_backend.service;

import com.convexa.ai.convexa_ai_backend.dto.DealRequest;
import com.convexa.ai.convexa_ai_backend.dto.PipelineSummaryResponse;
import com.convexa.ai.convexa_ai_backend.entity.*;
import com.convexa.ai.convexa_ai_backend.repository.CallRecordRepository;
import com.convexa.ai.convexa_ai_backend.repository.CompanyRepository;
import com.convexa.ai.convexa_ai_backend.repository.DealRepository;
import com.convexa.ai.convexa_ai_backend.repository.UserRepository;
import com.convexa.ai.convexa_ai_backend.security.WorkspacePrincipal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

@Service
@Transactional
public class DealService {

    @Autowired
    private DealRepository dealRepository;

    @Autowired
    private CallRecordRepository callRecordRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private UserRepository userRepository;

    /**
     * Creates or updates a deal and associates it with the specified call record.
     */
    public Deal saveOrUpdateDealForCall(Long callId, DealRequest request, WorkspacePrincipal principal) {
        if (request.getDealValue() == null || request.getDealValue().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Deal value must be non-negative");
        }
        if (request.getDealStatus() == null) {
            throw new IllegalArgumentException("Deal status is required");
        }
        if (request.getDealStage() == null) {
            throw new IllegalArgumentException("Deal stage is required");
        }

        CallRecord callRecord = callRecordRepository.findByIdAndCompanyId(callId, principal.getCompanyId())
                .orElseThrow(() -> new RuntimeException("Call record not found in this workspace"));

        // If user is a regular USER role, restrict modifications to their own calls
        if (principal.getRole() == Role.USER) {
            if (callRecord.getUser() == null || !callRecord.getUser().getId().equals(principal.getUserId())) {
                throw new RuntimeException("You do not have permission to attach a deal to this call");
            }
        }

        Deal deal = callRecord.getDeal();
        if (deal == null) {
            Company company = companyRepository.findById(principal.getCompanyId())
                    .orElseThrow(() -> new RuntimeException("Company not found"));
            User creator = userRepository.findById(principal.getUserId())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            deal = Deal.builder()
                    .company(company)
                    .createdBy(creator)
                    .dealValue(request.getDealValue())
                    .dealStatus(request.getDealStatus())
                    .dealStage(request.getDealStage())
                    .build();

            deal = dealRepository.save(deal);
            callRecord.setDeal(deal);
            callRecordRepository.save(callRecord);
        } else {
            // Update existing deal
            deal.setDealValue(request.getDealValue());
            deal.setDealStatus(request.getDealStatus());
            deal.setDealStage(request.getDealStage());
            deal = dealRepository.save(deal);
        }

        return deal;
    }

    /**
     * Detaches the deal from the specified call record and removes the deal from database if orphaned.
     */
    public void removeDealFromCall(Long callId, WorkspacePrincipal principal) {
        CallRecord callRecord = callRecordRepository.findByIdAndCompanyId(callId, principal.getCompanyId())
                .orElseThrow(() -> new RuntimeException("Call record not found in this workspace"));

        // Scoping check for USER role
        if (principal.getRole() == Role.USER) {
            if (callRecord.getUser() == null || !callRecord.getUser().getId().equals(principal.getUserId())) {
                throw new RuntimeException("You do not have permission to modify this call");
            }
        }

        Deal deal = callRecord.getDeal();
        if (deal != null) {
            callRecord.setDeal(null);
            callRecordRepository.save(callRecord);

            // Check if this deal is referenced by other calls in the company
            long refCount = callRecordRepository.findAll().stream()
                    .filter(c -> c.getDeal() != null && c.getDeal().getId().equals(deal.getId()))
                    .count();

            if (refCount == 0) {
                dealRepository.delete(deal);
            }
        }
    }

    /**
     * Fetches pipeline summary statistics securely for the workspace.
     */
    @Transactional(readOnly = true)
    public PipelineSummaryResponse getPipelineSummary(WorkspacePrincipal principal) {
        Long companyId = principal.getCompanyId();

        BigDecimal pipelineCovered = dealRepository.sumPipelineCoveredByCompanyId(companyId);
        BigDecimal closedWon = dealRepository.sumClosedWonByCompanyId(companyId);
        BigDecimal lostDealValue = dealRepository.sumLostDealValueByCompanyId(companyId);

        long openCount = dealRepository.countCoveredDealsByCompanyIdAndStatus(companyId, DealStatus.OPEN);
        long wonCount = dealRepository.countCoveredDealsByCompanyIdAndStatus(companyId, DealStatus.WON);
        long lostCount = dealRepository.countCoveredDealsByCompanyIdAndStatus(companyId, DealStatus.LOST);

        return PipelineSummaryResponse.builder()
                .pipelineCovered(pipelineCovered)
                .closedWon(closedWon)
                .lostDealValue(lostDealValue)
                .openDealCount(openCount)
                .wonDealCount(wonCount)
                .lostDealCount(lostCount)
                .build();
    }
}
