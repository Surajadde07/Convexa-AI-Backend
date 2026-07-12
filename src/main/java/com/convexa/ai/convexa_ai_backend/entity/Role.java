package com.convexa.ai.convexa_ai_backend.entity;

/**
 * Phase 1 role set for RBAC. Stored via @Enumerated(EnumType.STRING) on
 * User.role, so the DB column holds the literal strings "USER", "MANAGER",
 * "ADMIN" — identical to what was already stored as a plain String, which
 * is why this change needs no data migration.
 */
public enum Role {
    USER,
    MANAGER,
    ADMIN
}
