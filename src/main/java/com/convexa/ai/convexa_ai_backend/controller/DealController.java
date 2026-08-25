package com.convexa.ai.convexa_ai_backend.controller;

import com.convexa.ai.convexa_ai_backend.dto.DealRequest;
import com.convexa.ai.convexa_ai_backend.dto.PipelineIntelligenceResponse;
import com.convexa.ai.convexa_ai_backend.dto.PipelineSummaryResponse;
import com.convexa.ai.convexa_ai_backend.dto.RevenueTargetRequest;
import com.convexa.ai.convexa_ai_backend.entity.Company;
import com.convexa.ai.convexa_ai_backend.entity.Deal;
import com.convexa.ai.convexa_ai_backend.security.WorkspacePrincipal;
import com.convexa.ai.convexa_ai_backend.service.DealService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping
public class DealController {

    @Autowired
    private DealService dealService;

    @PostMapping("/api/calls/{callId}/deal")
    public ResponseEntity<?> saveOrUpdateDeal(
            @PathVariable Long callId,
            @Valid @RequestBody DealRequest request,
            @AuthenticationPrincipal WorkspacePrincipal principal
    ) {
        if (principal == null) {
            return ResponseEntity.status(401).body("Unauthorized");
        }
        try {
            Deal deal = dealService.saveOrUpdateDealForCall(callId, request, principal);
            return ResponseEntity.ok(deal);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    @DeleteMapping("/api/calls/{callId}/deal")
    public ResponseEntity<?> removeDeal(
            @PathVariable Long callId,
            @AuthenticationPrincipal WorkspacePrincipal principal
    ) {
        if (principal == null) {
            return ResponseEntity.status(401).body("Unauthorized");
        }
        try {
            dealService.removeDealFromCall(callId, principal);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    @GetMapping("/api/company/pipeline-summary")
    public ResponseEntity<?> getPipelineSummary(
            @AuthenticationPrincipal WorkspacePrincipal principal
    ) {
        if (principal == null) {
            return ResponseEntity.status(401).body("Unauthorized");
        }
        try {
            PipelineSummaryResponse summary = dealService.getPipelineSummary(principal);
            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    /**
     * GET /api/company/pipeline-intelligence
     *
     * Phase 1 Revenue Intelligence API:
     * - Accepts optional range param ("this_quarter", "this_month", "last_quarter", "30d", "7d", "all")
     * - Returns Current State snapshot + Period-filtered closed metrics
     * - Includes Health-Weighted Pipeline, At-Risk Deals, and Revenue-Connected Signals
     */
    @GetMapping("/api/company/pipeline-intelligence")
    public ResponseEntity<?> getPipelineIntelligence(
            @RequestParam(value = "range", required = false, defaultValue = "this_quarter") String range,
            @AuthenticationPrincipal WorkspacePrincipal principal
    ) {
        if (principal == null) {
            return ResponseEntity.status(401).body("Unauthorized");
        }
        try {
            PipelineIntelligenceResponse intelligence = dealService.getPipelineIntelligence(principal, range);
            return ResponseEntity.ok(intelligence);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    /**
     * PATCH /api/company/revenue-target
     *
     * Sets or updates the company revenue target (Quarterly or Monthly).
     * Restricted to OWNER and ADMIN roles.
     */
    @PatchMapping("/api/company/revenue-target")
    public ResponseEntity<?> updateRevenueTarget(
            @Valid @RequestBody RevenueTargetRequest request,
            @AuthenticationPrincipal WorkspacePrincipal principal
    ) {
        if (principal == null) {
            return ResponseEntity.status(401).body("Unauthorized");
        }
        try {
            Company company = dealService.updateRevenueTarget(request, principal);
            return ResponseEntity.ok(Map.of(
                    "message", "Revenue target updated successfully",
                    "quarterlyRevenueTarget", company.getQuarterlyRevenueTarget() != null ? company.getQuarterlyRevenueTarget() : 0,
                    "monthlyRevenueTarget", company.getMonthlyRevenueTarget() != null ? company.getMonthlyRevenueTarget() : 0,
                    "revenueTargetPeriod", company.getRevenueTargetPeriod() != null ? company.getRevenueTargetPeriod() : "QUARTERLY"
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("Only workspace owners")) {
                return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
            }
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
}
