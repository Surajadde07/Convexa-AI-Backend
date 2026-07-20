package com.convexa.ai.convexa_ai_backend.dto;

import lombok.*;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeResponse {
    private Long id;
    private String name;
    private String email;
    private String role;
    private String department;
    private LocalDateTime createdAt;
}
