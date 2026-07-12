package com.convexa.ai.convexa_ai_backend.controller;

import com.convexa.ai.convexa_ai_backend.dto.CompanyStatsResponse;
import com.convexa.ai.convexa_ai_backend.service.CompanyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Company Dashboard API (Sprint 2).
 *
 * No manual role check exists in this controller, and none is needed:
 * SecurityConfig already has
 *     .requestMatchers("/api/company/**").hasAnyRole("MANAGER", "ADMIN")
 * added in Sprint 1.5 specifically in anticipation of this controller. A
 * USER's request is rejected with 403 by Spring Security before the
 * dispatcher ever routes it here — that's the "USER should receive 403"
 * requirement, satisfied entirely by infrastructure that already existed.
 */
@RestController
@RequestMapping("/api/company")
@CrossOrigin("*")
public class CompanyController {

    @Autowired
    private CompanyService companyService;

    /**
     * @param range optional "7d" | "30d" | "all" (default "7d") — controls
     *              only the call-volume chart series; Top Performers and
     *              Needs Coaching are always the trailing 30 days per spec.
     */
    @GetMapping("/stats")
    public ResponseEntity<CompanyStatsResponse> getCompanyStats(
            @RequestParam(value = "range", required = false, defaultValue = "7d") String range
    ) {
        return ResponseEntity.ok(companyService.getCompanyStats(range));
    }
}
