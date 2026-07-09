package com.convexa.ai.convexa_ai_backend.controller;

import com.convexa.ai.convexa_ai_backend.dto.DashboardStatsResponse;
import com.convexa.ai.convexa_ai_backend.entity.User;
import com.convexa.ai.convexa_ai_backend.repository.UserRepository;
import com.convexa.ai.convexa_ai_backend.service.DashboardService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Dedicated dashboard API. Returns only the aggregate KPIs the Dashboard's
 * "command center" widgets need (KPI row, needs-attention list, weakest
 * dimension, recommendations, briefing) — computed once, server-side.
 *
 * This does NOT replace GET /api/calls/my-calls, which the Dashboard still
 * needs for the interactive recent-calls list, search, and the client-side
 * filter popover. This endpoint only takes over the numbers that used to be
 * re-derived from that same list on every render.
 */
@RestController
@RequestMapping("/api/dashboard")
@CrossOrigin("*")
public class DashboardController {

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private UserRepository userRepository;

    /**
     * @param range optional "7d" | "30d" | "all" (default "all"), matching
     *              the same date-range selector already used on the dashboard.
     */
    @GetMapping("/employee")
    public ResponseEntity<DashboardStatsResponse> getEmployeeDashboard(
            @RequestParam(value = "range", required = false, defaultValue = "all") String range,
            HttpServletRequest request
    ) {
        String email = (String) request.getAttribute("userEmail");

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return ResponseEntity.ok(dashboardService.getStats(user.getId(), range));
    }
}
