package com.convexa.ai.convexa_ai_backend.dto;

import lombok.*;

/**
 * Every field is nullable and optional. The controller/service only applies
 * fields that are non-null, so the frontend can PATCH a single toggle
 * (e.g. { "theme": "light" }) without needing to resend the whole object.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserSettingsUpdateRequest {
    private String theme;
    private Boolean notifCallReady;
    private Boolean notifNeedsAttention;
    private Boolean notifWeeklyDigest;
    private String defaultLandingPage;
    private String exportFormat;
    private Boolean shareAnonymizedData;
}
