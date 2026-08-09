package com.convexa.ai.convexa_ai_backend.controller;

import com.convexa.ai.convexa_ai_backend.dto.DealRequest;
import com.convexa.ai.convexa_ai_backend.dto.PipelineSummaryResponse;
import com.convexa.ai.convexa_ai_backend.entity.Deal;
import com.convexa.ai.convexa_ai_backend.security.WorkspacePrincipal;
import com.convexa.ai.convexa_ai_backend.service.DealService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

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
}
