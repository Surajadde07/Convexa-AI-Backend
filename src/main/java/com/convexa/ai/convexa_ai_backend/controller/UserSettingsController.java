package com.convexa.ai.convexa_ai_backend.controller;

import com.convexa.ai.convexa_ai_backend.dto.UserSettingsResponse;
import com.convexa.ai.convexa_ai_backend.dto.UserSettingsUpdateRequest;
import com.convexa.ai.convexa_ai_backend.entity.User;
import com.convexa.ai.convexa_ai_backend.repository.UserRepository;
import com.convexa.ai.convexa_ai_backend.service.UserSettingsService;
import com.convexa.ai.convexa_ai_backend.security.WorkspacePrincipal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/settings")
@CrossOrigin("*")
public class UserSettingsController {

    @Autowired
    private UserSettingsService userSettingsService;

    @Autowired
    private UserRepository userRepository;

    private User currentUser(WorkspacePrincipal principal) {
        if (principal == null) {
            throw new RuntimeException("Unauthorized");
        }
        return userRepository.findById(principal.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    @GetMapping("/me")
    public ResponseEntity<UserSettingsResponse> getMySettings(@AuthenticationPrincipal WorkspacePrincipal principal) {
        return ResponseEntity.ok(userSettingsService.getSettings(currentUser(principal)));
    }

    @PatchMapping("/me")
    public ResponseEntity<?> updateMySettings(
            @RequestBody UserSettingsUpdateRequest req,
            @AuthenticationPrincipal WorkspacePrincipal principal
    ) {
        try {
            return ResponseEntity.ok(userSettingsService.updateSettings(currentUser(principal), req));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("message", e.getMessage()));
        }
    }
}
