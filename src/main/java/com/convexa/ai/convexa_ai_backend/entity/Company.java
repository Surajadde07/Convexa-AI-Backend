package com.convexa.ai.convexa_ai_backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "companies", indexes = {
    @Index(name = "idx_company_slug", columnList = "company_slug", unique = true),
    @Index(name = "idx_company_billing_email", columnList = "billingEmail")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String companyName;

    @Column(name = "company_slug", unique = true, nullable = false, updatable = false)
    private String companySlug;

    private String companyLogo;
    private String industry;
    private String companySize;
    private String website;

    private String billingEmail;
    private String timezone;
    private String status;

    @Builder.Default
    private Boolean onboardingCompleted = false;

    @Builder.Default
    private Integer profileCompletionPercentage = 0;

    private String brandPrimaryColor;
    private String brandSecondaryColor;

    @OneToOne(mappedBy = "company", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Subscription subscription;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    public void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.onboardingCompleted == null) {
            this.onboardingCompleted = false;
        }
        if (this.profileCompletionPercentage == null) {
            this.profileCompletionPercentage = 0;
        }
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
