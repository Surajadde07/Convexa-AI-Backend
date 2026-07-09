package com.convexa.ai.convexa_ai_backend.service;

import com.convexa.ai.convexa_ai_backend.dto.AuthResponse;
import com.convexa.ai.convexa_ai_backend.dto.GoogleAuthRequest;
import com.convexa.ai.convexa_ai_backend.entity.User;
import com.convexa.ai.convexa_ai_backend.repository.UserRepository;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

/**
 * GoogleAuthService
 *
 * Verifies a Google ID token server-side using the Google API Client Library.
 * Never trust the email or name sent from the frontend — always verify the
 * raw credential token here before creating or logging in the user.
 *
 * Flow:
 *   1. Frontend: google.accounts.id → callback({ credential })
 *   2. Frontend: POST /api/auth/google  { credential: "<id_token>" }
 *   3. Here: GoogleIdTokenVerifier.verify(credential) → GoogleIdToken
 *   4. Extract email, name, googleId (sub) from payload
 *   5. If user exists by email → log in
 *      If user doesn't exist → create account with random password
 *   6. Generate Convexa JWT and return AuthResponse
 *
 * Maven dependency to add to pom.xml:
 *   <dependency>
 *     <groupId>com.google.api-client</groupId>
 *     <artifactId>google-api-client</artifactId>
 *     <version>2.2.0</version>
 *   </dependency>
 */
@Service
public class GoogleAuthService {

    @Value("${google.client.id}")
    private String googleClientId;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder passwordEncoder;

    public AuthResponse authenticateWithGoogle(GoogleAuthRequest request) {

        if (request.getCredential() == null || request.getCredential().isBlank()) {
            throw new RuntimeException("Missing Google credential in request");
        }

        // ── 1. Verify the Google ID token ─────────────────────────────────────
        GoogleIdToken idToken = verifyToken(request.getCredential());

        if (idToken == null) {
            // Root-cause note: the most common reason verify() returns null
            // is an audience (aud) mismatch — google.client.id in
            // application.properties does not exactly match the client_id
            // the frontend used to obtain the credential. Double-check both
            // values character-for-character (including no trailing
            // whitespace) if this exception appears.
            throw new RuntimeException(
                "Invalid Google token — verification failed. " +
                "Check that google.client.id in application.properties exactly " +
                "matches VITE_GOOGLE_CLIENT_ID used by the frontend."
            );
        }

        GoogleIdToken.Payload payload = idToken.getPayload();

        String email    = payload.getEmail();
        String name     = (String) payload.get("name");
        String googleId = payload.getSubject();   // unique Google user ID

        if (name == null || name.isBlank()) {
            name = email.split("@")[0];   // fallback: use email prefix as name
        }

        // ── 2. Find or create user ────────────────────────────────────────────
        Optional<User> existing = userRepository.findByEmail(email);

        User user;

        if (existing.isPresent()) {
            // Existing user — just log them in regardless of how they registered
            user = existing.get();
        } else {
            // New Google user — auto-create account
            // A random secure password is set so the account is unusable via
            // password login (the user must always come through Google OAuth).
            user = User.builder()
                    .email(email)
                    .name(name)
                    .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                    .role("USER")
                    .provider("GOOGLE")
                    .build();
            userRepository.save(user);
        }

        // ── 3. Generate Convexa JWT ───────────────────────────────────────────
        String token = jwtService.generateToken(user.getEmail());

        return AuthResponse.builder()
                .token(token)
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole())
                .build();
    }

    // ── Token verifier ────────────────────────────────────────────────────────

    private GoogleIdToken verifyToken(String credential) {
        try {
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                    new NetHttpTransport(),
                    GsonFactory.getDefaultInstance()
            )
                    .setAudience(Collections.singletonList(googleClientId))
                    .build();

            return verifier.verify(credential);

        } catch (Exception e) {
            // Previously this caught the exception and returned null with
            // no logging — meaning every verification failure looked
            // identical ("Invalid Google token") regardless of the actual
            // cause (expired token, audience mismatch, malformed JWT,
            // network error reaching Google's certs endpoint, etc).
            // Logging here makes the real cause visible in server logs.
            System.err.println("[GoogleAuthService] Token verification failed: "
                    + e.getClass().getSimpleName() + " — " + e.getMessage());
            return null;
        }
    }
}
