package com.convexa.ai.convexa_ai_backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "organization_memberships", indexes = {
    @Index(name = "idx_membership_user_id", columnList = "user_id"),
    @Index(name = "idx_membership_company_id", columnList = "company_id"),
    @Index(name = "idx_membership_company_role", columnList = "company_id, role"),
    @Index(name = "idx_membership_company_status", columnList = "company_id, status")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uq_user_company", columnNames = {"user_id", "company_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrganizationMembership {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    private String department;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MembershipStatus status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime joinedAt;

    @Column(nullable = false)
    private LocalDateTime lastActivatedAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // Audit and lifecycle fields
    private Long createdBy;
    private Long removedBy;
    private LocalDateTime removedAt;

    @Version
    private Long version;

    @PrePersist
    public void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (this.joinedAt == null) {
            this.joinedAt = now;
        }
        if (this.lastActivatedAt == null) {
            this.lastActivatedAt = now;
        }
        this.updatedAt = now;
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
