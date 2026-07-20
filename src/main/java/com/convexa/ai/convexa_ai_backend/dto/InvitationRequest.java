package com.convexa.ai.convexa_ai_backend.dto;

import lombok.*;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvitationRequest {
    @NotBlank(message = "Email is required")
    private String email;

    @NotNull(message = "Role is required")
    private String role;

    private String department;
}
