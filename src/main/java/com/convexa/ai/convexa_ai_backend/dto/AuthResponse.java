package com.convexa.ai.convexa_ai_backend.dto;

import lombok.*;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AuthResponse {

    private Long id;
    private String name;
    private String email;
    private String role;
    private String token;
    private String message;

    private String companyName;
    private String companySlug;
    private String companyLogo;
    private String department;
    private String managerName;

    // Subscription & Branding metadata
    private String subscriptionPlan;
    private String subscriptionStatus;
    private Integer seatLimit;
    private Integer currentSeatCount;
    private String trialEndsAt;

    private Boolean onboardingCompleted;
    private Integer profileCompletionPercentage;

    private String brandPrimaryColor;
    private String brandSecondaryColor;

    /**
     * True when the user authenticated successfully but currently has no company
     * (company_id = null — account was removed from its workspace).
     * Frontend should redirect to the /no-workspace page instead of the dashboard.
     */
    private Boolean noWorkspace;
}