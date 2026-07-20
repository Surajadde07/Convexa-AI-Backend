package com.convexa.ai.convexa_ai_backend.entity;

/**
 * Role set for RBAC including OWNER.
 * Stored via @Enumerated(EnumType.STRING) on User.role.
 */
public enum Role {
    OWNER,
    ADMIN,
    MANAGER,
    USER
}
