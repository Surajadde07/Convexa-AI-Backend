package com.convexa.ai.convexa_ai_backend.dto;

import lombok.*;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AuthResponse {

    private Long id;

    private String name;

    private String email;

    private String role;

    private String token;

    private String message;

    private String companyName;

    private String companyLogo;

    private String department;

    private String managerName;
}