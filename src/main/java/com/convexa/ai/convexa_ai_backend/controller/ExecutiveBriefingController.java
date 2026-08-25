package com.convexa.ai.convexa_ai_backend.controller;

import com.convexa.ai.convexa_ai_backend.dto.ExecutiveBriefingResponse;
import com.convexa.ai.convexa_ai_backend.security.WorkspacePrincipal;
import com.convexa.ai.convexa_ai_backend.service.ExecutiveBriefingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Controller providing GET /api/company/executive-briefing
 * Gated under /api/company/** in SecurityConfig for OWNER, MANAGER, ADMIN roles.
 */
@RestController
@RequestMapping("/api/company")
@CrossOrigin("*")
public class ExecutiveBriefingController {

    @Autowired
    private ExecutiveBriefingService executiveBriefingService;

    @GetMapping("/executive-briefing")
    public ResponseEntity<ExecutiveBriefingResponse> getExecutiveBriefing(
            @RequestParam(value = "range", required = false, defaultValue = "30d") String range,
            @AuthenticationPrincipal WorkspacePrincipal principal
    ) {
        if (principal == null || principal.getCompanyId() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        ExecutiveBriefingResponse response = executiveBriefingService.getExecutiveBriefing(principal.getCompanyId(), range);
        return ResponseEntity.ok(response);
    }
}
