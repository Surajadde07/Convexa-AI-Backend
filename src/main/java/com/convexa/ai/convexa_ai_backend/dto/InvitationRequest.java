package com.convexa.ai.convexa_ai_backend.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvitationRequest {
    private String email;
    private String role;
    private String department;
}
