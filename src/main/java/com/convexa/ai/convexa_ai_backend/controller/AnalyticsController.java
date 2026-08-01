package com.convexa.ai.convexa_ai_backend.controller;

import com.convexa.ai.convexa_ai_backend.dto.AnalyticsResponse;
import com.convexa.ai.convexa_ai_backend.security.WorkspacePrincipal;
import com.convexa.ai.convexa_ai_backend.service.AnalyticsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/analytics")
@CrossOrigin("*")
public class AnalyticsController {

    @Autowired
    private AnalyticsService analyticsService;

    @GetMapping("/employee")
    public ResponseEntity<AnalyticsResponse> getEmployeeAnalytics(
            @RequestParam(value = "range", required = false, defaultValue = "all") String range,
            @RequestParam(value = "employeeId", required = false) Long employeeId,
            @AuthenticationPrincipal WorkspacePrincipal principal
    ) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }

        Long scopedToUserId = null;
        if (principal.getRole() == com.convexa.ai.convexa_ai_backend.entity.Role.USER) {
            scopedToUserId = principal.getUserId();
        } else if (employeeId != null) {
            scopedToUserId = employeeId;
        }

        return ResponseEntity.ok(analyticsService.getAnalytics(principal.getCompanyId(), principal.getUserId(), range, scopedToUserId));
    }
}
