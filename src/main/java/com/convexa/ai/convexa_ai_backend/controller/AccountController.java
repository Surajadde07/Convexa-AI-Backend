package com.convexa.ai.convexa_ai_backend.controller;

import com.convexa.ai.convexa_ai_backend.dto.ChangePasswordRequest;
import com.convexa.ai.convexa_ai_backend.dto.UpdateProfileRequest;
import com.convexa.ai.convexa_ai_backend.entity.User;
import com.convexa.ai.convexa_ai_backend.repository.UserRepository;
import com.convexa.ai.convexa_ai_backend.service.AccountService;
import com.convexa.ai.convexa_ai_backend.service.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Profile, Security, and Danger Zone from the Settings page.
 * Same JWT-attribute auth pattern as every other controller in this app —
 * every operation is scoped to request.getAttribute("userEmail"), so one
 * account can never read or modify another's data.
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

    private User currentUser(HttpServletRequest request) {
        String email = (String) request.getAttribute("userEmail");
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    @GetMapping("/api/users/me")
    public ResponseEntity<?> getProfile(HttpServletRequest request) {
        User user = currentUser(request);
        return ResponseEntity.ok(Map.of(
                "name", user.getName(),
                "email", user.getEmail(),
                "provider", user.getProvider() != null ? user.getProvider() : "LOCAL"
        ));
    }

    @PatchMapping("/api/users/me")
    public ResponseEntity<?> updateProfile(@RequestBody UpdateProfileRequest req, HttpServletRequest request) {
        try {
            User updated = accountService.updateProfile(currentUser(request), req);
            // Root cause of "works once, then fails": the JWT's subject is the
            // email. If email just changed, the old token now points at an
            // email that no longer exists, and every subsequent request's
            // currentUser() lookup fails. Reissuing here keeps the token in
            // sync with the DB on every save, not just ones that change email.
            String refreshedToken = jwtService.generateToken(updated.getEmail());
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
    public ResponseEntity<?> changePassword(@RequestBody ChangePasswordRequest req, HttpServletRequest request) {
        try {
            accountService.changePassword(currentUser(request), req);
            return ResponseEntity.ok(Map.of("message", "Password changed successfully."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/api/account")
    public ResponseEntity<?> deleteAccount(HttpServletRequest request) {
        accountService.deleteAccount(currentUser(request));
        return ResponseEntity.ok(Map.of("message", "Account deleted."));
    }

    @GetMapping("/api/users/me/export")
    public ResponseEntity<?> exportAccountData(HttpServletRequest request) {
        return ResponseEntity.ok(accountService.exportAccountData(currentUser(request)));
    }
}
