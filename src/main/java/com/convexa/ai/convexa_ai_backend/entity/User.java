package com.convexa.ai.convexa_ai_backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_user_company_id", columnList = "company_id"),
    @Index(name = "idx_user_email", columnList = "email", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(
            unique = true,
            nullable = false
    )
    private String email;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    private Role role;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "company_id")
    private Company company;

    private String department;

    // "LOCAL" (email/password signup) or "GOOGLE" (OAuth signup). Set at
    // creation in UserService.register() / GoogleAuthService, defaulted
    // here the same way `role` already is, for any row created without it
    // explicitly set. Existing rows created before this column existed will
    // read as NULL, not "LOCAL" — see AccountService.changePassword, which
    // treats null as "has a real password" (safe for existing local users;
    // any Google account created before this fix will need a one-time
    // manual `UPDATE users SET provider = 'GOOGLE' WHERE ...` if you want
    // it correctly gated too).
    private String provider;

    private LocalDateTime createdAt;

    @PrePersist
    public void onCreate() {

        this.createdAt =
                LocalDateTime.now();

        if (this.role == null) {

            this.role = Role.USER;
        }

        if (this.provider == null) {

            this.provider = "LOCAL";
        }
    }
}
