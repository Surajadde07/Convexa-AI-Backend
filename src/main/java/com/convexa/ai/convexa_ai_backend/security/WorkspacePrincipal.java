package com.convexa.ai.convexa_ai_backend.security;

import com.convexa.ai.convexa_ai_backend.entity.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Builder
public class WorkspacePrincipal {
    private final Long userId;
    private final Long companyId;
    private final Role role;
    private final String email;
}
