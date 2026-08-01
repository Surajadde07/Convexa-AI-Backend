package com.convexa.ai.convexa_ai_backend.controller;

import com.convexa.ai.convexa_ai_backend.dto.ChangePasswordRequest;
import com.convexa.ai.convexa_ai_backend.dto.UpdateProfileRequest;
import com.convexa.ai.convexa_ai_backend.entity.User;
import com.convexa.ai.convexa_ai_backend.repository.UserRepository;
import com.convexa.ai.convexa_ai_backend.service.AccountService;
import com.convexa.ai.convexa_ai_backend.service.JwtService;
import com.convexa.ai.convexa_ai_backend.security.WorkspacePrincipal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Profile, Security, and Danger Zone from the Settings page.
 */
@RestController
@CrossOrigin("*")
public class AccountController {

    @Autowired
    private AccountService accountService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    private User currentUser(WorkspacePrincipal principal) {
        if (principal == null) {
            throw new RuntimeException("Unauthorized");
        }
        return userRepository.findById(principal.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    @GetMapping("/api/users/me")
    public ResponseEntity<?> getProfile(@AuthenticationPrincipal WorkspacePrincipal principal) {
        User user = currentUser(principal);
        return ResponseEntity.ok(Map.of(
                "name", user.getName(),
                "email", user.getEmail(),
                "provider", user.getProvider() != null ? user.getProvider() : "LOCAL"
        ));
    }

    @PatchMapping("/api/users/me")
    public ResponseEntity<?> updateProfile(
            @RequestBody UpdateProfileRequest req,
            @AuthenticationPrincipal WorkspacePrincipal principal
    ) {
        try {
            User current = currentUser(principal);
            User updated = accountService.updateProfile(current, req);
            String refreshedToken = jwtService.generateToken(updated.getEmail(), updated.getId());
            return ResponseEntity.ok(Map.of(
                    "name", updated.getName(),
                    "email", updated.getEmail(),
                    "token", refreshedToken
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/api/account/change-password")
    public ResponseEntity<?> changePassword(
            @RequestBody ChangePasswordRequest req,
            @AuthenticationPrincipal WorkspacePrincipal principal
    ) {
        try {
            accountService.changePassword(currentUser(principal), req);
            return ResponseEntity.ok(Map.of("message", "Password changed successfully."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/api/account")
    public ResponseEntity<?> deleteAccount(@AuthenticationPrincipal WorkspacePrincipal principal) {
        accountService.deleteAccount(currentUser(principal));
        return ResponseEntity.ok(Map.of("message", "Account deleted."));
    }

    @GetMapping("/api/users/me/export")
    public ResponseEntity<?> exportAccountData(@AuthenticationPrincipal WorkspacePrincipal principal) {
        return ResponseEntity.ok(accountService.exportAccountData(currentUser(principal)));
    }
}
