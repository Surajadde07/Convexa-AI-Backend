package com.convexa.ai.convexa_ai_backend.controller;

import com.convexa.ai.convexa_ai_backend.dto.UserSettingsResponse;
import com.convexa.ai.convexa_ai_backend.dto.UserSettingsUpdateRequest;
import com.convexa.ai.convexa_ai_backend.entity.User;
import com.convexa.ai.convexa_ai_backend.repository.UserRepository;
import com.convexa.ai.convexa_ai_backend.service.UserSettingsService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Backs theme, notifications, preferences, and privacy — everything on the
 * Settings page except Profile/Security/Danger Zone (see AccountController).
 *
 * Auth: identical pattern to AnalyticsController/DashboardController — the
 * JWT filter puts the authenticated email on the request as "userEmail";
 * every operation here is scoped to that user only, so there's no way for
 * one account to read or modify another's settings.
 */
@RestController
@RequestMapping("/api/settings")
@CrossOrigin("*")
public class UserSettingsController {

    @Autowired
    private UserSettingsService userSettingsService;

    @Autowired
    private UserRepository userRepository;

    private User currentUser(HttpServletRequest request) {
        String email = (String) request.getAttribute("userEmail");
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    @GetMapping("/me")
    public ResponseEntity<UserSettingsResponse> getMySettings(HttpServletRequest request) {
        return ResponseEntity.ok(userSettingsService.getSettings(currentUser(request)));
    }

    @PatchMapping("/me")
    public ResponseEntity<?> updateMySettings(
            @RequestBody UserSettingsUpdateRequest req,
            HttpServletRequest request
    ) {
        try {
            return ResponseEntity.ok(userSettingsService.updateSettings(currentUser(request), req));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("message", e.getMessage()));
        }
    }
}
