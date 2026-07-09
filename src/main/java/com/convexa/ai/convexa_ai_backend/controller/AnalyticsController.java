package com.convexa.ai.convexa_ai_backend.controller;

import com.convexa.ai.convexa_ai_backend.dto.AnalyticsResponse;
import com.convexa.ai.convexa_ai_backend.entity.User;
import com.convexa.ai.convexa_ai_backend.repository.UserRepository;
import com.convexa.ai.convexa_ai_backend.service.AnalyticsService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Dedicated analytics API. Returns the trend/series/distribution data
 * AnalyticsPage needs, computed once server-side instead of being
 * re-derived client-side from the same /api/calls/my-calls payload the
 * Dashboard already fetches.
 */
@RestController
@RequestMapping("/api/analytics")
@CrossOrigin("*")
public class AnalyticsController {

    @Autowired
    private AnalyticsService analyticsService;

    @Autowired
    private UserRepository userRepository;

    /**
     * @param range optional "7d" | "30d" | "all" (default "all")
     */
    @GetMapping("/employee")
    public ResponseEntity<AnalyticsResponse> getEmployeeAnalytics(
            @RequestParam(value = "range", required = false, defaultValue = "all") String range,
            HttpServletRequest request
    ) {
        String email = (String) request.getAttribute("userEmail");

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return ResponseEntity.ok(analyticsService.getAnalytics(user.getId(), range));
    }
}
