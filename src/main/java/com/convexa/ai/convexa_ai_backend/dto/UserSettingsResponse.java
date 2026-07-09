package com.convexa.ai.convexa_ai_backend.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSettingsResponse {
    private String theme;
    private Boolean notifCallReady;
    private Boolean notifNeedsAttention;
    private Boolean notifWeeklyDigest;
    private String defaultLandingPage;
    private String exportFormat;
    private Boolean shareAnonymizedData;
}
