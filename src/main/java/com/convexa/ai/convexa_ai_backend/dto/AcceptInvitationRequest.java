package com.convexa.ai.convexa_ai_backend.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AcceptInvitationRequest {
    private String token;
    private String name;
    private String password;
}
