package com.convexa.ai.convexa_ai_backend.dto;

import lombok.Data;
import java.time.LocalDate;

@Data
public class LearningAssignmentRequest {
    private String moduleName;
    private LocalDate deadline;
    private String priority;
    private String status; // Optional: Assigned, Completed
}
