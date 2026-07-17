package com.convexa.ai.convexa_ai_backend.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompanyProfileUpdateRequest {
    private String companyName;
    private String companyLogo;
    private String industry;
    private String companySize;
    private String website;
}
