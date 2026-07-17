package com.convexa.ai.convexa_ai_backend.dto;

import lombok.Data;
import java.time.LocalDate;

@Data
public class ImprovementPlanRequest {
    private Integer targetQA;
    private String targetSentiment;
    private LocalDate deadline;
    private String assignedModules;
    private Integer progress;
    private String status;
}
