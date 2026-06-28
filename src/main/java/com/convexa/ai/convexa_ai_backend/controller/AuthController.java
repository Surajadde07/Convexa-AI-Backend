package com.convexa.ai.convexa_ai_backend.controller;

import com.convexa.ai.convexa_ai_backend.dto.AuthResponse;
import com.convexa.ai.convexa_ai_backend.dto.GoogleAuthRequest;
import com.convexa.ai.convexa_ai_backend.dto.LoginRequest;
import com.convexa.ai.convexa_ai_backend.dto.RegisterRequest;
import com.convexa.ai.convexa_ai_backend.service.GoogleAuthService;
import com.convexa.ai.convexa_ai_backend.service.UserService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private UserService userService;

    @Autowired
    private GoogleAuthService googleAuthService;

    // =========================
    // REGISTER
    // =========================

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @RequestBody RegisterRequest request
    ) {

        AuthResponse response =
                userService.register(request);

        return ResponseEntity.ok(response);
    }

    // =========================
    // LOGIN
    // =========================

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @RequestBody LoginRequest request
    ) {

        AuthResponse response =
                userService.login(request);

        return ResponseEntity.ok(response);
    }

    // =========================
    // GOOGLE OAUTH
    // =========================

    /**
     * Accepts a raw Google ID token (credential) from the frontend,
     * verifies it server-side, and returns a Convexa JWT.
     * A new user account is created automatically on first sign-in.
     *
     * Errors from GoogleAuthService (e.g. audience mismatch, expired token)
     * are caught here and returned as a 401 with the actual message, instead
     * of bubbling up as an unhandled 500 with no body — which previously
     * made every failure look identical on the frontend ("Google sign-in
     * failed") regardless of the real cause.
     */
    @PostMapping("/google")
    public ResponseEntity<?> googleAuth(
            @RequestBody GoogleAuthRequest request
    ) {
        try {
            AuthResponse response =
                    googleAuthService.authenticateWithGoogle(request);

            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            return ResponseEntity
                    .status(401)
                    .body(java.util.Map.of("message", e.getMessage()));
        }
    }
}