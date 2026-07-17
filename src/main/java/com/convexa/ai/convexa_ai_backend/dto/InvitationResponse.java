package com.convexa.ai.convexa_ai_backend.dto;

import com.convexa.ai.convexa_ai_backend.entity.InvitationStatus;
import lombok.*;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvitationResponse {
    private Long id;
    private String email;
    private String role;
    private String department;
    private String invitedBy;
    private String token;
    private InvitationStatus status;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private boolean userExists;
}
